package dev.wakeuppure

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wakeuppure.domain.model.BackgroundSettings
import dev.wakeuppure.domain.model.ImageColors
import dev.wakeuppure.ui.PureTheme
import dev.wakeuppure.ui.background.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noImageOffersPickerButDisablesImageOptions() {
        var picked = false
        compose.setContent {
            PureTheme("light") {
                Column { BackgroundSettingsSection(BackgroundUiState(), { picked = true }, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("选择背景图片").performClick()
        compose.runOnIdle { assertTrue(picked) }
        compose.onNodeWithText("主题跟随背景").assertIsNotEnabled()
        compose.onNodeWithText("课程跟随背景配色").assertIsNotEnabled()
        compose.onNodeWithText("移除背景").assertDoesNotExist()
    }

    @Test fun themeAndCourseSwitchesAreIndependentAndRemovalRestoresStoredColor() {
        var state by mutableStateOf(imageState())
        var observedPrimary = Color.Unspecified
        var observedCourse = Color.Unspecified
        var observedPalette: List<Int>? = null
        compose.setContent {
            PureTheme("light", state) {
                val primary = MaterialTheme.colorScheme.primary
                val course = courseDisplayColors(7L, "#123456").first
                val palette = LocalCourseColors.current
                SideEffect { observedPrimary = primary; observedCourse = course; observedPalette = palette }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackgroundSettingsSection(state, {}, { state = BackgroundUiState() },
                        { state = state.copy(settings = state.settings.copy(imageTheme = it)) },
                        { state = state.copy(settings = state.settings.copy(courseTheme = it)) })
                }
            }
        }
        compose.onNodeWithTag("app_background_image").assertExists()
        compose.runOnIdle {
            assertEquals(state.colors!!.light.primary, observedPrimary)
            assertEquals(Color(0xFF123456), observedCourse)
            assertNull(observedPalette)
        }
        compose.onNodeWithText("课程跟随背景配色").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(state.colors!!.light.primary, observedPrimary)
            assertEquals(Color(state.colors!!.lightCourses[1]), observedCourse)
        }
        compose.onNodeWithText("主题跟随背景").performScrollTo().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(state.colors!!.lightCourses, observedPalette) }
        compose.onNodeWithText("课程跟随背景配色").performScrollTo().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(Color(0xFF123456), observedCourse) }
        compose.onNodeWithText("移除背景").performScrollTo().performClick()
        compose.onNodeWithTag("app_background_image").assertDoesNotExist()
        compose.onNodeWithText("选择背景图片").assertIsDisplayed()
        compose.runOnIdle { assertNull(observedPalette); assertEquals(Color(0xFF123456), observedCourse) }
    }

    @Test fun changingAppearanceUsesTheCorrespondingImageThemeAndCoursePalette() {
        val state = imageState().let { it.copy(settings = it.settings.copy(courseTheme = true)) }
        var mode by mutableStateOf("light")
        var primary = Color.Unspecified
        var courses: List<Int>? = null
        compose.setContent {
            PureTheme(mode, state) {
                val scheme = MaterialTheme.colorScheme
                val palette = LocalCourseColors.current
                SideEffect { primary = scheme.primary; courses = palette }
            }
        }
        compose.runOnIdle {
            assertEquals(state.colors!!.light.primary, primary)
            assertEquals(state.colors.lightCourses, courses)
            mode = "dark"
        }
        compose.runOnIdle {
            assertEquals(state.colors!!.dark.primary, primary)
            assertEquals(state.colors.darkCourses, courses)
        }
        compose.onNodeWithTag("app_background_image").assertExists()
    }

    @Test fun imageProcessingDisablesMutationsAndDisplaysFailures() {
        var state by mutableStateOf(imageState().copy(busy = true))
        compose.setContent {
            PureTheme("dark", state) {
                Column(Modifier.verticalScroll(rememberScrollState())) { BackgroundSettingsSection(state, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("更换背景图片").assertIsNotEnabled()
        compose.onNodeWithText("移除背景").assertIsNotEnabled()
        compose.onNodeWithText("课程跟随背景配色").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false, error = "无法读取这张图片") }
        compose.onNodeWithText("无法读取这张图片").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("更换背景图片").assertIsEnabled()
        compose.onNodeWithTag("app_background_image").assertExists()
    }

    @Test fun translucentStoredCourseColorsRemainReadableOverPhotos() {
        var state by mutableStateOf(imageState())
        var displayed: Pair<Color, Color>? = null
        compose.setContent {
            PureTheme("dark", state) {
                val colors = courseDisplayColors(1, "#00FFFFFF")
                SideEffect { displayed = colors }
            }
        }
        compose.runOnIdle {
            val (base, ink) = requireNotNull(displayed)
            assertEquals(1f, base.alpha, 0f)
            val light = maxOf(base.luminance(), ink.luminance())
            val dark = minOf(base.luminance(), ink.luminance())
            assertTrue((light + 0.05f) / (dark + 0.05f) >= 4.5f)
            state = BackgroundUiState()
        }
        compose.runOnIdle { assertEquals(Color(0x00FFFFFF), displayed!!.first) }
    }

    private fun imageState(): BackgroundUiState {
        val seed = 0xFF277D63.toInt()
        val colors = ImageColors(seed, listOf(seed))
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(seed) }
        return BackgroundUiState(BackgroundSettings("test.png", colors), bitmap,
            BackgroundColors(imageColorScheme(seed, false), imageColorScheme(seed, true),
                imageCourseColors(colors, false), imageCourseColors(colors, true)))
    }
}
