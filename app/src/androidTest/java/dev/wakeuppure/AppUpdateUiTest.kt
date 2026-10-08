package dev.wakeuppure

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wakeuppure.data.update.AppRelease
import dev.wakeuppure.ui.update.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateUiTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("0.3.0", "修复课程显示\n支持自动检查更新", "https://github.com/Qi-huanye/Schedule/releases/tag/v0.3.0")

    @Test fun promptCanBeDismissedSkippedAndOpened() {
        var visible by mutableStateOf(true)
        var skipped = false
        var opened = false
        compose.setContent {
            MaterialTheme {
                if (visible) AppUpdateDialog(release, { visible = false }, { skipped = true; visible = false }, { opened = true })
            }
        }
        compose.onNodeWithText("发现新版本 0.3.0").assertIsDisplayed()
        compose.onNodeWithText("稍后").performClick()
        compose.onNodeWithText("发现新版本 0.3.0").assertDoesNotExist()
        compose.runOnIdle { assertFalse(skipped); visible = true }
        compose.onNodeWithText("跳过此版本").performClick()
        compose.runOnIdle { assertTrue(skipped); visible = true }
        compose.onNodeWithText("前往下载").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun disablingAutomaticChecksKeepsManualButtonAvailable() {
        var state by mutableStateOf(AppUpdateState())
        var checked = false
        compose.setContent {
            MaterialTheme {
                Column { AppUpdateSettings(state, { state = state.copy(autoCheckEnabled = it) }, { checked = true }) }
            }
        }
        compose.onNodeWithText("自动检查更新").performClick().assertIsOff()
        compose.onNodeWithText("检查更新").assertIsEnabled().performClick()
        compose.runOnIdle { assertFalse(state.autoCheckEnabled); assertTrue(checked) }
    }

    @Test fun checkingAndFailureAreVisibleWithoutDuplicateRequests() {
        var state by mutableStateOf(AppUpdateState(checking = true))
        compose.setContent { MaterialTheme { Column { AppUpdateSettings(state, {}, {}) } } }
        compose.onNodeWithText("正在检查…").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(checking = false, message = "检查更新失败，请检查网络后重试。") }
        compose.onNodeWithText("检查更新失败，请检查网络后重试。").assertIsDisplayed()
        compose.onNodeWithText("检查更新").assertIsEnabled()
    }
}
