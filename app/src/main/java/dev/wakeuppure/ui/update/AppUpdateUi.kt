package dev.wakeuppure.ui.update

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wakeuppure.BuildConfig
import dev.wakeuppure.data.update.AppRelease
import dev.wakeuppure.ui.components.SettingRow
import dev.wakeuppure.ui.components.SwitchRow

@Composable
fun AppUpdateHost(vm: AppUpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.checkForUpdates() }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    state.release?.let { release ->
        AppUpdateDialog(release, vm::dismissUpdate, vm::skipVersion) {
            try {
                uriHandler.openUri(release.pageUrl)
                vm.dismissUpdate()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "未找到可用浏览器，请安装浏览器后重试。", Toast.LENGTH_LONG).show()
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, "无法打开下载页面，请稍后重试。", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun AppUpdateSettings(state: AppUpdateState, onAutomaticChanged: (Boolean) -> Unit, onCheck: () -> Unit) {
    SettingRow("版本 ${BuildConfig.VERSION_NAME}", chevron = false, trailing = {
        TextButton(onClick = onCheck, enabled = !state.checking) {
            if (state.checking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.checking) "正在检查…" else "检查更新")
        }
    })
    SwitchRow("自动检查更新", state.autoCheckEnabled, onAutomaticChanged)
    state.message?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun AppUpdateDialog(release: AppRelease, onDismiss: () -> Unit, onSkip: () -> Unit, onDownload: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${release.version}") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前版本 ${BuildConfig.VERSION_NAME}")
                Text(release.notes.ifBlank { "此版本未提供更新说明。" })
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("前往下载") } },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("跳过此版本") }
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        },
    )
}
