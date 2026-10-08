package dev.wakeuppure.data.background

import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import dev.wakeuppure.domain.model.ImageColors

object ImageColorExtractor {
    private const val MAX_SAMPLE_PIXELS = 16_384
    private const val MAX_QUANTIZED_COLORS = 64
    private const val FALLBACK_SEED = 0xFF16695F.toInt()

    /** Extracts a deterministic palette without retaining or modifying the image pixels. */
    fun extract(pixels: IntArray): ImageColors {
        val opaqueCount = pixels.count { it ushr 24 == 255 }
        if (opaqueCount == 0) return ImageColors(FALLBACK_SEED, listOf(FALLBACK_SEED))

        // Sample visible pixels evenly, so a large transparent border does not crowd them out.
        // Only the quantizer's bounded sample is allocated, even for a full-resolution bitmap.
        val sampleSize = minOf(opaqueCount, MAX_SAMPLE_PIXELS)
        val sample = IntArray(sampleSize)
        var visibleIndex = 0L
        var sampleIndex = 0
        for (pixel in pixels) {
            if (pixel ushr 24 != 255) continue
            val targetIndex = (2L * sampleIndex + 1L) * opaqueCount / (2L * sampleSize)
            if (visibleIndex == targetIndex) {
                sample[sampleIndex++] = pixel
                if (sampleIndex == sampleSize) break
            }
            visibleIndex++
        }
        // Stable input order also makes ties and the quantizer's seeded clustering reproducible.
        sample.sort()
        val populations = QuantizerCelebi.quantize(sample, MAX_QUANTIZED_COLORS)
        val accents = Score.score(populations, desired = 6, fallbackColorArgb = FALLBACK_SEED)
        return ImageColors(seed = accents.first(), accents = accents)
    }
}
