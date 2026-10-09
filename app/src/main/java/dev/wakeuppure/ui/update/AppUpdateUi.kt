package dev.wakeuppure.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wakeuppure.BuildConfig
import dev.wakeuppure.data.update.AppRelease
import dev.wakeuppure.ui.components.SettingRow
import dev.wakeuppure.ui.components.SwitchRow
import java.io.File

@Composable
fun AppUpdateHost(vm: AppUpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.checkForUpdates() }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    // Returning from the "install unknown apps" setting re-checks the permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = context.packageManager.canRequestPackageInstalls() }
    val download = state.download
    // Opens the installer once per finished download, and again right after the permission is granted.
    LaunchedEffect(download, canInstall) { if (download is UpdateDownload.Ready && canInstall) openInstaller(context, download.file) }
    fun openPage(release: AppRelease) {
        try {
            uriHandler.openUri(release.pageUrl)
            vm.dismissUpdate()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可用浏览器，请安装浏览器后重试。", Toast.LENGTH_LONG).show()
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, "无法打开下载页面，请稍后重试。", Toast.LENGTH_LONG).show()
        }
    }
    state.release?.let { release ->
        AppUpdateDialog(release, vm::dismissUpdate, vm::skipVersion,
            onUpdate = { if (release.apkUrl != null) vm.download() else openPage(release) },
            download = download, canInstall = canInstall, onCancel = vm::cancelDownload, onBrowser = { openPage(release) },
            onInstall = { if (download is UpdateDownload.Ready && canInstall) openInstaller(context, download.file) else openInstallSettings(context) })
    }
}

private fun openInstaller(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "无法打开系统安装程序。", Toast.LENGTH_LONG).show()
    }
}

private fun openInstallSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "请在系统设置中允许 Schedule 安装应用。", Toast.LENGTH_LONG).show()
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
fun AppUpdateDialog(release: AppRelease, onDismiss: () -> Unit, onSkip: () -> Unit, onUpdate: () -> Unit,
    download: UpdateDownload = UpdateDownload.Idle, canInstall: Boolean = true, onCancel: () -> Unit = {},
    onBrowser: () -> Unit = {}, onInstall: () -> Unit = {}) {
    val running = download is UpdateDownload.Running
    AlertDialog(
        // A running download is only stopped through 取消, never by tapping outside.
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("发现新版本 ${release.version}") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前版本 ${BuildConfig.VERSION_NAME}")
                ReleaseNotes(release.notes)
                when (download) {
                    is UpdateDownload.Running -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (download.total > 0) LinearProgressIndicator({ (download.read.toFloat() / download.total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(megabytes(download.read, download.total), style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateDownload.Failed -> Text(download.message, color = MaterialTheme.colorScheme.error)
                    is UpdateDownload.Ready -> if (!canInstall) Text("需要允许 Schedule 安装应用", color = MaterialTheme.colorScheme.primary)
                    UpdateDownload.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (download) {
                UpdateDownload.Idle -> TextButton(onClick = onUpdate) { Text(if (release.apkUrl != null) "更新" else "前往下载") }
                is UpdateDownload.Running -> TextButton(onClick = onCancel) { Text("取消") }
                is UpdateDownload.Failed -> TextButton(onClick = onUpdate) { Text("重试") }
                is UpdateDownload.Ready -> TextButton(onClick = onInstall) { Text(if (canInstall) "安装" else "去设置") }
            }
        },
        dismissButton = {
            when (download) {
                UpdateDownload.Idle -> Row {
                    TextButton(onClick = onSkip) { Text("跳过此版本") }
                    TextButton(onClick = onDismiss) { Text("稍后") }
                }
                is UpdateDownload.Failed -> Row {
                    TextButton(onClick = onBrowser) { Text("浏览器下载") }
                    TextButton(onClick = onDismiss) { Text("稍后") }
                }
                is UpdateDownload.Ready -> TextButton(onClick = onDismiss) { Text("稍后") }
                is UpdateDownload.Running -> {}
            }
        },
    )
}

@Composable
private fun ReleaseNotes(notes: String) {
    val lines = releaseNoteLines(notes)
    if (lines.isEmpty()) Text("此版本未提供更新说明。")
    else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            if (line.heading) Text(line.text, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall)
            else Text(line.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal data class NoteLine(val text: String, val heading: Boolean)

/** Release notes are GitHub Markdown; show headings and bullets without their raw markers. */
internal fun releaseNoteLines(notes: String): List<NoteLine> = notes.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
    val heading = Regex("^#{1,6}\\s+").find(line)
    val text = when {
        heading != null -> line.substring(heading.range.last + 1)
        line.startsWith("- ") || line.startsWith("* ") -> "• " + line.drop(2)
        else -> line
    }
    NoteLine(text.replace("**", "").replace("`", ""), heading != null)
}

private fun megabytes(read: Long, total: Long): String {
    fun mb(bytes: Long) = "%.1f".format(bytes / 1_048_576.0)
    return if (total > 0) "${mb(read)} / ${mb(total)} MB" else "${mb(read)} MB"
}
