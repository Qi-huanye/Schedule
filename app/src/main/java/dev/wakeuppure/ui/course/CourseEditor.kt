package dev.wakeuppure.ui.course

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.FixedLessonTime
import dev.wakeuppure.domain.usecase.TimeSlotPlanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditor(data: ScheduleData, initial: CourseWithPeriods?, busy: Boolean, onClose: () -> Unit,
    onSave: (Course, List<CoursePeriod>, List<TimeSlot>) -> Unit) {
    var course by remember { mutableStateOf(initial?.course ?: Course(scheduleId = data.schedule.id, name = "", color = CoursePalettes.color(data.schedule.colorPalette, data.courses.size))) }
    val periods = remember { mutableStateListOf<CoursePeriod>().apply { addAll(initial?.periods ?: listOf(CoursePeriod(endSection = minOf(2, data.timeSlots.size), endWeek = minOf(16, data.schedule.maxWeeks)))) } }
    var slots by remember { mutableStateOf(data.timeSlots.sortedBy { it.section }) }
    var editingPeriod by remember { mutableStateOf<Int?>(null) }
    var invalid by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (initial == null) "添加课程" else "编辑课程") }, navigationIcon = {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭") }
    }, actions = { IconButton(enabled = !busy, onClick = {
        invalid = course.name.isBlank() || periods.any { it.startWeek > it.endWeek || it.startSection > it.endSection }
        if (!invalid) onSave(course, periods.toList(), slots)
    }) { Icon(Icons.Default.Check, "保存课程") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(course.name, { course = course.copy(name = it) }, label = { Text("课程名") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = invalid && course.name.isBlank())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(course.teacher, { course = course.copy(teacher = it) }, label = { Text("教师") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(course.classroom, { course = course.copy(classroom = it) }, label = { Text("默认教室") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (CoursePalettes.find(data.schedule.colorPalette) ?: CoursePalettes.all.first()).colors.forEach { hex ->
                    Box(Modifier.size(42.dp).background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                        .border(if (course.color == hex) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { course = course.copy(color = hex) }, contentAlignment = Alignment.Center) {
                        if (course.color == hex) Icon(Icons.Default.Check, "已选颜色", tint = Color(0xFF24463F))
                    }
                }
            }
            Text("上课时段", style = MaterialTheme.typography.titleMedium)
            periods.forEachIndexed { index, period ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("时段 ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        if (periods.size > 1) IconButton(onClick = { periods.removeAt(index) }) { Icon(Icons.Default.Delete, "删除时段 ${index+1}") }
                    }
                    OutlinedCard(onClick = { editingPeriod = index }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${listOf("周一","周二","周三","周四","周五","周六","周日")[period.dayOfWeek - 1]} · 第 ${period.startSection}–${period.endSection} 节", style = MaterialTheme.typography.titleMedium)
                            Text("${slots[period.startSection - 1].startTime}–${slots[period.endSection - 1].endTime} · 点击选择节次", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Choice("起始周", period.startWeek, (1..data.schedule.maxWeeks).toList()) { periods[index] = period.copy(startWeek = it) } }
                        Box(Modifier.weight(1f)) { Choice("结束周", period.endWeek, (1..data.schedule.maxWeeks).toList()) { periods[index] = period.copy(endWeek = it) } }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeekType.entries.forEachIndexed { i, type -> SegmentedButton(
                            selected = period.weekType == type, onClick = { periods[index] = period.copy(weekType = type) },
                            shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(listOf("每周", "单周", "双周")[i]) } }
                    }
                    OutlinedTextField(period.classroom, { periods[index] = period.copy(classroom = it) }, label = { Text("本时段教室（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
            OutlinedButton(onClick = { periods.add(CoursePeriod(endSection = minOf(2, data.timeSlots.size), endWeek = minOf(16, data.schedule.maxWeeks))) }) { Icon(Icons.Default.Add, null); Text("添加上课时段") }
            OutlinedTextField(course.note, { course = course.copy(note = it) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (invalid) Text("请填写课程名，并检查起止节次和周次", color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                invalid = course.name.isBlank() || periods.any { it.startWeek > it.endWeek || it.startSection > it.endSection }
                if (!invalid) onSave(course, periods.toList(), slots)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("保存课程") }
        }
    }
    editingPeriod?.let { index ->
        SectionPicker(periods[index], slots, data.schedule.fixedLessonMinutes, onDismiss = { editingPeriod = null }) { period, updatedSlots ->
            periods[index] = period
            slots = updatedSlots
            editingPeriod = null
        }
    }

}

@Composable
fun Choice(label: String, value: Int, values: List<Int>, display: (Int) -> String = { it.toString() }, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label · ${display(value)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { item -> DropdownMenuItem(text = { Text(display(item)) }, onClick = { open = false; onSelect(item) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionPicker(initial: CoursePeriod, initialSlots: List<TimeSlot>, fixedMinutes: Int?, onDismiss: () -> Unit,
    onConfirm: (CoursePeriod, List<TimeSlot>) -> Unit) {
    var period by remember { mutableStateOf(initial) }
    var slots by remember { mutableStateOf(initialSlots) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选择上课时间", style = MaterialTheme.typography.headlineSmall)
            Choice("星期", period.dayOfWeek, (1..7).toList(), { listOf("周一","周二","周三","周四","周五","周六","周日")[it-1] }) { period = period.copy(dayOfWeek = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { Choice("开始", period.startSection, (1..slots.size).toList(), { "第 $it 节" }) { start ->
                    val length = period.endSection - period.startSection
                    period = period.copy(startSection = start, endSection = (start + length).coerceAtMost(slots.size))
                } }
                Box(Modifier.weight(1f)) { Choice("结束", period.endSection, (period.startSection..slots.size).toList(), { "第 $it 节" }) { period = period.copy(endSection = it) } }
            }
            Text("第 ${period.startSection}–${period.endSection} 节 · ${slots[period.startSection - 1].startTime}–${slots[period.endSection - 1].endTime}", color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text("找不到第 9–10 节？在这里增加每天节数。", style = MaterialTheme.typography.bodyMedium)
            Choice("每天节数", slots.size, (initialSlots.size..30).toList(), { "$it 节" }) { count ->
                runCatching { FixedLessonTime.apply(TimeSlotPlanner.resize(slots, count, initialSlots.size), fixedMinutes) }.onSuccess {
                    slots = it
                    period = period.copy(startSection = period.startSection.coerceAtMost(count), endSection = period.endSection.coerceAtMost(count))
                    error = null
                }.onFailure { error = it.message }
            }
            if (slots.size > initialSlots.size) Text("新增节次使用建议作息，保存课程后可在课表设置中修改。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onConfirm(period, slots) }, modifier = Modifier.fillMaxWidth()) { Text("确定时段") }
        }
    }
}
