package dev.wakeuppure.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.FixedLessonTime
import dev.wakeuppure.domain.usecase.TimeSlotPlanner
import dev.wakeuppure.domain.usecase.SemesterAdjustment
import java.time.Instant
import java.time.ZoneOffset
import dev.wakeuppure.ui.course.Choice
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditor(initial: ScheduleData?, busy: Boolean, onClose: () -> Unit,
    onSave: (Schedule, List<TimeSlot>, Boolean, Int) -> Unit) {
    var schedule by remember { mutableStateOf(initial?.schedule ?: newSchedule()) }
    var date by remember { mutableStateOf(schedule.semesterStartDate) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var preserveDates by remember { mutableStateOf(true) }
    var weekOffset by remember { mutableIntStateOf(0) }
    val originalPeriods = initial?.courses?.flatMap { it.periods }.orEmpty()
    val firstWeek = originalPeriods.minOfOrNull { it.startWeek } ?: 1
    val oldDate = initial?.schedule?.semesterStartDate ?: date
    val dateOffset = if (preserveDates) runCatching { SemesterAdjustment.dateOffset(oldDate, date) }.getOrDefault(0) else 0
    val proposedPeriods = runCatching { SemesterAdjustment.adjust(originalPeriods, oldDate, date, preserveDates, weekOffset) }
    val effectiveWeeks = maxOf(schedule.maxWeeks, proposedPeriods.getOrNull()?.maxOfOrNull { it.endWeek } ?: 1)
    var fixedDuration by remember { mutableStateOf(schedule.fixedLessonMinutes != null) }
    var durationText by remember { mutableStateOf((schedule.fixedLessonMinutes ?: 45).toString()) }
    var addedTimes by remember { mutableStateOf(false) }
    val slots = remember { mutableStateListOf<TimeSlot>().apply { addAll((initial?.timeSlots ?: defaultTimeSlots()).sortedBy { it.section }) } }
    val occupiedEnd = initial?.courses?.flatMap { it.periods }?.maxOfOrNull { it.endSection } ?: 1
    fun updateEnds() {
        val minutes = durationText.toIntOrNull()?.takeIf { it in 1..240 } ?: return
        slots.indices.forEach { index -> slots[index] = slots[index].copy(endTime = runCatching { FixedLessonTime.end(slots[index].startTime, minutes) }.getOrDefault("")) }
    }
    fun resize(count: Int) {
        runCatching {
            val resized = TimeSlotPlanner.resize(slots.toList(), count, occupiedEnd)
            FixedLessonTime.apply(resized, if (fixedDuration) requireNotNull(durationText.toIntOrNull()?.takeIf { it in 1..240 }) { "请填写 1–240 分钟的课时时长" } else null)
        }.onSuccess { result ->
            if (result.size > slots.size) addedTimes = true
            slots.clear(); slots.addAll(result); error = null
        }.onFailure { error = it.message }
    }
    fun save() {
        error = when {
            fixedDuration && durationText.toIntOrNull()?.let { it in 1..240 } != true -> "请填写 1–240 分钟的课时时长"
            schedule.name.isBlank() -> "请填写课表名称"
            runCatching { LocalDate.parse(date) }.isFailure -> "请按 YYYY-MM-DD 填写有效开学日期"
            proposedPeriods.isFailure -> proposedPeriods.exceptionOrNull()?.message
            else -> runCatching {
                var previous = LocalTime.MIN
                slots.forEach { slot ->
                    require(slot.startTime.matches(Regex("\\d{2}:\\d{2}")) && slot.endTime.matches(Regex("\\d{2}:\\d{2}"))) { "第 ${slot.section} 节时间请使用 HH:mm 格式" }
                    val start = LocalTime.parse(slot.startTime)
                    val end = LocalTime.parse(slot.endTime)
                    require(start < end && start >= previous) { "第 ${slot.section} 节时间需前后有序，且不能与上一节重叠" }
                    previous = end
                }
            }.exceptionOrNull()?.let { if (it is java.time.DateTimeException) "请输入有效时间（00:00–23:59）" else it.message }
        }
        if (error == null) onSave(schedule.copy(fixedLessonMinutes = if (fixedDuration) durationText.toIntOrNull() else null, semesterStartDate = date, maxWeeks = effectiveWeeks, updatedAt = System.currentTimeMillis()), slots.toList(), preserveDates, weekOffset)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(if (initial == null) "新建课表" else "课表设置") }, navigationIcon = {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭") }
    }) }, bottomBar = {
        Surface(shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { save() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "保存中…" else "保存课表") }
            }
        }
    }) { inset ->
        Column(Modifier.padding(inset).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsGroup("学期与上课周次", "开学日期是第 1 周的起点，可以早于第一次上课。") {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("开学日期 · $date")
                }
                if (originalPeriods.isNotEmpty()) {
                    if (date != oldDate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("保持课程实际日期")
                                Text(if (preserveDates) "更改开学日期时自动换算周次" else "保持原周次，课程日期随之移动", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(preserveDates, { preserveDates = it; weekOffset = 0 })
                        }
                    }
                    val adjustedFirst = firstWeek + dateOffset + weekOffset
                    Choice("首课从第几周开始", adjustedFirst, (1..60).toList(), { "第 $it 周" }) { target ->
                        weekOffset = target - firstWeek - dateOffset
                    }
                    val last = proposedPeriods.getOrNull()?.maxOfOrNull { it.endWeek }
                    if (last != null) Text(if (adjustedFirst > 1) "前 ${adjustedFirst - 1} 周无课，课程安排在第 $adjustedFirst–$last 周。" else "课程安排在第 1–$last 周。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text("调整首课周次会整体移动已有课程，保留课程间隔与单双周安排。", style = MaterialTheme.typography.bodySmall)
                    proposedPeriods.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Choice("学期周数", schedule.maxWeeks, (1..60).toList()) { schedule = schedule.copy(maxWeeks = it) }
                if (effectiveWeeks > schedule.maxWeeks) Text("为保留全部课程，保存时将扩展至 $effectiveWeeks 周。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(schedule.name, { schedule = schedule.copy(name = it) }, label = { Text("课表名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("每天显示几节课", style = MaterialTheme.typography.titleLarge)
                    Text("${slots.size} 节", style = MaterialTheme.typography.headlineLarge)
                    Text("包含空课时；所有日期共用这份作息。", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 10, 12, 14).forEach { count ->
                            FilterChip(selected = slots.size == count, onClick = { resize(count) }, label = { Text("$count 节") }, enabled = !busy && count >= occupiedEnd)
                        }
                    }
                    Choice("自定义节数", slots.size, (occupiedEnd..30).toList(), { "$it 节" }) { resize(it) }
                    if (occupiedEnd > 1) Text("已有课程到第 $occupiedEnd 节，至少保留 $occupiedEnd 节。", style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsGroup("课表显示", "选择一周的排列方式") {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("显示周末", Modifier.weight(1f)); Switch(schedule.showWeekend, { schedule = schedule.copy(showWeekend = it) }) }
                Choice("每周第一天", schedule.firstDay, listOf(1, 7), { if (it == 1) "周一" else "周日" }) { schedule = schedule.copy(firstDay = it) }
            }
            SettingsGroup("课程配色", "选择方案后，保存时统一更新当前课表的课程颜色。") {
                OutlinedCard(onClick = { schedule = schedule.copy(colorPalette = "custom") }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = schedule.colorPalette == "custom", onClick = { schedule = schedule.copy(colorPalette = "custom") })
                        Text("保留现有颜色")
                    }
                }
                CoursePalettes.all.forEach { palette ->
                    OutlinedCard(onClick = { schedule = schedule.copy(colorPalette = palette.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = schedule.colorPalette == palette.id, onClick = { schedule = schedule.copy(colorPalette = palette.id) })
                                Text(palette.name)
                            }
                            Row(Modifier.padding(start = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                palette.colors.forEach { hex -> Box(Modifier.size(28.dp).background(Color(android.graphics.Color.parseColor(hex)), CircleShape)) }
                            }
                        }
                    }
                }
                Text("也可在编辑课程时单独选色；同一方案下再次保存设置不会覆盖单独选色。", style = MaterialTheme.typography.bodySmall)
            }
            SettingsGroup("作息时间 · ${slots.size} 节", "24 小时制，按顺序填写每节课的开始与结束时间") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("固定每节课时长")
                        Text("仅填写开始时间，自动计算结束时间", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(fixedDuration, { fixedDuration = it; if (it) updateEnds() }, modifier = Modifier.semantics { contentDescription = "固定每节课时长" })
                }
                if (fixedDuration) {
                    OutlinedTextField(durationText, { durationText = it; updateEnds() }, label = { Text("每节课时长（分钟）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("启用或修改时长会重新计算所有结束时间，开始时间与课间安排不变。", style = MaterialTheme.typography.bodySmall)
                }
                if (addedTimes) Text("新增节次已填入建议时间，请按学校作息确认。", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                slots.forEachIndexed { index, slot ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("第 ${slot.section}\n节", Modifier.width(36.dp), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(slot.startTime, { slots[index] = slot.copy(startTime = it, endTime = if (fixedDuration) runCatching { FixedLessonTime.end(it, durationText.toInt()) }.getOrDefault("") else slot.endTime) }, label = { Text("开始") }, modifier = Modifier.weight(1f), singleLine = true)
                        Text("—", color = MaterialTheme.colorScheme.outline)
                        OutlinedTextField(slot.endTime, { slots[index] = slot.copy(endTime = it) }, label = { Text(if (fixedDuration) "自动结束" else "结束") }, readOnly = fixedDuration, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        }
    }
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString(); weekOffset = 0 }
                showDatePicker = false
            }, enabled = picker.selectedDateMillis != null) { Text("确定日期") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }) {
            DatePicker(state = picker, title = { Text("选择开学日期", Modifier.padding(24.dp)) })
        }
    }

}

@Composable
private fun SettingsGroup(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
