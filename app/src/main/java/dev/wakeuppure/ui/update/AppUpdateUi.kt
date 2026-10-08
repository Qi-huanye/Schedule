package dev.wakeuppure.ui.update

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wakeuppure.BuildConfig
import dev.wakeuppure.data.update.AppRelease

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
    Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
    ListItem(
        headlineContent = { Text("自动检查更新") },
        supportingContent = { Text("打开应用时每天最多检查一次，发现新版时提示。关闭后仍可手动检查。") },
        trailingContent = { Switch(checked = state.autoCheckEnabled, onCheckedChange = null) },
        modifier = Modifier.fillMaxWidth().toggleable(
            value = state.autoCheckEnabled, role = Role.Switch, onValueChange = onAutomaticChanged,
        ),
    )
    OutlinedButton(onClick = onCheck, enabled = !state.checking, modifier = Modifier.fillMaxWidth()) {
        if (state.checking) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (state.checking) "正在检查…" else "检查更新")
    }
    state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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
                Text("前往 GitHub 发布页下载 APK。可在“我的”中关闭自动检查更新。", style = MaterialTheme.typography.bodySmall)
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
