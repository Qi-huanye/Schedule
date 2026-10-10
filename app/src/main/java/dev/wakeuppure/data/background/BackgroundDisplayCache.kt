package dev.wakeuppure.data.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import android.util.Size
import dev.wakeuppure.BuildConfig
import dev.wakeuppure.domain.model.BackgroundFocus
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Derived pixels only. All calls are serialized by BackgroundRepository's image mutex. */
internal class BackgroundDisplayCache(context: Context) {
    private val directory = File(context.noBackupFilesDir, "background-display")

    fun prepare(source: File, bitmap: Bitmap, size: Size, focus: BackgroundFocus) {
        val display = render(bitmap, size, focus)
        try { store(key(source, size, focus), display) } finally { display.recycle() }
    }

    fun read(source: File, size: Size, focus: BackgroundFocus, decode: (File) -> Bitmap): Bitmap {
        val started = SystemClock.elapsedRealtime()
        val key = key(source, size, focus)
        readRaw(key, size)?.let {
            report("raw", it, started)
            return it
        }
        readWebp(key, size)?.let {
            cacheAttempt { writeRaw(key, it) }
            report("webp", it, started)
            return it
        }
        val original = decode(source)
        val display = try { render(original, size, focus) } finally { original.recycle() }
        store(key, display)
        report("source", display, started)
        return display
    }

    fun retainSource(imageName: String?) = prune { name ->
        imageName != null && name.startsWith("${imageName.removeSuffix(".png")}-")
    }

    fun retainVariant(source: File, size: Size, focus: BackgroundFocus) {
        val key = key(source, size, focus)
        prune { it == "$key.raw" || it == "$key.webp" }
    }

    fun removeSource(imageName: String) = prune { !it.startsWith("${imageName.removeSuffix(".png")}-") }

    private fun key(source: File, size: Size, focus: BackgroundFocus): String {
        val identity = "$VERSION:${source.name}:${source.length()}:${source.lastModified()}:${size.width}:${size.height}:$focus"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "${source.nameWithoutExtension}-$digest"
    }

