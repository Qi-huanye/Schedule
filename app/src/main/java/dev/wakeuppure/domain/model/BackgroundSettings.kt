package dev.wakeuppure.domain.model

import kotlinx.serialization.Serializable

/**
 * [accents] are Score's ranked theme candidates; [clusters] are HCT k-means centroids of the
 * image's chromatic pixels, largest first, used to fill course colors with hues the photo has.
 */
@Serializable
data class ImageColors(val seed: Int, val accents: List<Int>, val clusters: List<Int> = emptyList())

/** Which part of a cropped photo stays visible, so faces can move out of the timetable. */
@Serializable
enum class BackgroundFocus { TOP, CENTER, BOTTOM }

@Serializable
data class BackgroundSettings(
    val imageName: String? = null,
    val colors: ImageColors? = null,
    val imageTheme: Boolean = true,
    val courseTheme: Boolean = false,
    /** 0 shows the photo as-is; 1 softens it fully so courses dominate. */
    val blur: Float = DEFAULT_BLUR,
    val focus: BackgroundFocus = BackgroundFocus.CENTER,
) {
    val hasImage: Boolean get() = imageName != null && colors != null

    companion object {
        const val DEFAULT_BLUR = 0.8f
    }
}
