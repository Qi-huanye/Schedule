package dev.wakeuppure.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.CourseFilter
import dev.wakeuppure.domain.usecase.SemesterAdjustment
import dev.wakeuppure.ui.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Semester start, length and the week of the first lesson. Moving the start date asks once
 * whether lessons keep their calendar dates or their week numbers; the strip previews the result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TermSheet(data: ScheduleData, busy: Boolean, onDismiss: () -> Unit, onSave: (Schedule, Boolean, Int) -> Unit) {
    val oldDate = data.schedule.semesterStartDate
    var date by remember { mutableStateOf(oldDate) }
    var maxWeeks by remember { mutableIntStateOf(data.schedule.maxWeeks) }
    var preserveDates by remember { mutableStateOf(true) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val periods = data.courses.flatMap { it.periods }
    val firstWeek = periods.minOfOrNull { it.startWeek } ?: 1
    val dateOffset = if (preserveDates) runCatching { SemesterAdjustment.dateOffset(oldDate, date) }.getOrDefault(0) else 0
    val proposed = runCatching { SemesterAdjustment.adjust(periods, oldDate, date, preserveDates, weekOffset) }
    val lastWeek = proposed.getOrNull()?.maxOfOrNull { it.endWeek } ?: 1
    val weeks = maxOf(maxWeeks, lastWeek)
    val first = firstWeek + dateOffset + weekOffset
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                SheetTitle("学期", "确定", enabled = !busy && proposed.isSuccess) {
                    onSave(data.schedule.copy(semesterStartDate = date, maxWeeks = weeks, updatedAt = System.currentTimeMillis()), preserveDates, weekOffset)
                }
            }
            SettingRow("开学日期", date) { picking = true }
            if (periods.isNotEmpty() && date != oldDate) Segments(listOf(true to "课程日期不变", false to "周次不变"), preserveDates,
                { preserveDates = it; weekOffset = 0 }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            StepperRow("学期周数", "$weeks", { maxWeeks = weeks - 1 }, { maxWeeks = weeks + 1 }, weeks - 1 >= maxOf(1, lastWeek), weeks < 60)
            if (periods.isNotEmpty()) {
                StepperRow("第一节课在", "第 $first 周", { weekOffset-- }, { weekOffset++ }, first > 1, first < 60)
                proposed.getOrNull()?.let { WeekStrip(it, weeks) }
                proposed.exceptionOrNull()?.message?.let {
                    Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (picking) StartDatePicker(date, { picking = false }) { date = it; weekOffset = 0; picking = false }
}

/** One bar per week; filled weeks have at least one lesson. */
@Composable
private fun WeekStrip(periods: List<CoursePeriod>, weeks: Int) {
    val busyWeeks = (1..weeks).filter { w -> periods.any { CourseFilter.matches(it, w) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .semantics { contentDescription = "有课周次：${busyWeeks.firstOrNull() ?: "-"}–${busyWeeks.lastOrNull() ?: "-"}" }) {
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..weeks).forEach { w ->
                Box(Modifier.weight(1f).fillMaxHeight().background(
                    if (w in busyWeeks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(2.dp)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("1", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$weeks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartDatePicker(date: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } },
            enabled = picker.selectedDateMillis != null) { Text("确定") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) {
        DatePicker(state = picker, title = { Text("开学日期", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
