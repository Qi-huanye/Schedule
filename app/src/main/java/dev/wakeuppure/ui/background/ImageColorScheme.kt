package dev.wakeuppure.ui.background

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.utils.ColorUtils
import dev.wakeuppure.domain.model.ImageColors
import kotlin.math.abs
import kotlin.math.min

private const val FALLBACK_SEED = 0xFF16695F.toInt()
private const val OPAQUE_ALPHA = -0x1000000
private const val COURSE_COLOR_COUNT = 6
private const val MIN_COURSE_HUE_DISTANCE = 16.0
// Course hues stay in the wallpaper's own color family, so no card competes with the photo.
private const val MAX_COURSE_HUE_SPREAD = 60.0
private const val COURSE_CHROMA = 18.0

/** Every Material 3 role comes from the same image palette, including dialogs and error states. */
fun imageColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(chromaticSeed(seed), isDark = dark, contrastLevel = 0.0)
    val roles = MaterialDynamicColors()
    fun DynamicColor.color(): Color = Color(getArgb(scheme))

    return ColorScheme(
        primary = roles.primary().color(),
        onPrimary = roles.onPrimary().color(),
        primaryContainer = roles.primaryContainer().color(),
        onPrimaryContainer = roles.onPrimaryContainer().color(),
        inversePrimary = roles.inversePrimary().color(),
        secondary = roles.secondary().color(),
        onSecondary = roles.onSecondary().color(),
        secondaryContainer = roles.secondaryContainer().color(),
        onSecondaryContainer = roles.onSecondaryContainer().color(),
        tertiary = roles.tertiary().color(),
        onTertiary = roles.onTertiary().color(),
        tertiaryContainer = roles.tertiaryContainer().color(),
        onTertiaryContainer = roles.onTertiaryContainer().color(),
        background = roles.background().color(),
        onBackground = roles.onBackground().color(),
        surface = roles.surface().color(),
        onSurface = roles.onSurface().color(),
        surfaceVariant = roles.surfaceVariant().color(),
        onSurfaceVariant = roles.onSurfaceVariant().color(),
        surfaceTint = roles.surfaceTint().color(),
        inverseSurface = roles.inverseSurface().color(),
        inverseOnSurface = roles.inverseOnSurface().color(),
        error = roles.error().color(),
        onError = roles.onError().color(),
        errorContainer = roles.errorContainer().color(),
        onErrorContainer = roles.onErrorContainer().color(),
        outline = roles.outline().color(),
        outlineVariant = roles.outlineVariant().color(),
        scrim = roles.scrim().color(),
        surfaceBright = roles.surfaceBright().color(),
        surfaceDim = roles.surfaceDim().color(),
        surfaceContainer = roles.surfaceContainer().color(),
        surfaceContainerHigh = roles.surfaceContainerHigh().color(),
        surfaceContainerHighest = roles.surfaceContainerHighest().color(),
        surfaceContainerLow = roles.surfaceContainerLow().color(),
        surfaceContainerLowest = roles.surfaceContainerLowest().color(),
    )
}

/**
 * Six calm course colors from the wallpaper's color family: image hues near the seed first
 * (k-means clusters, then Score accents), then the nearest free neighbouring hues. All share one low chroma and one tone, so cards read
 * as a single layer and differ only by hue.
 */
fun imageCourseColors(colors: ImageColors, dark: Boolean): List<Int> {
    val seed = chromaticSeed(colors.seed)
    val hues = mutableListOf<Double>()
    fun addDistinct(hue: Double) {
        val normalized = ((hue % 360.0) + 360.0) % 360.0
        if (hues.size < COURSE_COLOR_COUNT && hues.all { hueDistance(it, normalized) >= MIN_COURSE_HUE_DISTANCE }) {
            hues += normalized
        }
    }

    fun addFromImage(argb: Int, minChroma: Double) {
        val candidate = Hct.fromInt(argb or OPAQUE_ALPHA)
        if (candidate.chroma >= minChroma && hueDistance(candidate.hue, seed.hue) <= MAX_COURSE_HUE_SPREAD) addDistinct(candidate.hue)
    }

    addDistinct(seed.hue)
    // Real image hues first: k-means clusters by size, then Score's accents.
    colors.clusters.forEach { addFromImage(it, 8.0) }
    colors.accents.take(COURSE_COLOR_COUNT).forEach { addFromImage(it, 10.0) }
    // Only a palette the photo cannot fill falls back to neighbours: first evenly spaced ones,
    // then a fine outward scan that uses gaps between image hues before leaving the family.
    for (offset in (20..60 step 20) + (1..180)) {
        addDistinct(seed.hue + offset)
        addDistinct(seed.hue - offset)
    }
    val tone = if (dark) 30.0 else 90.0
    return hues.map { Hct.from(it, COURSE_CHROMA, tone).toInt() }
}

/** Picks opaque black or white with the larger WCAG contrast against an opaque background. */
fun contrastingTextColor(argb: Int): Int {
    val luminance = ColorUtils.xyzFromArgb(argb)[1] / 100.0
    val blackContrast = (luminance + 0.05) / 0.05
    val whiteContrast = 1.05 / (luminance + 0.05)
    return if (blackContrast >= whiteContrast) OPAQUE_ALPHA else -1
}

private fun chromaticSeed(argb: Int): Hct {
    val seed = Hct.fromInt(argb or OPAQUE_ALPHA)
    return if (seed.chroma >= 5.0) seed else Hct.fromInt(FALLBACK_SEED)
}

private fun hueDistance(first: Double, second: Double): Double {
    val difference = abs(first - second)
    return min(difference, 360.0 - difference)
}
