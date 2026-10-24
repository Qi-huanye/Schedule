@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.ui.background.courseDisplayColors
import dev.wakeuppure.ui.components.*

private fun WeekType.includes(week: Int) = when (this) {
    WeekType.ALL -> true
    WeekType.ODD -> week % 2 == 1
    WeekType.EVEN -> week % 2 == 0
}

internal fun Set<Int>.toggle(week: Int): Set<Int> = if (week in this && size > 1) this - week else this + week

internal fun Set<Int>.toWeekRanges(): List<IntRange> = sorted().fold(mutableListOf()) { ranges, week ->
    val previous = ranges.lastOrNull()
    if (previous != null && week == previous.last + 1) ranges[ranges.lastIndex] = previous.first..week
    else ranges += week..week
    ranges
}

internal fun Set<Int>.weekSummary(): String = toWeekRanges().joinToString("、") { range ->
    if (range.first == range.last) "第 ${range.first} 周" else "第 ${range.first}–${range.last} 周"
}.ifBlank { "未选择周次" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodSheet(initial: CoursePeriod, initialSlots: List<TimeSlot>, maxWeeks: Int, defaultRoom: String,
    canDelete: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onConfirm: (List<CoursePeriod>) -> Unit) {
    var period by remember { mutableStateOf(initial) }
    var sections by remember { mutableStateOf(RangePick(initial.startSection, initial.endSection)) }
    var selectedWeeks by remember { mutableStateOf((initial.startWeek..initial.endWeek).filter { it in 1..maxWeeks }.toSet()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle("上课时间", "确定") {
                onConfirm(selectedWeeks.toWeekRanges().mapIndexed { index, range ->
                    period.copy(id = if (index == 0) initial.id else 0, startSection = sections.start, endSection = sections.end,
                        startWeek = range.first, endWeek = range.last)
                })
            }
            SheetLabel("星期")
            SelectGrid((1..7).toList(), 7, { if (it == period.dayOfWeek) CellState.EDGE else CellState.NONE },
                { period = period.copy(dayOfWeek = it) }, label = { WEEKDAYS[it - 1] }, describe = { "周${WEEKDAYS[it - 1]}" })
            SheetLabel("节次", "${initialSlots[sections.start - 1].startTime}–${initialSlots[sections.end - 1].endTime}")
            SelectGrid((1..initialSlots.size).toList(), 5, { rangeState(it, sections.start, sections.end) }, { sections = sections.tap(it) },
                describe = { "第 $it 节" })
            SheetLabel("周次", selectedWeeks.weekSummary())
            Segments(WeekType.entries.map { it to WEEK_TYPES[it.ordinal] }, period.weekType, { period = period.copy(weekType = it) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SelectGrid((1..maxWeeks).toList(), 5,
                { if (it in selectedWeeks && period.weekType.includes(it)) CellState.EDGE else CellState.NONE },
                { selectedWeeks = selectedWeeks.toggle(it) }, describe = { "第 $it 周" })
            OutlinedTextField(period.classroom, { period = period.copy(classroom = it) }, label = { Text("教室") },
                placeholder = if (defaultRoom.isNotBlank()) { { Text(defaultRoom) } } else null,
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            if (canDelete) TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除这个时间") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(item: CourseWithPeriods, slots: List<TimeSlot>, onDismiss: () -> Unit, onEdit: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(courseDisplayColors(item.course.id, item.course.color).first, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(item.course.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onEdit) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("编辑") }
            }
            item.periods.forEach { p ->
                val start = slots.find { it.section == p.startSection }?.startTime
                val end = slots.find { it.section == p.endSection }?.endTime
                Spacer(Modifier.height(6.dp))
                Text("周${WEEKDAYS[p.dayOfWeek - 1]} ${p.startSection}–${p.endSection} 节" + if (start != null && end != null) "，$start–$end" else "",
                    style = MaterialTheme.typography.bodyLarge)
                val room = p.classroom.ifBlank { item.course.classroom }
                Text(listOf(periodWeeks(p.copy(classroom = ""), ""), room).filter { it.isNotBlank() }.joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val extra = listOf(item.course.teacher, item.course.note).filter { it.isNotBlank() }
            if (extra.isNotEmpty()) Text(extra.joinToString("\n"), Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
