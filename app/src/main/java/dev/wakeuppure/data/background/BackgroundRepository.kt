package dev.wakeuppure.data.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.AtomicFile
import androidx.exifinterface.media.ExifInterface
import dev.wakeuppure.domain.model.BackgroundFocus
import dev.wakeuppure.domain.model.BackgroundSettings
import dev.wakeuppure.domain.model.ImageColors
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.roundToInt

class BackgroundRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val directory = File(appContext.filesDir, "backgrounds")

    /** Reads only the small settings record and file metadata; image decoding is asynchronous. */
    fun load(): BackgroundSettings = synchronized(settingsLock) { loadLocked() }

    suspend fun restore(): BackgroundSettings = withContext(Dispatchers.IO) {
        imageOperations.withLock {
            reclaimOrphans()
            load()
        }
    }

    suspend fun importImage(uri: Uri): BackgroundSettings = withContext(Dispatchers.IO) {
        imageOperations.withLock {
            currentCoroutineContext().ensureActive()
            reclaimOrphans()
            if (privateDirectory() == null) throw IOException("背景图片目录不可用")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建背景图片目录")
            val source = File.createTempFile("source-", ".tmp", directory)
            var normalizedFile: File? = null
            var bitmap: Bitmap? = null
            var committed = false
            try {
                copyBounded(uri, source)
                currentCoroutineContext().ensureActive()
                val normalized = normalize(source).also { bitmap = it }
                currentCoroutineContext().ensureActive()
                val colors = extractColors(normalized)
                val destination = File(directory, "${UUID.randomUUID()}.png").also { normalizedFile = it }
                writeImage(normalized, destination)
                currentCoroutineContext().ensureActive()
                synchronized(settingsLock) {
                    val previous = loadLocked()
                    val next = previous.copy(imageName = destination.name, colors = colors)
                    persistLocked(next)
                    committed = true
                    imageFile(previous.imageName)?.delete()
                    next
                }
            } finally {
                bitmap?.recycle()
                source.delete()
                if (!committed) normalizedFile?.let { AtomicFile(it).delete() }
            }
        }
    }

    suspend fun setOptions(imageTheme: Boolean, courseTheme: Boolean): BackgroundSettings =
        update { it.copy(imageTheme = imageTheme, courseTheme = courseTheme) }

    suspend fun setLayout(blur: Float, focus: BackgroundFocus): BackgroundSettings =
        update { it.copy(blur = saneBlur(blur), focus = focus) }

    private suspend fun update(transform: (BackgroundSettings) -> BackgroundSettings): BackgroundSettings =
        withContext(Dispatchers.IO) {
            imageOperations.withLock {
                currentCoroutineContext().ensureActive()
                synchronized(settingsLock) { transform(loadLocked()).also(::persistLocked) }
            }
        }

    suspend fun clear(): BackgroundSettings = withContext(Dispatchers.IO) {
        imageOperations.withLock {
            currentCoroutineContext().ensureActive()
            val cleared = synchronized(settingsLock) {
                val previous = loadLocked()
                val next = previous.copy(imageName = null, colors = null)
                persistLocked(next)
                next
            }
            reclaimOrphans()
            cleared
        }
    }

    suspend fun readBitmap(settings: BackgroundSettings): Bitmap? {
        // Keep ownership until the dispatch back to the caller succeeds, including cancellation.
        var bitmap: Bitmap? = null
        return try {
            withContext(Dispatchers.IO) {
                imageOperations.withLock {
                    val file = imageFile(settings.imageName)
                    if (!settings.hasImage || file?.isFile != true) return@withLock null
                    currentCoroutineContext().ensureActive()
                    bitmap = decodeBounded(file)
                    currentCoroutineContext().ensureActive()
                    bitmap
                }
            }
        } catch (cancelled: CancellationException) {
            bitmap?.recycle()
            throw cancelled
        } catch (_: IOException) {
            bitmap?.recycle()
            null
        } catch (_: RuntimeException) {
            bitmap?.recycle()
            null
        }
    }

    private fun loadLocked(): BackgroundSettings {
        val settings = readSettingsLocked().let { it.copy(blur = saneBlur(it.blur)) }
        return if (validColors(settings.colors) && imageFile(settings.imageName)?.isFile == true) {
            settings
        } else {
            settings.copy(imageName = null, colors = null)
        }
    }

    private fun readSettingsLocked(): BackgroundSettings {
        return try {
            val encoded = preferences.getString(SETTINGS_KEY, null) ?: return BackgroundSettings()
            if (encoded.length > MAX_SETTINGS_LENGTH) return BackgroundSettings()
            json.decodeFromString<BackgroundSettings>(encoded)
        } catch (_: RuntimeException) {
            BackgroundSettings()
        }
    }

    /** Called with imageOperations held so active imports never appear to be orphans. */
    private suspend fun reclaimOrphans() {
        currentCoroutineContext().ensureActive()
        val privateDirectory = privateDirectory() ?: return
        if (!privateDirectory.isDirectory) return
        val referencedName = synchronized(settingsLock) {
            readSettingsLocked().imageName?.takeIf(SAFE_IMAGE_NAME::matches)
        }
        val referencedExists = referencedName?.let { File(privateDirectory, it).isFile } == true
        directory.listFiles().orEmpty().forEach { file ->
            currentCoroutineContext().ensureActive()
            val atomicBase = when {
                file.name.endsWith(".new") -> file.name.removeSuffix(".new")
                file.name.endsWith(".bak") -> file.name.removeSuffix(".bak")
                else -> null
            }
            val managed = SAFE_SOURCE_NAME.matches(file.name) || SAFE_IMAGE_NAME.matches(file.name) ||
                atomicBase?.let(SAFE_IMAGE_NAME::matches) == true
            if (!managed || file.name == referencedName) return@forEach
            // A referenced backup may be the only surviving copy. It is not an orphan.
            if (atomicBase != null && atomicBase == referencedName && !referencedExists) return@forEach
            val plainFile = try {
                file.isFile && file.canonicalFile == File(privateDirectory, file.name)
            } catch (_: IOException) {
                false
            }
            // Only unlink recognized direct files. Never recurse into directories or follow links.
            if (plainFile) file.delete()
        }
    }

    private fun privateDirectory(): File? = try {
        val expected = File(appContext.filesDir.canonicalFile, "backgrounds")
        directory.canonicalFile.takeIf { it == expected }
    } catch (_: IOException) {
        null
    }

    private fun persistLocked(settings: BackgroundSettings) {
        val previous = try { preferences.getString(SETTINGS_KEY, null) } catch (_: ClassCastException) { null }
        val encoded = json.encodeToString(settings)
        val saved = try {
            preferences.edit().putString(SETTINGS_KEY, encoded).commit()
        } catch (failure: RuntimeException) {
            restorePreferences(previous)
            throw IOException("无法保存背景设置", failure)
        }
        if (!saved) {
            // commit() updates its in-memory value even when the durable write fails.
            restorePreferences(previous)
            throw IOException("无法保存背景设置")
        }
    }

    private fun restorePreferences(previous: String?) {
        val editor = preferences.edit()
        if (previous == null) editor.remove(SETTINGS_KEY) else editor.putString(SETTINGS_KEY, previous)
        editor.commit()
    }

    private fun imageFile(name: String?): File? {
        if (name == null || !SAFE_IMAGE_NAME.matches(name)) return null
        return try {
            File(directory, name).takeIf { it.canonicalFile.parentFile == directory.canonicalFile }
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun copyBounded(uri: Uri, destination: File) {
        val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("无法读取所选图片")
        input.use { source ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer, 0, minOf(buffer.size.toLong(), MAX_INPUT_BYTES - total + 1).toInt())
                    if (count < 0) break
                    currentCoroutineContext().ensureActive()
                    if (count == 0) continue
                    total += count
                    if (total > MAX_INPUT_BYTES) throw IOException("图片超过 32 MiB，请选择更小的图片")
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun normalize(source: File): Bitmap {
        val orientation = try {
            ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val decoded = decodeBounded(source)
        if (matrix.isIdentity) return decoded
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } catch (failure: Throwable) {
            decoded.recycle()
            throw failure
        }
    }

    private fun decodeBounded(source: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法解析所选图片")
        val largest = max(bounds.outWidth, bounds.outHeight).toLong()
        var sample = 1
        while ((largest + sample - 1) / sample > MAX_IMAGE_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: throw IOException("无法解析所选图片")
        if (max(decoded.width, decoded.height) <= MAX_IMAGE_SIZE) return decoded
        // Guard formats whose decoder does not honor the requested sample size exactly.
        return try {
            val scale = MAX_IMAGE_SIZE.toDouble() / max(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(
                decoded,
                max(1, (decoded.width * scale).roundToInt()),
                max(1, (decoded.height * scale).roundToInt()),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } catch (failure: Throwable) {
            decoded.recycle()
            throw failure
        }
    }

    /** Colors saved by builds before k-means lack clusters; derive them from the stored image. */
    fun withClusters(colors: ImageColors, bitmap: Bitmap): ImageColors =
        if (colors.clusters.isNotEmpty()) colors
        else colors.copy(clusters = HctKMeans.clusters(samplePixels(bitmap)).take(MAX_CLUSTERS))

    private fun samplePixels(bitmap: Bitmap): IntArray {
        val scale = minOf(1.0, SAMPLE_SIZE.toDouble() / max(bitmap.width, bitmap.height))
        val sample = Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).roundToInt()),
            max(1, (bitmap.height * scale).roundToInt()),
            true,
        )
        return try {
            IntArray(sample.width * sample.height).also {
                sample.getPixels(it, 0, sample.width, 0, 0, sample.width, sample.height)
            }
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
    }

    private fun extractColors(bitmap: Bitmap): ImageColors {
        val extracted = ImageColorExtractor.extract(samplePixels(bitmap))
        return extracted.copy(accents = extracted.accents.take(MAX_ACCENTS), clusters = extracted.clusters.take(MAX_CLUSTERS)).also {
            if (!validColors(it)) throw IOException("无法生成图片配色")
        }
    }

    private fun writeImage(bitmap: Bitmap, destination: File) {
        val atomic = AtomicFile(destination)
        var stream: FileOutputStream? = null
        try {
            val output = atomic.startWrite().also { stream = it }
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("无法保存背景图片")
            output.fd.sync()
            atomic.finishWrite(output)
            stream = null
            if (!destination.isFile || destination.length() == 0L) throw IOException("无法保存背景图片")
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }

    private fun validColors(colors: ImageColors?): Boolean =
        colors != null && colors.seed ushr 24 == 255 && colors.accents.size in 1..MAX_ACCENTS &&
            colors.accents.all { it ushr 24 == 255 } && colors.clusters.size <= MAX_CLUSTERS &&
            colors.clusters.all { it ushr 24 == 255 }

    private fun saneBlur(blur: Float): Float =
        if (blur.isFinite()) blur.coerceIn(0f, 1f) else BackgroundSettings.DEFAULT_BLUR

    private companion object {
        const val PREFERENCES = "background_settings"
        const val SETTINGS_KEY = "settings"
        const val MAX_SETTINGS_LENGTH = 4096
        const val MAX_INPUT_BYTES = 32L * 1024 * 1024
        const val MAX_IMAGE_SIZE = 2048
        const val SAMPLE_SIZE = 128
        const val MAX_ACCENTS = 6
        const val MAX_CLUSTERS = 8
        val SAFE_IMAGE_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")
        val SAFE_SOURCE_NAME = Regex("source-[0-9]+\\.tmp")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        // Multiple repositories can be constructed during recreation; they share one private store.
        val imageOperations = Mutex()
        val settingsLock = Any()
    }
}