    /** Match Compose's Crop and vertical alignment, without upscaling the stored source. */
    private fun render(source: Bitmap, size: Size, focus: BackgroundFocus): Bitmap {
        val fillScale = max(size.width.toDouble() / source.width, size.height.toDouble() / source.height)
        val resolution = min(1.0, 1.0 / fillScale)
        val width = max(1, (size.width * resolution).roundToInt())
        val height = max(1, (size.height * resolution).roundToInt())
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val left = (width - source.width * scale) / 2f
        val top = (height - source.height * scale) * when (focus) {
            BackgroundFocus.TOP -> 0f
            BackgroundFocus.CENTER -> 0.5f
            BackgroundFocus.BOTTOM -> 1f
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setHasAlpha(source.hasAlpha())
            Canvas(bitmap).drawBitmap(source, null,
                RectF(left, top, left + source.width * scale, top + source.height * scale),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    private fun readRaw(key: String, size: Size): Bitmap? = cacheAttempt {
        val file = cacheFile(key, "raw") ?: return@cacheAttempt null
        AtomicFile(file).openRead().use { stream ->
            val input = DataInputStream(stream)
            if (input.readInt() != MAGIC || input.readInt() != VERSION || input.readInt() != ENDIAN) return@use null
            val width = input.readInt()
            val height = input.readInt()
            val stride = input.readInt()
            val alpha = input.readInt()
            val checksum = input.readLong()
            val identity = ByteArray(64).also(input::readFully).toString(Charsets.US_ASCII)
            if (identity != key.takeLast(64) || !validSize(width, height, size) ||
                stride != width * 4 || alpha !in 0..1) return@use null
            val bytes = stride.toLong() * height
            if (stream.channel.size() != HEADER_BYTES + bytes) return@use null
            val pixels = stream.channel.map(FileChannel.MapMode.READ_ONLY, HEADER_BYTES.toLong(), bytes)
            if (CRC32().apply { update(pixels.duplicate()) }.value != checksum) return@use null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                if (bitmap.rowBytes != stride) throw IOException("Unsupported bitmap stride")
                bitmap.setHasAlpha(alpha == 1)
                bitmap.copyPixelsFromBuffer(pixels)
                bitmap
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
    }

    private fun readWebp(key: String, size: Size): Bitmap? = cacheAttempt {
        val file = cacheFile(key, "webp") ?: return@cacheAttempt null
        AtomicFile(file).openRead().use { stream ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(stream.fd, null, bounds)
            if (!validSize(bounds.outWidth, bounds.outHeight, size)) return@use null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            BitmapFactory.decodeFileDescriptor(stream.fd, null, options)
        }
    }

    private fun validSize(width: Int, height: Int, size: Size): Boolean =
        width in 1..min(size.width, MAX_DIMENSION) && height in 1..min(size.height, MAX_DIMENSION)

    private fun store(key: String, bitmap: Bitmap) {
        cacheAttempt { writeRaw(key, bitmap) }
        cacheAttempt {
            val file = cacheFile(key, "webp") ?: return@cacheAttempt
            writeAtomic(file) { output ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                if (!bitmap.compress(format, 90, output)) throw IOException("Cannot save display image")
            }
        }
    }

    private fun writeRaw(key: String, bitmap: Bitmap) {
        val file = cacheFile(key, "raw") ?: return
        val pixels = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(pixels)
        val checksum = CRC32().apply { update(pixels.array()) }.value
        writeAtomic(file) { stream ->
            val output = DataOutputStream(stream)
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(ENDIAN)
            output.writeInt(bitmap.width)
            output.writeInt(bitmap.height)
            output.writeInt(bitmap.rowBytes)
            output.writeInt(if (bitmap.hasAlpha()) 1 else 0)
            output.writeLong(checksum)
            output.write(key.takeLast(64).toByteArray(Charsets.US_ASCII))
            output.write(pixels.array())
        }
    }

    private fun writeAtomic(file: File, write: (FileOutputStream) -> Unit) {
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null
        try {
            val output = atomic.startWrite().also { stream = it }
            write(output)
            output.fd.sync()
            atomic.finishWrite(output)
            stream = null
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }

    private fun cacheFile(key: String, extension: String): File? {
        val root = privateDirectory() ?: return null
        if (!root.isDirectory && !root.mkdirs()) return null
        val file = File(root, "$key.$extension")
        // AtomicFile also touches these two siblings; never follow a substituted link.
        return file.takeIf { listOf(file, File("$file.new"), File("$file.bak")).all { it.canonicalFile == it } }
    }

    private fun privateDirectory(): File? = cacheAttempt {
        val expected = File(directory.parentFile!!.canonicalFile, directory.name)
        directory.canonicalFile.takeIf { it == expected }
    }

    private fun prune(keep: (String) -> Boolean) {
        cacheAttempt {
            val root = privateDirectory() ?: return@cacheAttempt
            root.listFiles().orEmpty().forEach { file ->
                if (MANAGED_NAME.matches(file.name) && !keep(file.name) && file.isFile && file.canonicalFile == file) file.delete()
            }
        }
    }

    /** Cache failures must never turn a valid original into an import/display failure. */
    private inline fun <T> cacheAttempt(block: () -> T): T? = try { block() }
    catch (_: IOException) { null }
    catch (_: RuntimeException) { null }

    private fun report(source: String, bitmap: Bitmap, started: Long) {
        if (BuildConfig.DEBUG) Log.d("BackgroundCache", "$source ${bitmap.width}x${bitmap.height} ${SystemClock.elapsedRealtime() - started}ms")
    }

    private companion object {
        const val VERSION = 1
        const val MAGIC = 0x57425231
        const val HEADER_BYTES = 100 // 7 ints, checksum long, 64 ASCII hex characters.
        const val MAX_DIMENSION = 2048
        val ENDIAN = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) 1 else 2
        val MANAGED_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[0-9a-f]{64}\\.(raw|webp)(\\.(new|bak))?")
    }
}
