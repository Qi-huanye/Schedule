@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    var chooseJsonSchedule by remember { mutableStateOf(false) }
    var selectedJsonScheduleIds by remember { mutableStateOf<Set<Long>?>(null) }
    val exportSchedules = all.filter { selectedJsonScheduleIds?.contains(it.schedule.id) ?: true }
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = backgroundScreenColor(), topBar = { LargeTopAppBar(title = { Text("导入与导出") }, scrollBehavior = scrollBehavior,
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { inset ->
        Column(Modifier.padding(inset).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            GroupTitle("导入")
            TransferActionCard(Icons.Default.FolderOpen, "从文件导入", enabled = !busy, onClick = { file.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), prominent = true)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TransferActionCard(Icons.Default.ContentPaste, "文本导入", enabled = !busy, onClick = { openSheet(ImportSheet.TEXT) }, modifier = Modifier.weight(1f))
                TransferActionCard(Icons.Default.Link, "WakeUp 口令", enabled = !busy, onClick = { openSheet(ImportSheet.TOKEN) }, modifier = Modifier.weight(1f))
            }
            GroupTitle("导出")
            Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.DataObject, null, Modifier.size(30.dp))
                        Text("JSON", style = MaterialTheme.typography.headlineSmall)
                    }
                    FilledTonalButton(onClick = { chooseJsonSchedule = true },
                        enabled = all.isNotEmpty() && !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                        Text(when {
                            exportSchedules.size == all.size -> "全部课表"
                            exportSchedules.size == 1 -> exportSchedules.single().schedule.name
                            else -> "已选择 ${exportSchedules.size} 个课表"
                        }, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    Button(onClick = { export("Schedule.json") { ScheduleExporter.appJson(exportSchedules, vm.appearance.value) } },
                        enabled = exportSchedules.isNotEmpty() && !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("导出 JSON")
                    }
                }
            }
            data?.let { current ->
                TransferActionCard(Icons.Default.CalendarMonth, "ICS 日历", enabled = !busy,
                    onClick = { export("Schedule.ics") { ScheduleExporter.ics(current) } },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().padding(top = 12.dp))
                GroupTitle("WakeUp 兼容")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TransferActionCard(Icons.Default.DataObject, "WakeUp JSON", onClick = { export("WakeUp-Schedule.json") { ScheduleExporter.json(current) } }, modifier = Modifier.weight(1f))
                    TransferActionCard(Icons.Default.Share, "分享文本", onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, ScheduleExporter.legacy(current))
                        }, "分享课表"))
                    }, modifier = Modifier.weight(1f))
                }
            }

        }
    }
    when (sheet) {
        ImportSheet.TEXT -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle("粘贴文本", "解析", enabled = text.isNotBlank() && !busy) { vm.parseImport(text) }
                OutlinedTextField(text, { text = it }, enabled = !busy, label = { Text("ICS、JSON 或分享文本") },
                    modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 9)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        ImportSheet.TOKEN -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Box(Modifier.padding(horizontal = 16.dp)) { SheetTitle("WakeUp 分享口令") }
                OutlinedTextField(text, { text = it }, enabled = !busy, label = { Text("口令或分享文案") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), maxLines = 4)
                SwitchRow("实验兼容模式", experimental, vm::setExperimentalToken, enabled = !busy, subtitle = "设备被拒绝时开启")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { profile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, enabled = !busy) { Text("协议配置") }
                    Spacer(Modifier.weight(1f))
                    Button(shapes = ButtonDefaults.shapes(), onClick = { vm.importToken(text) }, enabled = text.isNotBlank() && !busy) { Text("联网获取") }
                }
            }
        }
        null -> {}
    }
    if (chooseJsonSchedule) ExportScheduleDialog(all, selectedJsonScheduleIds,
        onDismiss = { chooseJsonSchedule = false }, onConfirm = { ids ->
            selectedJsonScheduleIds = ids
            chooseJsonSchedule = false
        })
    pending?.let { schedules ->
        AlertDialog(onDismissRequest = vm::clearImport, title = { Text("导入课表") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                schedules.forEach { Text("${it.schedule.name}，${it.courses.size} 门课程，${it.todos.size} 项待办") }
            }
        }, confirmButton = { TextButton(onClick = { vm.acceptImport { toast("课表已导入") } }, enabled = !busy) { Text("导入") } },
            dismissButton = { TextButton(onClick = vm::clearImport, enabled = !busy) { Text("取消") } })
    }
}

@Composable
internal fun ExportScheduleDialog(all: List<ScheduleData>, selectedIds: Set<Long>?, onDismiss: () -> Unit, onConfirm: (Set<Long>?) -> Unit) {
    val availableIds = all.map { it.schedule.id }.toSet()
    var ids by remember { mutableStateOf(selectedIds?.intersect(availableIds) ?: availableIds) }
    val selectionState = when {
        ids.isEmpty() -> ToggleableState.Off
        ids == availableIds -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("导出范围") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().triStateToggleable(state = selectionState, role = Role.Checkbox,
                onClick = { ids = if (ids == availableIds) emptySet() else availableIds }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(state = selectionState, onClick = null)
                Text("全部课表", Modifier.padding(start = 8.dp))
            }
            all.forEach { schedule ->
                val id = schedule.schedule.id
                Row(Modifier.fillMaxWidth().toggleable(value = id in ids, role = Role.Checkbox,
                    onValueChange = { ids = if (it) ids + id else ids - id }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = id in ids, onCheckedChange = null)
                    Text(schedule.schedule.name, Modifier.padding(start = 8.dp))
                }
            }
        }
    }, confirmButton = { TextButton(enabled = ids.isNotEmpty(), onClick = { onConfirm(ids.takeUnless { it == availableIds }) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun TransferActionCard(icon: ImageVector, title: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier, prominent: Boolean = false) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = if (prominent) 92.dp else 76.dp),
        shape = if (prominent) RoundedCornerShape(topStart = 28.dp, topEnd = 20.dp, bottomEnd = 28.dp, bottomStart = 20.dp) else RoundedCornerShape(22.dp),
        color = if (prominent) MaterialTheme.colorScheme.secondaryContainer else backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentColor = if (prominent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = LocalContentColor.current.copy(alpha = 0.72f))
        }
    }
}
