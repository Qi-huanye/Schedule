@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.wakeuppure.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.CourseWithPeriods
import dev.wakeuppure.domain.model.Todo
import dev.wakeuppure.ui.components.SheetTitle
import dev.wakeuppure.ui.settings.LessonTimeDialog
import java.time.LocalTime
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun TodoEditorSheet(scheduleId: Long, courses: List<CourseWithPeriods>, now: LocalDateTime, onDismiss: () -> Unit, onSave: (Todo) -> Unit, todo: Todo? = null, onDelete: (() -> Unit)? = null, busy: Boolean = false) {
    var title by remember { mutableStateOf(todo?.title.orEmpty()) }
    var due by remember { mutableStateOf(todo?.let { Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: now.plusHours(1).withSecond(0).withNano(0)) }
    var courseId by remember { mutableStateOf<Long?>(todo?.courseId) }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE") }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle(if (todo == null) "添加待办" else "编辑待办", "保存", enabled = title.isNotBlank() && !busy) {
                onSave(Todo(id = todo?.id ?: 0, scheduleId = scheduleId, title = title.trim(), dueAt = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), courseId = courseId, completed = todo?.completed ?: false))
            }
            OutlinedTextField(title, { title = it.take(200) }, enabled = !busy, label = { Text("待办事项") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PickerField("截止日期", due.format(dateFormat), Icons.Default.CalendarMonth) { showDatePicker = true }
            PickerField("截止时间", due.format(timeFormat), Icons.Default.Schedule) { showTimePicker = true }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(courses.firstOrNull { it.course.id == courseId }?.course?.name ?: "不关联课程", {}, readOnly = true,
                    label = { Text("关联课程（可选）") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("不关联课程") }, onClick = { courseId = null; expanded = false })
                    courses.forEach { item -> DropdownMenuItem(text = { Text(item.course.name) }, onClick = { courseId = item.course.id; expanded = false }) }
                }
            }
            if (todo != null) FilledTonalButton(onClick = { onSave(todo.copy(title = title.trim(), dueAt = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), courseId = courseId, completed = !todo.completed)) }, enabled = title.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (todo.completed) "恢复未完成" else "标记完成")
            }
            if (onDelete != null) TextButton(onClick = { confirmDelete = true }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除待办") }
        }
    }
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = due.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    due = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atTime(due.toLocalTime())
                }
                showDatePicker = false
            }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }) {
            DatePicker(state)
        }
    }
    if (showTimePicker) LessonTimeDialog(due.format(timeFormat), "待办截止时间", { showTimePicker = false }) {
        due = due.toLocalDate().atTime(LocalTime.parse(it)); showTimePicker = false
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除待办？") }, text = { Text(todo?.title.orEmpty()) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

@Composable
private fun PickerField(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(label) }, trailingIcon = { Icon(icon, null) },
            modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().semantics(mergeDescendants = true) { contentDescription = "$label，$value" }
            .clickable(role = Role.Button, onClick = onClick))
    }
}
