package dev.wakeuppure.ui.course

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.background.backgroundPanelColor
import dev.wakeuppure.ui.background.backgroundScreenColor
import dev.wakeuppure.ui.components.WEEKDAYS
import dev.wakeuppure.ui.components.WEEK_TYPES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditor(data: ScheduleData, initial: CourseWithPeriods?, busy: Boolean, onClose: () -> Unit,
    onDelete: (CourseWithPeriods) -> Unit, onSave: (Course, List<CoursePeriod>, List<TimeSlot>) -> Unit) {
    var course by remember { mutableStateOf(initial?.course ?: Course(scheduleId = data.schedule.id, name = "", color = CoursePalettes.color(data.schedule.colorPalette, data.courses.size))) }
    fun defaultPeriod() = CoursePeriod(endSection = minOf(2, data.timeSlots.size), endWeek = minOf(16, data.schedule.maxWeeks))
    val periods = remember { mutableStateListOf<CoursePeriod>().apply { addAll(initial?.periods ?: listOf(defaultPeriod())) } }
    var slots by remember { mutableStateOf(data.timeSlots.sortedBy { it.section }) }
    var editingPeriod by remember { mutableStateOf<Int?>(null) }
    // A period added from "添加时间" is dropped again if its sheet is dismissed without confirming.
    var addedPeriod by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun save() {
        invalid = course.name.isBlank()
        if (!invalid) onSave(course, periods.toList(), slots)
    }
    Scaffold(containerColor = backgroundScreenColor(), topBar = { TopAppBar(title = { Text(if (initial == null) "添加课程" else "编辑课程") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭") } },
        actions = { TextButton(onClick = ::save, enabled = !busy) { Text("保存") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(course.name, { course = course.copy(name = it); invalid = false }, label = { Text("课程名") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), isError = invalid, supportingText = if (invalid) { { Text("请填写课程名") } } else null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(course.teacher, { course = course.copy(teacher = it) }, label = { Text("教师") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(course.classroom, { course = course.copy(classroom = it) }, label = { Text("教室") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (CoursePalettes.find(data.schedule.colorPalette) ?: CoursePalettes.all.first()).colors.forEach { hex ->
                    Box(Modifier.size(40.dp).background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                        .border(if (course.color == hex) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { course = course.copy(color = hex) }, contentAlignment = Alignment.Center) {
                        if (course.color == hex) Icon(Icons.Default.Check, "已选颜色", tint = Color(0xFF24463F))
                    }
                }
            }
            Text("上课时间", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            periods.forEachIndexed { index, period ->
                Surface(onClick = { addedPeriod = false; editingPeriod = index }, shape = RoundedCornerShape(16.dp),
                    color = backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("周${WEEKDAYS[period.dayOfWeek - 1]} · ${period.startSection}–${period.endSection} 节", style = MaterialTheme.typography.titleMedium)
                            Text(periodWeeks(period, course.classroom), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "编辑上课时间 ${index + 1}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = { periods.add(defaultPeriod()); addedPeriod = true; editingPeriod = periods.lastIndex }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("添加时间")
            }
            OutlinedTextField(course.note, { course = course.copy(note = it) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (initial != null) TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除课程") }
        }
    }
    editingPeriod?.let { index ->
        PeriodSheet(periods[index], slots, data.schedule.maxWeeks, data.schedule.fixedLessonMinutes, course.classroom,
            canDelete = periods.size > 1,
            onDismiss = { if (addedPeriod) periods.removeAt(index); addedPeriod = false; editingPeriod = null },
            onDelete = { periods.removeAt(index); addedPeriod = false; editingPeriod = null }) { period, updatedSlots ->
            periods[index] = period
            slots = updatedSlots
            addedPeriod = false
            editingPeriod = null
        }
    }
    if (confirmDelete && initial != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除 ${initial.course.name}？") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

/** "第 3–18 周 · 单周 · 实验楼 101" — the room only when it differs from the course room. */
internal fun periodWeeks(period: CoursePeriod, courseRoom: String): String = listOfNotNull(
    "第 ${period.startWeek}–${period.endWeek} 周",
    WEEK_TYPES[period.weekType.ordinal].takeIf { period.weekType != WeekType.ALL },
    period.classroom.takeIf { it.isNotBlank() && it != courseRoom },
).joinToString(" · ")
