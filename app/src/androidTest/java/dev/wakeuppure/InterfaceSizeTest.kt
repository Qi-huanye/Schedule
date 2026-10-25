package dev.wakeuppure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wakeuppure.domain.model.ThemeSettings
import dev.wakeuppure.ui.InterfaceScale
import dev.wakeuppure.ui.PureTheme
import dev.wakeuppure.ui.settings.InterfaceSizeSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InterfaceSizeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultPreservesSystemAndPreviewDoesNotMultiplyExistingScale() {
        var percent by mutableIntStateOf(100)
        lateinit var system: Density
        lateinit var actual: Density
        lateinit var preview: Density
        compose.setContent {
            val base = LocalDensity.current
            val largeSystem = remember(base) { Density(base.density * 1.3f, 2f) }
            CompositionLocalProvider(LocalDensity provides largeSystem) {
                system = LocalDensity.current
                PureTheme("light", theme = ThemeSettings(interfaceScalePercent = percent)) {
                    actual = LocalDensity.current
                    InterfaceScale(75) { preview = LocalDensity.current }
                }
            }
        }
        compose.runOnIdle {
            assertSame(system, actual)
            assertEquals(system.density * .75f, preview.density, .001f)
            percent = 60
        }
        compose.runOnIdle {
            assertEquals(system.density * .6f, actual.density, .001f)
            assertEquals(2f, actual.fontScale, .001f)
            assertEquals(system.density * .75f, preview.density, .001f)
        }
    }

    @Test fun sizeSheetCanResetAndApplyAtLargeSystemFont() {
        var applied: Int? = null
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                PureTheme("light") { InterfaceSizeSheet(75, true, {}, { applied = it }) }
            }
        }
        compose.onNodeWithText("恢复跟随系统").performScrollTo().performClick()
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithContentDescription("界面缩放比例").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.onNodeWithText("60%").assertExists()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("应用").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(60, applied) }
    }

    @Test fun smallerSizeFitsMoreCourseNameCharactersInSevenColumns() {
        var percent by mutableIntStateOf(100)
        var firstLineCharacters = 0
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    PureTheme("light", theme = ThemeSettings(interfaceScalePercent = percent)) {
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { day ->
                                Text("高等数学", Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 13.sp,
                                    onTextLayout = { if (day == 0) firstLineCharacters = it.getLineEnd(0) })
                            }
                        }
                    }
                }
            }
        }
        var original = 0
        compose.runOnIdle { original = firstLineCharacters; percent = 60 }
        compose.runOnIdle { assertTrue("Characters per line: $original before, $firstLineCharacters after shrinking", firstLineCharacters > original) }
    }
}
