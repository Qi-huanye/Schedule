@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.settings

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.components.*

@Composable
internal fun RenameDialog(name: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("课表名称") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

/** Show stored custom colors alongside presets when the current schedule uses them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaletteSheet(palette: String, followsBackground: Boolean, courseColors: List<Color>, backgroundColors: List<Int>?,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val selected = if (followsBackground && backgroundColors != null) FOLLOW_BACKGROUND else palette
    var choice by rememberSaveable(selected) { mutableStateOf(selected) }
    val options = buildList {
        if (backgroundColors != null) add(Triple(FOLLOW_BACKGROUND, "跟随背景", backgroundColors.map { Color(it) }))
        if (palette == "custom") add(Triple("custom", "当前课程颜色", courseColors.distinct()))
        CoursePalettes.all.forEach { add(Triple(it.id, it.name, it.colors.map(::hexColor))) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle("课程配色", "确定") { onConfirm(choice) }
            Text("选择后点击确定应用。预设配色会替换各课程的颜色。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
            options.forEach { (id, name, colors) ->
                ColorPaletteOption(name, colors.take(6), choice == id) { choice = id }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReminderSheet(current: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val presets = listOf(5, 10, 15, 30)
    var custom by remember { mutableStateOf(current != null && current !in presets) }
    var text by remember { mutableStateOf(current?.takeIf { it !in presets }?.toString() ?: "") }
    val minutes = text.toIntOrNull()?.takeIf { it in 0..1440 }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SheetTitle("课前提醒")
            val options: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to "关闭") + presets.map { it to "$it 分钟" }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    FilterChip(selected = !custom && current == value, onClick = { onPick(value) }, label = { Text(label) })
                }
                FilterChip(selected = custom, onClick = { custom = true }, label = { Text("自定义") })
            }
            if (custom) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("提前分钟数") }, singleLine = true, isError = text.isNotEmpty() && minutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                Button(shapes = ButtonDefaults.shapes(), onClick = { onPick(minutes) }, enabled = minutes != null) { Text("确定") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScheduleSheet(busy: Boolean, onDismiss: () -> Unit, onCreate: (Schedule) -> Unit) {
    var schedule by remember { mutableStateOf(newSchedule()) }
    var picking by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                SheetTitle("新建课表", "创建", enabled = !busy && schedule.name.isNotBlank()) {
                    onCreate(schedule.copy(name = schedule.name.trim(), current = true))
                }
            }
            OutlinedTextField(schedule.name, { schedule = schedule.copy(name = it) }, label = { Text("名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            SettingRow("开学日期", schedule.semesterStartDate) { picking = true }
            StepperRow("学期周数", "${schedule.maxWeeks}", { schedule = schedule.copy(maxWeeks = schedule.maxWeeks - 1) },
                { schedule = schedule.copy(maxWeeks = schedule.maxWeeks + 1) }, schedule.maxWeeks > 1, schedule.maxWeeks < 60)
        }
    }
    if (picking) StartDatePicker(schedule.semesterStartDate, { picking = false }) { schedule = schedule.copy(semesterStartDate = it); picking = false }
}
