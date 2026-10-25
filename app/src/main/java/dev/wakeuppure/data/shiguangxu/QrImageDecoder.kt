package dev.wakeuppure.data.shiguangxu

import android.graphics.BitmapFactory
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Decodes a user-selected image locally, without retaining the image or uploading pixels. */
object QrImageDecoder {
    fun decode(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 32 * 1024 * 1024) { "时光序导入失败：请选择小于 32 MB 的二维码图片" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "时光序导入失败：无法读取图片，请重新选择" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > 2048L * sample) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("时光序导入失败：无法读取图片，请重新选择")
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            // Transparent QR exports need a white backdrop rather than an all-black background.
            for (i in pixels.indices) {
                val p = pixels[i]
                val alpha = p ushr 24
                if (alpha != 255) {
                    fun channel(shift: Int) = (((p ushr shift) and 255) * alpha + 255 * (255 - alpha)) / 255
                    pixels[i] = (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                }
            }
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val reader = MultiFormatReader()
            val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
            for (candidate in listOf(source, source.invert())) {
                try { return reader.decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text }
                catch (_: ReaderException) { /* Also try images with light QR modules on a dark background. */ }
                finally { reader.reset() }
            }
            throw IllegalArgumentException("时光序导入失败：图片中未识别到二维码，请选择清晰、完整的二维码图片")
        } finally { bitmap.recycle() }
    }
}
