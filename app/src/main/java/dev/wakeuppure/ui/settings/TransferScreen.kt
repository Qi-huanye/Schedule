package dev.wakeuppure.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wakeuppure.data.export.ScheduleExporter
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.PureViewModel
import dev.wakeuppure.ui.background.backgroundPanelColor
import dev.wakeuppure.ui.background.backgroundScreenColor
import dev.wakeuppure.ui.components.*
import kotlinx.coroutines.*

private enum class ImportSheet { TEXT, TOKEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(vm: PureViewModel, all: List<ScheduleData>, data: ScheduleData?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<ImportSheet?>(null) }
    var text by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    val pending by vm.imported.collectAsState()
    val experimental by vm.experimentalToken.collectAsState()
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun read(uri: android.net.Uri?, profile: Boolean) {
        if (uri == null) return
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 5_000_000)
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                }
                if (profile) { vm.saveProfile(content); toast("已读取协议配置") } else vm.parseImport(content)
            } catch (_: Exception) { toast("无法读取文件，或文件超过 5 MB") }
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { read(it, false) }
    val profile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { read(it, true) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { try {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(exportText) } }
            toast("文件已导出")
        } catch (_: Exception) { toast("导出失败，请检查文件位置") } }
    }
    fun export(name: String, build: () -> String) {
        runCatching { exportText = build(); save.launch(name) }.onFailure { toast("导出失败，请检查作息时间") }
    }
    fun openSheet(target: ImportSheet) { text = ""; vm.clearImport(); sheet = target }
    // A parsed import replaces the input sheet with the shared confirmation dialog.
    LaunchedEffect(pending) { if (pending != null) sheet = null }
    Scaffold(containerColor = backgroundScreenColor(), topBar = { TopAppBar(title = { Text("导入与导出") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { inset ->
        Column(Modifier.padding(inset).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            GroupTitle("导入")
            Surface(onClick = { file.launch(arrayOf("*/*")) }, enabled = !busy, shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.FolderOpen, null)
                    Column {
                        Text("选择文件", style = MaterialTheme.typography.titleMedium)
                        Text("ICS · JSON · 备份", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ActionRow(Icons.Default.ContentPaste, "粘贴文本", !busy, chevron = true) { openSheet(ImportSheet.TEXT) }
            ActionRow(Icons.Default.Link, "WakeUp 分享口令", !busy, chevron = true) { openSheet(ImportSheet.TOKEN) }
            data?.let { current ->
                GroupTitle("导出当前课表")
                ActionRow(Icons.Default.Download, "WakeUp JSON") { export("Schedule.json") { ScheduleExporter.json(current) } }
                ActionRow(Icons.Default.Download, "ICS 日历") { export("Schedule.ics") { ScheduleExporter.ics(current) } }
                ActionRow(Icons.Default.Share, "分享文本") {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, ScheduleExporter.legacy(current))
                    }, "分享课表"))
                }
            }
            GroupTitle("备份")
            ActionRow(Icons.Default.Download, "备份全部课表", all.isNotEmpty()) {
                export("Schedule-backup.json") { ScheduleExporter.backup(all, vm.appearance.value) }
            }
        }
    }
    when (sheet) {
        ImportSheet.TEXT -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle("粘贴文本", "解析", enabled = text.isNotBlank() && !busy) { vm.parseImport(text) }
                OutlinedTextField(text, { text = it }, enabled = !busy, label = { Text("ICS、JSON 或分享文本") },
                    modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 9)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        ImportSheet.TOKEN -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Box(Modifier.padding(horizontal = 16.dp)) { SheetTitle("WakeUp 分享口令") }
                OutlinedTextField(text, { text = it }, enabled = !busy, label = { Text("口令或分享文案") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), maxLines = 4)
                SwitchRow("实验兼容模式", experimental, vm::setExperimentalToken, enabled = !busy, subtitle = "设备被拒绝时开启")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { profile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, enabled = !busy) { Text("协议配置") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { vm.importToken(text) }, enabled = text.isNotBlank() && !busy) { Text("联网获取") }
                }
            }
        }
        null -> {}
    }
    pending?.let { schedules ->
        AlertDialog(onDismissRequest = vm::clearImport, title = { Text("导入课表") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                schedules.forEach { Text("${it.schedule.name} · ${it.courses.size} 门课程") }
            }
        }, confirmButton = { TextButton(onClick = { vm.acceptImport { toast("课表已导入") } }, enabled = !busy) { Text("导入") } },
            dismissButton = { TextButton(onClick = vm::clearImport, enabled = !busy) { Text("取消") } })
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, enabled: Boolean = true, chevron: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
