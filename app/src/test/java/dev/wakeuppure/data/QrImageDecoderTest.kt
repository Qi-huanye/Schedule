package dev.wakeuppure.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.wakeuppure.data.shiguangxu.QrImageDecoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrImageDecoderTest {
    private val content = "synthetic-timetable-qr-0123456789+example="
    private fun qr(size: Int = 400, background: Int = Color.WHITE, foreground: Int = Color.BLACK): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(IntArray(size * size) { i -> if (matrix[i % size, i / size]) foreground else background },
                0, size, 0, 0, size, size)
        }
    }
    private fun decode(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(format, 95, output)
        return QrImageDecoder.decode(ByteArrayInputStream(output.toByteArray()))
    }

    @Test fun readsPngAndJpegQrPhotos() {
        val image = qr()
        try {
            assertEquals(content, decode(image))
            assertEquals(content, decode(image, Bitmap.CompressFormat.JPEG))
        } finally { image.recycle() }
    }

    @Test fun readsRotatedImage() {
        val image = qr()
        val rotated = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(90f) }, false)
        try { assertEquals(content, decode(rotated)) }
        finally { image.recycle(); rotated.recycle() }
    }

    @Test fun readsTransparentAndInvertedQrExports() {
        for (image in listOf(qr(background = Color.TRANSPARENT), qr(background = Color.BLACK, foreground = Color.WHITE))) {
            try { assertEquals(content, decode(image)) }
            finally { image.recycle() }
        }
    }

    @Test fun readsQrInsideLargeScreenshotAfterDownsampling() {
        val image = Bitmap.createBitmap(1800, 3200, Bitmap.Config.ARGB_8888)
        val code = qr(800)
        try {
            Canvas(image).apply { drawColor(Color.WHITE); drawBitmap(code, 500f, 1100f, null) }
            assertEquals(content, decode(image))
        } finally { image.recycle(); code.recycle() }
    }

    @Test fun missingQrAndUnreadableImageHaveSpecificErrors() {
        val image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val missing = assertThrows(IllegalArgumentException::class.java) { decode(image) }
            assertTrue(missing.message.orEmpty().contains("未识别到二维码"))
        } finally { image.recycle() }
        val unreadable = assertThrows(IllegalArgumentException::class.java) {
            QrImageDecoder.decode(ByteArrayInputStream("not an image".toByteArray()))
        }
        assertTrue(unreadable.message.orEmpty().contains("无法读取图片"))
    }
}
