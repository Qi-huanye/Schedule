package dev.wakeuppure.ui.background

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.temperature.TemperatureCache
import com.materialkolor.utils.ColorUtils
import dev.wakeuppure.domain.model.ImageColors
import kotlin.math.abs
import kotlin.math.min

private const val FALLBACK_SEED = 0xFF16695F.toInt()
private const val OPAQUE_ALPHA = -0x1000000
private const val COURSE_COLOR_COUNT = 6
private const val MIN_COURSE_HUE_DISTANCE = 20.0

/** Every Material 3 role comes from the same image palette, including dialogs and error states. */
fun imageColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(chromaticSeed(seed), isDark = dark, contrastLevel = 0.5)
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

/** Keeps the image accents, then fills missing hues with related, readable course colors. */
fun imageCourseColors(colors: ImageColors, dark: Boolean): List<Int> {
    val seed = chromaticSeed(colors.seed)
    val courseSeeds = mutableListOf<Hct>()
    fun addDistinct(candidate: Hct) {
        if (courseSeeds.size == COURSE_COLOR_COUNT) return
        // A middle tone and a minimum chroma keep neutral/extreme images from collapsing to gray.
        val normalized = Hct.from(candidate.hue, candidate.chroma.coerceIn(28.0, 36.0), 60.0)
        if (courseSeeds.all { hueDistance(it.hue, normalized.hue) >= MIN_COURSE_HUE_DISTANCE }) {
            courseSeeds += normalized
        }
    }

    addDistinct(seed)
    colors.accents.take(COURSE_COLOR_COUNT).forEach { accent ->
        val candidate = Hct.fromInt(accent or OPAQUE_ALPHA)
        if (candidate.chroma >= 5.0) addDistinct(candidate)
    }
    if (courseSeeds.size < COURSE_COLOR_COUNT) {
        TemperatureCache(courseSeeds.first()).getAnalogousColors(COURSE_COLOR_COUNT, 12)
            .forEach(::addDistinct)
    }
    // Gamut clipping can leave neighbouring analogues too close. Try nearby hue steps until
    // six distinct hues are available; this is bounded and identical in light and dark modes.
    for (step in 1..12) {
        if (courseSeeds.size == COURSE_COLOR_COUNT) break
        addDistinct(Hct.from(seed.hue - step * 15.0, 32.0, 60.0))
        addDistinct(Hct.from(seed.hue + step * 15.0, 32.0, 60.0))
    }
    val tone = if (dark) 30.0 else 85.0
    return courseSeeds.map { Hct.from(it.hue, it.chroma, tone).toInt() }
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
