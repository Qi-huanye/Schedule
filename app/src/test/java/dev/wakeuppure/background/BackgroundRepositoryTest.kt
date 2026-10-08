package dev.wakeuppure.background

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.data.background.BackgroundRepository
import dev.wakeuppure.domain.model.BackgroundSettings
import dev.wakeuppure.domain.model.ImageColors
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: BackgroundRepository
    private lateinit var sources: File
    private val directory get() = File(context.filesDir, "backgrounds")
    private val preferences get() = context.getSharedPreferences("background_settings", Context.MODE_PRIVATE)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences.edit().clear().commit()
        directory.deleteRecursively()
        sources = File(context.cacheDir, "background-test-sources").apply { mkdirs() }
        repository = BackgroundRepository(context)
    }

    @After fun tearDown() {
        sources.deleteRecursively()
        directory.deleteRecursively()
        preferences.edit().clear().commit()
    }

    @Test fun startsWithoutAnImage() = runBlocking {
        assertEquals(BackgroundSettings(), repository.load())
        assertNull(repository.readBitmap(repository.load()))
    }

    @Test fun contentImageSurvivesRestartAndOriginalDeletion() = runBlocking {
        val source = createImage("source.png", 48, 32)
        val uri = Uri.parse("content://background-test/source")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { source.inputStream() }

        val imported = repository.importImage(uri)
        assertTrue(imported.hasImage)
        assertTrue(imported.colors!!.accents.size in 1..6)
        assertTrue(File(directory, imported.imageName!!).isFile)
        assertTrue(source.delete())

        val restarted = BackgroundRepository(context)
        assertEquals(imported, restarted.load())
        val bitmap = restarted.readBitmap(restarted.load())!!
        try {
            assertEquals(48, bitmap.width)
            assertEquals(32, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(12, 8))
        } finally { bitmap.recycle() }
    }

    @Test fun replacementOnlyLeavesTheNewImageAndPreservesOptions() = runBlocking {
        repository.setOptions(imageTheme = false, courseTheme = true)
        val first = repository.importImage(Uri.fromFile(createImage("first.png", 48, 32)))
        val second = repository.importImage(Uri.fromFile(createImage("second.png", 64, 40)))

        assertNotEquals(first.imageName, second.imageName)
        assertFalse(second.imageTheme)
        assertTrue(second.courseTheme)
        assertEquals(listOf(second.imageName), directory.listFiles()!!.map { it.name })
        assertEquals(second, BackgroundRepository(context).load())
    }

    @Test fun invalidInputKeepsThePreviousImageColorsAndOptions() = runBlocking {
        repository.setOptions(imageTheme = false, courseTheme = true)
        val previous = repository.importImage(Uri.fromFile(createImage("valid.png", 48, 32)))
        val bytes = File(directory, previous.imageName!!).readBytes()
        val invalid = File(sources, "invalid.jpg").apply { writeText("not an image") }

        expectImportFailure(Uri.fromFile(invalid))

        assertEquals(previous, repository.load())
        assertArrayEquals(bytes, File(directory, previous.imageName!!).readBytes())
        assertEquals(listOf(previous.imageName), directory.listFiles()!!.map { it.name })
    }

    @Test fun oversizedInputCannotReplaceTheCurrentImage() = runBlocking {
        val previous = repository.importImage(Uri.fromFile(createImage("valid.png", 48, 32)))
        val oversized = File(sources, "oversized.png")
        createImage("oversized.png", 48, 32)
        RandomAccessFile(oversized, "rw").use { it.setLength(32L * 1024 * 1024 + 1) }

        expectImportFailure(Uri.fromFile(oversized))

        assertEquals(previous, repository.load())
        assertEquals(listOf(previous.imageName), directory.listFiles()!!.map { it.name })
    }

    @Test fun interruptedInputCannotReplaceTheCurrentImage() = runBlocking {
        val previous = repository.importImage(Uri.fromFile(createImage("valid.png", 48, 32)))
        val uri = Uri.parse("content://background-test/broken")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                    throw IOException("Provider became unavailable")
            }
        }

        expectImportFailure(uri)

        assertEquals(previous, repository.load())
        assertEquals(listOf(previous.imageName), directory.listFiles()!!.map { it.name })
    }

    @Test fun cancellationRemovesTemporaryFilesAndKeepsTheCurrentImage() = runBlocking {
        val previous = repository.importImage(Uri.fromFile(createImage("valid.png", 48, 32)))
        val bytes = createImage("next.png", 64, 40).readBytes()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val uri = Uri.parse("content://background-test/slow")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.read(buffer, offset, length)
                }
            }
        }
        val job = launch(Dispatchers.Default) { repository.importImage(uri) }
        try {
            assertTrue("Import should open the provider off the caller thread", started.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            release.countDown()
            job.cancelAndJoin()
        }

        assertEquals(previous, repository.load())
        assertEquals(listOf(previous.imageName), directory.listFiles()!!.map { it.name })
    }

    @Test fun missingImageFallsBackWithoutLosingOptions() = runBlocking {
        repository.setOptions(imageTheme = false, courseTheme = true)
        val imported = repository.importImage(Uri.fromFile(createImage("valid.png", 48, 32)))
        assertTrue(File(directory, imported.imageName!!).delete())

        val restarted = BackgroundRepository(context)
        assertEquals(BackgroundSettings(imageTheme = false, courseTheme = true), restarted.load())
        assertNull(restarted.readBitmap(imported))
    }

    @Test fun corruptAndUnsafeMetadataCannotReadOrDeleteOtherFiles() = runBlocking {
        preferences.edit().putString("settings", "not json").commit()
        assertEquals(BackgroundSettings(), repository.load())

        val outside = File(context.filesDir, "keep.png")
        outside.writeBytes(createImage("safe.png", 48, 32).readBytes())
        try {
            val unsafe = BackgroundSettings(
                imageName = "../keep.png",
                colors = ImageColors(Color.RED, listOf(Color.RED)),
                imageTheme = false,
                courseTheme = true,
            )
            preferences.edit().putString("settings", Json.encodeToString(unsafe)).commit()
            assertEquals(BackgroundSettings(imageTheme = false, courseTheme = true), repository.load())
            assertNull(repository.readBitmap(unsafe))
            repository.clear()
            assertTrue(outside.isFile)
        } finally { outside.delete() }
    }

    @Test fun honorsEveryExifRotationAndReflection() = runBlocking {
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            ExifInterface.ORIENTATION_ROTATE_180 to listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
            ExifInterface.ORIENTATION_TRANSPOSE to listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            ExifInterface.ORIENTATION_ROTATE_90 to listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            ExifInterface.ORIENTATION_TRANSVERSE to listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            ExifInterface.ORIENTATION_ROTATE_270 to listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE),
        )
        expected.forEach { (orientation, colors) ->
            val source = createImage("orientation-$orientation.jpg", 120, 80, Bitmap.CompressFormat.JPEG)
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setAttribute(ExifInterface.TAG_USER_COMMENT, "private photo metadata")
                saveAttributes()
            }
            val imported = repository.importImage(Uri.fromFile(source))
            val bitmap = repository.readBitmap(imported)!!
            try {
                assertEquals(if (orientation >= 5) 80 else 120, bitmap.width)
                assertEquals(if (orientation >= 5) 120 else 80, bitmap.height)
                val actual = listOf(
                    bitmap.getPixel(bitmap.width / 4, bitmap.height / 4),
                    bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 4),
                    bitmap.getPixel(bitmap.width / 4, bitmap.height * 3 / 4),
                    bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height * 3 / 4),
                )
                colors.zip(actual).forEach { (wanted, found) ->
                    assertTrue("Orientation $orientation: expected $wanted, got $found", colorDistance(wanted, found) < 12)
                }
                val normalized = File(directory, imported.imageName!!)
                assertTrue(normalized.name.endsWith(".png"))
                assertNull(ExifInterface(normalized).getAttribute(ExifInterface.TAG_USER_COMMENT))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun largeImagesAreBoundedWithoutChangingAspectRatio() = runBlocking {
        val imported = repository.importImage(Uri.fromFile(createImage("large.png", 5000, 1000)))
        val bitmap = repository.readBitmap(imported)!!
        try {
            assertTrue(bitmap.width <= 2048)
            assertTrue(bitmap.height <= 2048)
            assertEquals(5.0, bitmap.width.toDouble() / bitmap.height, 0.02)
        } finally { bitmap.recycle() }
    }

    @Test fun normalizedPngRetainsTransparency() = runBlocking {
        val source = File(sources, "transparent.png")
        val original = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        try {
            original.eraseColor(Color.argb(80, 220, 40, 80))
            source.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { original.recycle() }

        val imported = repository.importImage(Uri.fromFile(source))
        val bitmap = repository.readBitmap(imported)!!
        try { assertEquals(80, Color.alpha(bitmap.getPixel(12, 12))) } finally { bitmap.recycle() }
    }

    @Test fun clearRemovesOnlyImageAndColorsWhileKeepingOptions() = runBlocking {
        val imported = repository.importImage(Uri.fromFile(createImage("valid.png", 48, 32)))
        val toggled = repository.setOptions(imageTheme = false, courseTheme = true)
        assertEquals(imported.copy(imageTheme = false, courseTheme = true), toggled)

        val cleared = repository.clear()

        assertEquals(BackgroundSettings(imageTheme = false, courseTheme = true), cleared)
        assertEquals(cleared, BackgroundRepository(context).load())
        assertNull(repository.readBitmap(cleared))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun restoreReclaimsOnlyManagedOrphansAndKeepsTheCurrentImage() = runBlocking {
        repository.setOptions(imageTheme = false, courseTheme = true)
        val previous = repository.importImage(Uri.fromFile(createImage("current.png", 48, 32)))
        val originalBytes = File(directory, previous.imageName!!).readBytes()
        val orphans = createKilledImportFiles(previous.imageName!!)
        val unrelated = listOf("notes.txt", "photo.png", "source-personal.tmp", "${UUID.randomUUID()}.png.more")
            .map { File(directory, it).apply { writeText("keep this file") } }
        val nested = File(directory, "${UUID.randomUUID()}.png").apply { mkdir() }
        val nestedFile = File(nested, "source-12345.tmp").apply { writeText("keep nested files") }
        val outside = File(sources, "unrelated.txt").apply { writeText("keep the link target") }
        val link = File(directory, "${UUID.randomUUID()}.png")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        val restarted = BackgroundRepository(context)
        assertEquals(previous, restarted.restore())

        orphans.forEach { assertFalse("Orphan ${it.name} must be reclaimed", it.exists()) }
        assertArrayEquals(originalBytes, File(directory, previous.imageName!!).readBytes())
        assertEquals(previous, restarted.load())
        unrelated.forEach { assertEquals("keep this file", it.readText()) }
        assertEquals("keep nested files", nestedFile.readText())
        assertEquals("keep the link target", outside.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertNotNull(restarted.readBitmap(previous)?.also { it.recycle() })
    }

    @Test fun newImportReclaimsKilledImportFilesEvenIfTheNewInputFails() = runBlocking {
        val previous = repository.importImage(Uri.fromFile(createImage("current.png", 48, 32)))
        val orphans = createKilledImportFiles(previous.imageName!!)
        val invalid = File(sources, "invalid.png").apply { writeText("not an image") }

        expectImportFailure(Uri.fromFile(invalid))

        assertEquals(previous, repository.load())
        orphans.forEach { assertFalse("Orphan ${it.name} must be reclaimed before import", it.exists()) }
        assertEquals(listOf(previous.imageName), directory.listFiles()!!.map { it.name })
    }

    @Test fun clearAlsoReclaimsKilledImportFilesAndPreservesUnrelatedFiles() = runBlocking {
        repository.setOptions(imageTheme = false, courseTheme = true)
        val previous = repository.importImage(Uri.fromFile(createImage("current.png", 48, 32)))
        val orphans = createKilledImportFiles(previous.imageName!!)
        val unrelated = File(directory, "personal.png").apply { writeText("leave this file") }

        assertEquals(BackgroundSettings(imageTheme = false, courseTheme = true), repository.clear())

        assertFalse(File(directory, previous.imageName!!).exists())
        orphans.forEach { assertFalse("Orphan ${it.name} must be reclaimed on clear", it.exists()) }
        assertEquals("leave this file", unrelated.readText())
        assertEquals(listOf(unrelated.name), directory.listFiles()!!.map { it.name })
    }

    @Test fun restoreNeverScansThroughABackgroundDirectorySymlink() = runBlocking {
        val options = repository.setOptions(imageTheme = false, courseTheme = true)
        val external = File(sources, "unrelated-directory").apply { mkdir() }
        val externalFiles = listOf("source-12345.tmp", "${UUID.randomUUID()}.png", "${UUID.randomUUID()}.png.new")
            .map { File(external, it).apply { writeText("unrelated data") } }
        Files.createSymbolicLink(directory.toPath(), external.toPath())
        try {
            assertEquals(options, repository.restore())
            externalFiles.forEach { assertEquals("unrelated data", it.readText()) }
            assertTrue(Files.isSymbolicLink(directory.toPath()))
        } finally { Files.delete(directory.toPath()) }
    }

    private fun createKilledImportFiles(currentName: String): List<File> = listOf(
        "source-1234567890.tmp",
        "${UUID.randomUUID()}.png",
        "${UUID.randomUUID()}.png.new",
        "${UUID.randomUUID()}.png.bak",
        "$currentName.new",
        "$currentName.bak",
    ).map { File(directory, it).apply { writeText("interrupted image import") } }.also { files ->
        RandomAccessFile(files.first(), "rw").use { it.setLength(32L * 1024 * 1024) }
    }

    private fun createImage(
        name: String,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
            val row = IntArray(width)
            for (y in 0 until height) {
                for (x in 0 until width) row[x] = colors[(if (y >= height / 2) 2 else 0) + (if (x >= width / 2) 1 else 0)]
                bitmap.setPixels(row, 0, width, 0, y, width, 1)
            }
            return File(sources, name).also { file ->
                file.outputStream().use { assertTrue(bitmap.compress(format, 100, it)) }
            }
        } finally { bitmap.recycle() }
    }

    private suspend fun expectImportFailure(uri: Uri) {
        assertTrue("Invalid input should be rejected", runCatching { repository.importImage(uri) }.isFailure)
    }

    private fun colorDistance(first: Int, second: Int): Int =
        kotlin.math.abs(Color.red(first) - Color.red(second)) +
            kotlin.math.abs(Color.green(first) - Color.green(second)) +
            kotlin.math.abs(Color.blue(first) - Color.blue(second))
}
