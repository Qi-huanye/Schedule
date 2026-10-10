package dev.wakeuppure.ui.timetable

import androidx.compose.foundation.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.DragInteraction
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import dev.wakeuppure.data.local.PreparedTimetable
import dev.wakeuppure.ui.background.*
import java.time.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimetableScreen(timetable: PreparedTimetable, all: List<ScheduleData>, now: LocalDateTime, onDetail: (CourseWithPeriods) -> Unit,
    onEdit: (CourseWithPeriods) -> Unit, onSelect: (Long) -> Unit, onNewSchedule: () -> Unit, onImport: () -> Unit,
    prefetchEnabled: Boolean = true) {
    val data = timetable.data
    val current = WeekCalculator.week(data.schedule.semesterStartDate, now.toLocalDate())
    val pager = key(data.schedule.id, current, data.schedule.maxWeeks) {
        rememberPagerState(initialPage = current.coerceIn(1, data.schedule.maxWeeks) - 1, pageCount = { data.schedule.maxWeeks })
    }
    val week = pager.currentPage + 1
    val scope = rememberCoroutineScope()
    var weekJump by remember(timetable, pager) { mutableStateOf<Job?>(null) }
    fun showWeek(target: Int) {
        weekJump?.cancel()
        weekJump = scope.launch {
            timetable.prepareAround(target)
            pager.animateScrollToPage(target - 1)
        }
    }
    DisposableEffect(timetable, pager) { onDispose { weekJump?.cancel() } }
    LaunchedEffect(timetable, pager) {
        pager.interactionSource.interactions.collect { interaction ->
            // A fresh gesture takes priority over a button request still waiting for disk.
            if (interaction is DragInteraction.Start) weekJump?.cancel()
        }
    }
    LaunchedEffect(timetable, pager, prefetchEnabled) {
        if (prefetchEnabled) snapshotFlow { pager.targetPage }.distinctUntilChanged().collectLatest { page ->
            // Only data is prefetched further ahead; the pager still composes exactly one neighbour.
            timetable.prepareAround(page + 1, radius = 2)
        }
    }
    var panel by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(backgroundChromeColor()).heightIn(min = 64.dp).padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { panel = true }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (week == current) "第${week}周" else "第${week}周 · 非本周",
                        fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Icon(Icons.Default.ExpandMore, "选择周次或课表", Modifier.size(20.dp))
                }
                Text(data.schedule.name, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { showWeek((week - 1).coerceAtLeast(1)) }, enabled = week > 1) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一周") }
            IconButton(onClick = { showWeek((week + 1).coerceAtMost(data.schedule.maxWeeks)) }, enabled = week < data.schedule.maxWeeks) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一周") }
            IconButton(onClick = { showWeek(current.coerceIn(1, data.schedule.maxWeeks)) }) { Icon(Icons.Default.Today, "回到本周") }
        }
        if (current < 1 || current > data.schedule.maxWeeks) Text(if (current < 1) "尚未开学" else "本学期已结束",
            Modifier.align(Alignment.CenterHorizontally).background(backgroundPanelColor()).padding(bottom = 8.dp), color = MaterialTheme.colorScheme.secondary)
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { page ->
            val layout = remember(timetable, page) { timetable.week(page + 1) }
            TimetableWeek(data, layout, now, onDetail, onEdit)
        }
    }
    if (panel) WeekSchedulePanel(data, all, week, current, onDismiss = { panel = false },
        onWeek = { target -> panel = false; showWeek(target) },
        onSelect = { panel = false; onSelect(it) }, onNewSchedule = { panel = false; onNewSchedule() }, onImport = { panel = false; onImport() })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableWeek(data: ScheduleData, layout: TimetableWeekLayout, now: LocalDateTime,
    onDetail: (CourseWithPeriods) -> Unit, onEdit: (CourseWithPeriods) -> Unit) {
    val today = now.toLocalDate().toEpochDay()
    val second = now.toLocalTime().toSecondOfDay()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(backgroundChromeColor()).padding(end = 6.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(layout.month, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                Text("月", fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            layout.days.forEach { day ->
                val isToday = day.epochDay == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(listOf("一", "二", "三", "四", "五", "六", "日")[day.dayOfWeek-1], fontSize = 11.sp, lineHeight = 15.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(27.dp).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                        Text("${day.dayOfMonth}", fontSize = 14.sp, lineHeight = 18.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val slots = layout.slots
        val height = 68.dp
        Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp, end = 6.dp)) {
            Column(Modifier.width(42.dp)) { slots.forEach { slot ->
                Column(Modifier.height(height).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(slot.section.toString(), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                    Text(slot.startTime, fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(slot.endTime, fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            layout.days.forEach { day ->
                BoxWithConstraints(Modifier.weight(1f).height(height * slots.size)) {
                    day.cards.forEach { card ->
                        val course = data.courses[card.courseIndex]
                        val period = course.periods[card.periodIndex]
                        val ghost = card.ghost
                        val width = maxWidth / card.laneCount
                        val (base, courseInk) = courseDisplayColors(course.course.id, course.course.color)
                        val ink = if (ghost) MaterialTheme.colorScheme.onSurfaceVariant else courseInk
                        val active = !ghost && day.epochDay == today && card.startSecond != null && card.endSecond != null &&
                            second >= card.startSecond && second < card.endSecond
                        Box(Modifier.offset(x = width * card.lane, y = height * card.sectionIndex).width(width)
                            .height(height * card.sectionSpan).padding(2.dp)
                            .background(if (ghost) ghostCourseColor() else base, RoundedCornerShape(5.dp))
                            .then(when {
                                active -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                                ghost && LocalBackgroundActive.current -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
                                else -> Modifier
                            })
                            .combinedClickable(onClick = { onDetail(course) }, onLongClick = { onEdit(course) }).padding(4.dp)) {
                            Column {
                                if (ghost) Text("非本周", fontSize = 9.sp, lineHeight = 11.sp, color = ink)
                                Text(course.course.name, fontSize = 12.sp, lineHeight = 15.sp, color = ink,
                                    fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                val room = period.classroom.ifBlank { course.course.classroom }
                                if (room.isNotBlank()) Text(room, fontSize = 10.sp, lineHeight = 13.sp, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
