package dev.wakeuppure.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ImageColors(val seed: Int, val accents: List<Int>)

@Serializable
data class BackgroundSettings(
    val imageName: String? = null,
    val colors: ImageColors? = null,
    val imageTheme: Boolean = true,
    val courseTheme: Boolean = false,
) {
    val hasImage: Boolean get() = imageName != null && colors != null
}
