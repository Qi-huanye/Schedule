@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.timetable

import androidx.compose.foundation.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val week by remember(pager) { derivedStateOf { pager.settledPage + 1 } }
    val scope = rememberCoroutineScope()
    var weekJump by remember(timetable, pager) { mutableStateOf<Job?>(null) }
    fun showWeek(target: Int) {
        weekJump?.cancel()
        weekJump = scope.launch {
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
        Column(Modifier.fillMaxWidth().background(backgroundChromeColor())) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(MaterialTheme.shapes.large).clickable { panel = true }.padding(4.dp)) {
                    Text(data.schedule.name, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第${week}周", style = MaterialTheme.typography.headlineLarge)
                        Icon(Icons.Default.ExpandMore, "选择周次或课表", Modifier.padding(start = 4.dp).size(28.dp))
                    }
                }
                if (week != current) Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text("非本周", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { showWeek((week - 1).coerceAtLeast(1)) }, enabled = week > 1,
                    modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一周") }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { showWeek(current.coerceIn(1, data.schedule.maxWeeks)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Today, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("回到本周")
                }
                FilledTonalIconButton(onClick = { showWeek((week + 1).coerceAtMost(data.schedule.maxWeeks)) },
                    enabled = week < data.schedule.maxWeeks, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一周")
                }
            }
        }
        if (current < 1 || current > data.schedule.maxWeeks) Text(if (current < 1) "尚未开学" else "本学期已结束",
            Modifier.align(Alignment.CenterHorizontally).background(backgroundPanelColor()).padding(bottom = 8.dp), color = MaterialTheme.colorScheme.secondary)
        WeekPager(timetable, pager, now, onDetail, onEdit, Modifier.fillMaxWidth().weight(1f))
    }
    if (panel) WeekSchedulePanel(data, all, week, current, onDismiss = { panel = false },
        onWeek = { target -> panel = false; showWeek(target) },
        onSelect = { panel = false; onSelect(it) }, onNewSchedule = { panel = false; onNewSchedule() }, onImport = { panel = false; onImport() })
}

@Composable
internal fun WeekPager(timetable: PreparedTimetable, pager: PagerState, now: LocalDateTime,
    onDetail: (CourseWithPeriods) -> Unit, onEdit: (CourseWithPeriods) -> Unit, modifier: Modifier = Modifier) {
    HorizontalPager(state = pager, modifier = modifier.testTag("week_pager"), beyondViewportPageCount = 1,
        key = { it }, flingBehavior = PagerDefaults.flingBehavior(pager, snapPositionalThreshold = 0.35f)) { page ->
        val layout by key(timetable, page) {
            produceState(initialValue = timetable.cached(page + 1)) { value = timetable.prepareWeek(page + 1) }
        }
        layout?.let { TimetableWeek(timetable.data, it, now, onDetail, onEdit) }
            ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableWeek(data: ScheduleData, layout: TimetableWeekLayout, now: LocalDateTime,
    onDetail: (CourseWithPeriods) -> Unit, onEdit: (CourseWithPeriods) -> Unit) {
    val today = now.toLocalDate().toEpochDay()
    val second = now.toLocalTime().toSecondOfDay()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Horizontal gestures belong exclusively to the week pager, at every font size.
        val contentWidth = maxWidth
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(contentWidth).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().background(backgroundChromeColor()).padding(end = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            Box(Modifier.size(32.dp).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                Text("${day.dayOfMonth}", fontSize = 14.sp, lineHeight = 18.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                val slots = layout.slots
                val height = (68 * fontScale.coerceAtLeast(1f)).dp
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
                                val gradient = if (ghost) null else courseDisplayGradient(course.course.id, course.course.color)
                                val active = !ghost && day.epochDay == today && card.startSecond != null && card.endSecond != null &&
                                    second >= card.startSecond && second < card.endSecond
                                Box(Modifier.offset(x = width * card.lane, y = height * card.sectionIndex).width(width)
                                    .height(height * card.sectionSpan).padding(2.dp)
                                    .then(if (ghost) Modifier.background(ghostCourseColor(), RoundedCornerShape(16.dp))
                                        else Modifier.background(requireNotNull(gradient), RoundedCornerShape(16.dp)))
                                    .then(when {
                                        active -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                        ghost && LocalBackgroundActive.current -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        else -> Modifier
                                    })
                                    .clip(RoundedCornerShape(16.dp))
                                    .semantics { contentDescription = "${course.course.name}，${period.startSection}至${period.endSection}节" +
                                        (if (ghost) "，非本周" else if (active) "，进行中" else "") }
                                    .combinedClickable(onClick = { onDetail(course) }, onLongClick = { onEdit(course) }).padding(8.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (active && card.sectionSpan >= 2) Text("进行中", fontSize = 9.sp, lineHeight = 11.sp, color = ink, fontWeight = FontWeight.Bold)
                                            if (ghost) Text("非本周", fontSize = 9.sp, lineHeight = 11.sp, color = ink)
                                        }
                                        Text(course.course.name, fontSize = 13.sp, lineHeight = 16.sp, color = ink,
                                            fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                        val room = period.classroom.ifBlank { course.course.classroom }
                                        val details = listOf(course.course.teacher, room).filter { it.isNotBlank() }.joinToString(" @ ")
                                        if (details.isNotBlank()) Text(details, fontSize = 10.sp, lineHeight = 13.sp, color = ink.copy(alpha = .82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
