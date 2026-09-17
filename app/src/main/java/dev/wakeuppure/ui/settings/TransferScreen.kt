package dev.wakeuppure.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wakeuppure.ui.PureViewModel
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.data.export.ScheduleExporter
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(vm: PureViewModel, all: List<ScheduleData>, data: ScheduleData?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var exportText by remember { mutableStateOf("") }
    var localStatus by remember { mutableStateOf<String?>(null) }
    val busy by vm.busy.collectAsState()
    val pending by vm.imported.collectAsState()
    val experimental by vm.experimentalToken.collectAsState()
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
                if (profile) { vm.saveProfile(content); localStatus = "已读取协议配置，校验失败会显示错误" }
                else { text = content; vm.parseImport(content) }
            } catch (_: Exception) { localStatus = "无法读取文件，或文件超过 5 MB" }
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { read(it, false) }
    val profile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { read(it, true) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { try {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(exportText) } }
            localStatus = "文件已导出"
        } catch (_: Exception) { localStatus = "导出失败，请检查文件位置" } }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("导入 / 导出") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { inset ->
        Column(Modifier.padding(inset).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("导入课表 · WakeUp", style = MaterialTheme.typography.titleLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("文件 / 文本", "分享口令").forEachIndexed { i, label -> SegmentedButton(selected = mode == i, enabled = !busy, onClick = { mode = i; vm.clearImport() }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) } }
            }
            OutlinedTextField(text, { text = it; vm.clearImport() }, enabled = !busy, label = { Text(if (mode == 0) "ICS、JSON 或分享文本" else "WakeUp 分享口令") }, modifier = Modifier.fillMaxWidth(), minLines = if (mode == 0) 5 else 1, maxLines = 9)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (mode == 0) vm.parseImport(text) else vm.importToken(text) }, enabled = text.isNotBlank() && !busy) { Text(if (mode == 0) "解析课表" else "联网获取课表") }
                OutlinedButton(onClick = { file.launch(arrayOf("*/*")) }, enabled = !busy) { Icon(Icons.Default.FolderOpen, null); Text("选择文件") }
            }
            if (mode == 1) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("实验兼容模式", Modifier.weight(1f))
                    Switch(checked = experimental, onCheckedChange = vm::setExperimentalToken, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "实验兼容模式" })
                }
                Text(if (experimental) "使用公共兼容身份直接访问 WakeUp，不发送本机 Android ID。服务端变化可能导致失效。仅点击联网获取时访问网络。"
                    else "使用本机设备身份。若提示设备被拒绝，可开启实验兼容模式，或使用 ICS / JSON 文件。仅点击联网获取时访问网络。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { profile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) { Text("导入协议配置") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            pending?.let { schedules ->
                Text("导入预览", style = MaterialTheme.typography.titleMedium)
                schedules.forEach { Text("${it.schedule.name} · ${it.courses.size} 门课程 · ${it.schedule.maxWeeks} 周") }
                Text("将新增课表，保留现有数据。", style = MaterialTheme.typography.bodySmall)
                Row {
                    Button(onClick = { vm.acceptImport { localStatus = "课表已导入"; text = "" } }, enabled = !busy) { Text("确认导入") }
                    TextButton(onClick = { vm.clearImport() }, enabled = !busy) { Text("取消") }
                }
            }
            HorizontalDivider()
            Text("导出与备份", style = MaterialTheme.typography.titleLarge)
            data?.let { current ->
                listOf("WakeUp JSON" to "json", "旧版分享文本" to "txt", "ICS 日历" to "ics").forEachIndexed { i, (label, extension) ->
                    OutlinedButton(onClick = {
                        runCatching { exportText = when(i) { 0 -> ScheduleExporter.json(current); 1 -> ScheduleExporter.legacy(current); else -> ScheduleExporter.ics(current) }
                            save.launch("Schedule.$extension") }.onFailure { localStatus = "导出失败，请检查作息时间" }
                    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, ScheduleExporter.legacy(current))
                    }, "分享课表"))
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Text("分享旧版文本") }
            }
            Button(onClick = { exportText = ScheduleExporter.backup(all, vm.appearance.value); save.launch("Schedule-backup.json") }, enabled = all.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("备份全部课表") }
            localStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
