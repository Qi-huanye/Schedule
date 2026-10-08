package dev.wakeuppure.background

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import dev.wakeuppure.data.background.ImageColorExtractor
import dev.wakeuppure.domain.model.ImageColors
import dev.wakeuppure.ui.background.contrastingTextColor
import dev.wakeuppure.ui.background.imageColorScheme
import dev.wakeuppure.ui.background.imageCourseColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageColorsTest {
    @Test fun dominantChromaticRegionSuppliesTheThemeSeed() {
        val green = 0xFF2E7D32.toInt()
        val orange = 0xFFDE5C45.toInt()
        val blue = 0xFF3F61B5.toInt()
        val pixels = IntArray(1_000) { if (it < 700) green else if (it < 850) orange else blue }

        val result = ImageColorExtractor.extract(pixels)

        assertTrue(hueDifference(result.seed, green) < 5.0)
        assertTrue(result.accents.contains(result.seed))
        assertTrue(result.accents.size in 1..6)
        assertTrue(result.accents.all { it ushr 24 == 255 })
    }

    @Test fun aVisibleAccentSurvivesALargeNeutralRegion() {
        val orange = 0xFFE47B29.toInt()
        val pixels = IntArray(1_000) {
            if (it < 450) 0xFFFFFFFF.toInt() else if (it < 900) 0xFF333333.toInt() else orange
        }

        assertTrue(hueDifference(ImageColorExtractor.extract(pixels).seed, orange) < 5.0)
    }

    @Test fun transparentRgbValuesDoNotAffectThePalette() {
        val blue = 0xFF3166B8.toInt()
        val visible = IntArray(80) { blue }
        val hidden = IntArray(2_000) { if (it % 2 == 0) 0x00FF0000 else 0x18FF0000 }

        assertEquals(ImageColorExtractor.extract(visible), ImageColorExtractor.extract(hidden + visible))
    }

    @Test fun emptyTransparentAndGrayscaleImagesUseTheMintFallback() {
        val empty = ImageColorExtractor.extract(intArrayOf())
        val transparent = ImageColorExtractor.extract(intArrayOf(0x00FFFFFF, 0x00000000))
        val gray = ImageColorExtractor.extract(
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF777777.toInt(), 0xFF000000.toInt()),
        )

        assertEquals(0xFF16695F.toInt(), empty.seed)
        assertEquals(empty, transparent)
        assertEquals(empty, gray)
        assertTrue(empty.accents.isNotEmpty())
    }

    @Test fun extractionIsDeterministicAndDoesNotMutatePixels() {
        val pixels = IntArray(32_000) { 0xFF000000.toInt() or ((it * 15485863) and 0x00FFFFFF) }
        val original = pixels.copyOf()

        val first = ImageColorExtractor.extract(pixels)

        assertEquals(first, ImageColorExtractor.extract(pixels))
        assertTrue(original.contentEquals(pixels))
    }

    @Test fun monochromeImagesStillProduceSixDistinctCourseColors() {
        val palettes = listOf(
            ImageColorExtractor.extract(IntArray(100) { 0xFF337C74.toInt() }),
            ImageColorExtractor.extract(IntArray(100) { 0xFF888888.toInt() }),
            ImageColors(0xFFFFFFFF.toInt(), listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())),
        )

        for (palette in palettes) for (dark in listOf(false, true)) {
            val courses = imageCourseColors(palette, dark)
            assertEquals(6, courses.size)
            assertEquals(6, courses.distinct().size)
            assertTrue(courses.all { it ushr 24 == 255 && Hct.fromInt(it).chroma >= 12.0 })
            for (first in courses.indices) for (second in first + 1 until courses.size) {
                assertTrue("Course hues should be distinguishable", hueDifference(courses[first], courses[second]) >= 10.0)
            }
            assertEquals(courses, imageCourseColors(palette, dark))
        }
    }

    @Test fun courseColorsKeepSourceAccentsAndAdaptToDarkMode() {
        val seed = Hct.from(190.0, 44.0, 50.0).toInt()
        val accent = Hct.from(275.0, 40.0, 50.0).toInt()
        val source = ImageColors(seed, listOf(seed, accent))
        val light = imageCourseColors(source, dark = false)
        val dark = imageCourseColors(source, dark = true)

        assertTrue(hueDifference(seed, light.first()) < 5.0)
        assertTrue(light.any { hueDifference(accent, it) < 5.0 })
        assertTrue(light.all { Hct.fromInt(it).tone in 80.0..92.0 })
        assertTrue(dark.all { Hct.fromInt(it).tone in 22.0..38.0 })
        for (index in light.indices) assertTrue(hueDifference(light[index], dark[index]) < 5.0)
        for (color in light + dark) assertTrue(contrast(color, contrastingTextColor(color)) >= 4.5)
    }

    @Test fun themeSurfacesAndAccentsAllRespondToTheImageSeed() {
        for (dark in listOf(false, true)) {
            val green = imageColorScheme(0xFF008577.toInt(), dark)
            val pink = imageColorScheme(0xFFB63277.toInt(), dark)
            val greenRoles = imageDependentRoles(green)
            val pinkRoles = imageDependentRoles(pink)

            for (role in greenRoles.keys) {
                // Material fixes the light lowest container at white; medium contrast also
                // raises dark onSurface to white against the brightest allowed surface.
                if ((!dark && role == "surfaceContainerLowest") || (dark && role == "onSurface")) {
                    assertEquals(Color.White, greenRoles.getValue(role))
                    assertEquals(Color.White, pinkRoles.getValue(role))
                } else {
                    assertNotEquals("Unchanged role: $role", greenRoles.getValue(role), pinkRoles.getValue(role))
                }
                assertEquals(1f, greenRoles.getValue(role).alpha, 0f)
            }
        }
    }

    @Test fun lightAndDarkThemesProvideReadableTextOnEveryFill() {
        val seeds = listOf(0xFF008577.toInt(), 0xFFFF5500.toInt(), 0xFF5D33BB.toInt())
        for (seed in seeds) for (dark in listOf(false, true)) {
            val scheme = imageColorScheme(seed, dark)
            val pairs = listOf(
                "primary" to (scheme.primary to scheme.onPrimary),
                "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
                "secondary" to (scheme.secondary to scheme.onSecondary),
                "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
                "tertiary" to (scheme.tertiary to scheme.onTertiary),
                "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
                "background" to (scheme.background to scheme.onBackground),
                "surface" to (scheme.surface to scheme.onSurface),
                "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
                "surfaceContainerLowest" to (scheme.surfaceContainerLowest to scheme.onSurface),
                "surfaceContainerLow" to (scheme.surfaceContainerLow to scheme.onSurface),
                "surfaceContainer" to (scheme.surfaceContainer to scheme.onSurface),
                "surfaceContainerHigh" to (scheme.surfaceContainerHigh to scheme.onSurface),
                "surfaceContainerHighest" to (scheme.surfaceContainerHighest to scheme.onSurface),
                "surfaceBright" to (scheme.surfaceBright to scheme.onSurface),
                "surfaceDim" to (scheme.surfaceDim to scheme.onSurface),
                "inverseSurface" to (scheme.inverseSurface to scheme.inverseOnSurface),
                "error" to (scheme.error to scheme.onError),
                "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
            )

            for ((name, colors) in pairs) {
                assertTrue("Unreadable $name in dark=$dark", contrast(colors.first.toArgb(), colors.second.toArgb()) >= 4.5)
                assertEquals(1f, colors.first.alpha, 0f)
                assertEquals(1f, colors.second.alpha, 0f)
            }
        }
    }

    @Test fun generatedThemeUsesMediumContrastIncludingErrorRoles() {
        val light = imageColorScheme(0xFF008577.toInt(), dark = false)
        val dark = imageColorScheme(0xFF008577.toInt(), dark = true)

        assertTrue(contrast(light.primary.toArgb(), light.onPrimary.toArgb()) >= 6.8)
        assertTrue(contrast(light.error.toArgb(), light.onError.toArgb()) >= 6.8)
        assertTrue(contrast(dark.primary.toArgb(), dark.onPrimary.toArgb()) >= 6.8)
        assertTrue(contrast(dark.error.toArgb(), dark.onError.toArgb()) >= 6.8)
    }

    @Test fun textColorMaintainsNormalTextContrastAcrossRgbSpace() {
        for (red in 0..255 step 17) for (green in 0..255 step 17) for (blue in 0..255 step 17) {
            val background = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
            val ink = contrastingTextColor(background)
            assertEquals(255, ink ushr 24)
            assertTrue("Insufficient contrast for ${background.toUInt().toString(16)}", contrast(background, ink) >= 4.5)
        }
    }

    private fun imageDependentRoles(scheme: ColorScheme): Map<String, Color> = mapOf(
        "primary" to scheme.primary,
        "primaryContainer" to scheme.primaryContainer,
        "secondary" to scheme.secondary,
        "secondaryContainer" to scheme.secondaryContainer,
        "tertiary" to scheme.tertiary,
        "tertiaryContainer" to scheme.tertiaryContainer,
        "inversePrimary" to scheme.inversePrimary,
        "surfaceTint" to scheme.surfaceTint,
        "background" to scheme.background,
        "onBackground" to scheme.onBackground,
        "surface" to scheme.surface,
        "onSurface" to scheme.onSurface,
        "surfaceVariant" to scheme.surfaceVariant,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "surfaceDim" to scheme.surfaceDim,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "inverseSurface" to scheme.inverseSurface,
        "inverseOnSurface" to scheme.inverseOnSurface,
        "outline" to scheme.outline,
        "outlineVariant" to scheme.outlineVariant,
    )

    private fun hueDifference(first: Int, second: Int): Double {
        val difference = abs(Hct.fromInt(first).hue - Hct.fromInt(second).hue)
        return min(difference, 360.0 - difference)
    }

    private fun contrast(first: Int, second: Int): Double {
        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val srgb = ((argb ushr shift) and 255) / 255.0
                return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
            }
            return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
        }
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) / (min(firstLuminance, secondLuminance) + 0.05)
    }
}
