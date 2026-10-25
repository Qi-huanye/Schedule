@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Schedule
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.FixedLessonTime
import dev.wakeuppure.domain.usecase.TimeSlotPlanner
import dev.wakeuppure.ui.background.backgroundScreenColor
import dev.wakeuppure.ui.components.*
import java.time.DateTimeException
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesScreen(data: ScheduleData, busy: Boolean, onClose: () -> Unit, onSave: (Schedule, List<TimeSlot>) -> Unit) {
    val slots = remember { mutableStateListOf<TimeSlot>().apply { addAll(data.timeSlots.sortedBy { it.section }) } }
    var fixed by remember { mutableStateOf(data.schedule.fixedLessonMinutes != null) }
    var minutes by remember { mutableIntStateOf(data.schedule.fixedLessonMinutes ?: 45) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val occupiedEnd = data.courses.flatMap { it.periods }.maxOfOrNull { it.endSection } ?: 1
    fun end(start: String) = runCatching { FixedLessonTime.end(start, minutes) }.getOrDefault("")
    fun applyFixed() { if (fixed) slots.indices.forEach { slots[it] = slots[it].copy(endTime = end(slots[it].startTime)) } }
    fun resize(count: Int) {
        runCatching { FixedLessonTime.apply(TimeSlotPlanner.resize(slots.toList(), count, occupiedEnd), if (fixed) minutes else null) }
            .onSuccess { slots.clear(); slots.addAll(it); error = null }.onFailure { error = it.message }
    }
    fun save() {
        error = runCatching {
            var previous = LocalTime.MIN
            slots.forEach { slot ->
                val start = LocalTime.parse(slot.startTime)
                val end = LocalTime.parse(slot.endTime)
                require(start < end && start >= previous) { "第 ${slot.section} 节时间需前后有序，且不能与上一节重叠" }
                previous = end
            }
        }.exceptionOrNull()?.let { if (it is DateTimeException) "存在无效时间，请检查跨天的课程" else it.message }
        if (error == null) onSave(data.schedule.copy(fixedLessonMinutes = if (fixed) minutes else null, updatedAt = System.currentTimeMillis()), slots.toList())
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = backgroundScreenColor(), topBar = { LargeTopAppBar(title = { Text("作息时间") }, scrollBehavior = scrollBehavior,
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = { Button(shapes = ButtonDefaults.shapes(), onClick = ::save, enabled = !busy, modifier = Modifier.padding(end = 12.dp)) { Text("保存") } }) }) { inset ->
        Column(Modifier.padding(inset).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection("课程节奏") {
            StepperRow("每天节数", "${slots.size}", { resize(slots.size - 1) }, { resize(slots.size + 1) }, slots.size > occupiedEnd, slots.size < 30)
            SwitchRow("固定课时", fixed, { fixed = it; applyFixed() })
            if (fixed) StepperRow("每节时长", "$minutes 分", { minutes = (minutes - 5).coerceAtLeast(5); applyFixed() },
                { minutes = (minutes + 5).coerceAtMost(240); applyFixed() }, minutes > 5, minutes < 240)
            error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error) }
            }
            GroupTitle("每日时间表")
            slots.forEachIndexed { index, slot ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${slot.section}", Modifier.width(28.dp), fontWeight = FontWeight.SemiBold)
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { picking = index to true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(slot.startTime) }
                    Text("–", color = MaterialTheme.colorScheme.outline)
                    if (fixed) Text(slot.endTime, Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { picking = index to false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(slot.endTime) }
                }
            }
        }
    }
    picking?.let { (index, isStart) ->
        val slot = slots[index]
        LessonTimeDialog(if (isStart) slot.startTime else slot.endTime,
            "第 ${slot.section} 节${if (isStart) "开始" else "结束"}时间", { picking = null }) { time ->
            slots[index] = if (isStart) slot.copy(startTime = time, endTime = if (fixed) end(time) else slot.endTime) else slot.copy(endTime = time)
            picking = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonTimeDialog(value: String, fieldLabel: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val initial = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(8, 0))
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    val density = LocalDensity.current
    val height = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = height < 560.dp || density.fontScale > 1.3f
    var inputMode by rememberSaveable(compact) { mutableStateOf(compact) }
    TimePickerDialog(onDismissRequest = onDismiss,
        title = { Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (inputMode) "输入时间" else "选择时间", style = MaterialTheme.typography.labelMedium)
            Text(fieldLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        modeToggleButton = { IconButton(onClick = { inputMode = !inputMode }) {
            Icon(if (inputMode) Icons.Outlined.Schedule else Icons.Outlined.Keyboard,
                if (inputMode) "切换为时钟选择" else "切换为键盘输入")
        } },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) {
        Column(Modifier.heightIn(max = (height - 220.dp).coerceAtLeast(160.dp)).verticalScroll(rememberScrollState())) {
            if (inputMode) TimeInput(state) else TimePicker(state)
        }
    }
}
