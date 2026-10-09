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

    @Test fun downloadStatesOfferCancelRetryBrowserAndInstall() {
        val downloadable = release.copy(apkName = "Schedule-0.3.0.apk", apkUrl = "https://github.com/a.apk", checksumUrl = "https://github.com/s.txt")
        var download by mutableStateOf<UpdateDownload>(UpdateDownload.Idle)
        var canInstall by mutableStateOf(true)
        val clicks = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                AppUpdateDialog(downloadable, { clicks += "dismiss" }, { clicks += "skip" }, { clicks += "update" }, download, canInstall,
                    { clicks += "cancel" }, { clicks += "browser" }, { clicks += "install" })
            }
        }
        compose.onNodeWithText("更新").performClick()
        compose.runOnIdle { download = UpdateDownload.Running(6_400_000, 12_800_000) }
        compose.onNodeWithText("6.1 / 12.2 MB").assertIsDisplayed()
        compose.onNodeWithText("稍后").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { download = UpdateDownload.Failed("安装包校验失败，请重试") }
        compose.onNodeWithText("安装包校验失败，请重试").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("浏览器下载").performClick()
        compose.runOnIdle { download = UpdateDownload.Ready(java.io.File("Schedule-0.3.0.apk")); canInstall = false }
        compose.onNodeWithText("需要允许 Schedule 安装应用").assertIsDisplayed()
        compose.onNodeWithText("去设置").performClick()
        compose.runOnIdle { canInstall = true }
        compose.onNodeWithText("安装").performClick()
        compose.runOnIdle { assertEquals(listOf("update", "cancel", "update", "browser", "install", "install"), clicks) }
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
