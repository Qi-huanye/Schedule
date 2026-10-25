@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.wakeuppure.ui.timetable

import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.material.icons.filled.Check
import dev.wakeuppure.ui.todo.TodoListSheet
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
import androidx.compose.ui.zIndex
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
    onEdit: (CourseWithPeriods) -> Unit, onTodo: (Todo) -> Unit, onSelect: (Long) -> Unit, onNewSchedule: () -> Unit, onImport: () -> Unit,
    prefetchEnabled: Boolean = true, showNonCurrentWeekCourses: Boolean = true, onScrolled: (Boolean) -> Unit = {}) {
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
            // Prefetch data without composing both neighbouring pages before the first frame.
            timetable.prepareAround(page + 1, radius = 2)
        }
    }
    var panel by remember { mutableStateOf(false) }
    var showOverdue by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var collapsed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val revealThresholdPx = with(density) { 56.dp.toPx() }
    var pullToReveal by remember { mutableFloatStateOf(0f) }
    val headerScrollConnection = remember(scrollState, revealThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                val down = available.y > 0f && source == NestedScrollSource.UserInput
                if (!collapsed || scrollState.value != 0 || !down) {
                    pullToReveal = 0f
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                pullToReveal += available.y
                if (pullToReveal >= revealThresholdPx) {
                    collapsed = false
                    pullToReveal = 0f
                }
                return androidx.compose.ui.geometry.Offset(x = 0f, y = available.y)
            }
        }
    }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { offset ->
            if (!collapsed && offset > 72) collapsed = true
        }
    }
    LaunchedEffect(collapsed) { onScrolled(collapsed) }
    val overdue = data.todos.filter { !it.completed && it.dueAt < now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.sortedBy { it.dueAt }
    Column(Modifier.fillMaxSize()) {
        TimetableHeader(data.schedule.name, week, current, data.schedule.maxWeeks, overdue.size, collapsed,
            onOverdue = { showOverdue = true }, onPanel = { panel = true }, onWeek = ::showWeek)
        if (current < 1 || current > data.schedule.maxWeeks) Text(if (current < 1) "尚未开学" else "本学期已结束",
            Modifier.align(Alignment.CenterHorizontally).background(backgroundPanelColor()).padding(bottom = 8.dp), color = MaterialTheme.colorScheme.secondary)
        WeekPager(timetable, pager, now, onDetail, onEdit, Modifier.fillMaxWidth().weight(1f), onTodo,
            showNonCurrentWeekCourses, scrollState, headerScrollConnection)
    }
    if (showOverdue) TodoListSheet("逾期待办", overdue, { showOverdue = false }, onTodo)
    if (panel) WeekSchedulePanel(data, all, week, current, onDismiss = { panel = false },
        onWeek = { target -> panel = false; showWeek(target) },
        onSelect = { panel = false; onSelect(it) }, onNewSchedule = { panel = false; onNewSchedule() }, onImport = { panel = false; onImport() })
}

@Composable
internal fun WeekPager(timetable: PreparedTimetable, pager: PagerState, now: LocalDateTime,
    onDetail: (CourseWithPeriods) -> Unit, onEdit: (CourseWithPeriods) -> Unit, modifier: Modifier = Modifier, onTodo: (Todo) -> Unit = {},
    showNonCurrentWeekCourses: Boolean = true, scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    headerScrollConnection: NestedScrollConnection = remember { object : NestedScrollConnection {} }) {
    HorizontalPager(state = pager, modifier = modifier.testTag("week_pager"), beyondViewportPageCount = 0,
        key = { it }, flingBehavior = PagerDefaults.flingBehavior(pager, snapPositionalThreshold = 0.35f)) { page ->
        val layout by key(timetable, page) {
            produceState(initialValue = timetable.cached(page + 1)) { value = timetable.prepareWeek(page + 1) }
        }
        layout?.let { TimetableWeek(timetable.data, it, now, onDetail, onEdit, onTodo,
            showNonCurrentWeekCourses, scrollState, headerScrollConnection) }
            ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableWeek(data: ScheduleData, layout: TimetableWeekLayout, now: LocalDateTime,
    onDetail: (CourseWithPeriods) -> Unit, onEdit: (CourseWithPeriods) -> Unit, onTodo: (Todo) -> Unit,
    showNonCurrentWeekCourses: Boolean, scrollState: androidx.compose.foundation.ScrollState,
    headerScrollConnection: NestedScrollConnection) {
    val today = now.toLocalDate().toEpochDay()
    val second = now.toLocalTime().toSecondOfDay()
    val fontScale = LocalDensity.current.fontScale
    var selectedGroup by remember { mutableStateOf<List<Todo>?>(null) }
    selectedGroup?.let { todos -> TodoListSheet("待办", todos, { selectedGroup = null }, onTodo) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Horizontal gestures belong exclusively to the week pager, at every font size.
        val contentWidth = maxWidth
        val dayWidth = ((contentWidth - 48.dp) / layout.days.size).coerceAtLeast(0.dp)
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(contentWidth).fillMaxHeight()) {
                TimetableDateHeader(layout, today)
                val slots = layout.slots
                val height = (68 * fontScale.coerceAtLeast(1f)).dp
                Row(Modifier.fillMaxWidth().weight(1f).nestedScroll(headerScrollConnection).verticalScroll(scrollState)
                    .padding(bottom = 80.dp, end = 6.dp)) {
                    Column(Modifier.width(42.dp)) { slots.forEach { slot ->
                        Column(Modifier.height(height).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(slot.section.toString(), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                            Text(slot.startTime, fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(slot.endTime, fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } }
                    layout.days.forEach { day ->
                        TimetableDay(data, day, slots, today, second, now, height, dayWidth,
                            showNonCurrentWeekCourses, Modifier.weight(1f), onDetail, onEdit, onTodo,
                            onTodoGroup = { selectedGroup = it })
                    }
                }
            }
        }
    }
}

/** Keep each card independently skippable instead of one large subcomposed day lambda. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableCourseCard(course: CourseWithPeriods, period: CoursePeriod, card: TimetableCardLayout,
    active: Boolean, modifier: Modifier, onDetail: (CourseWithPeriods) -> Unit, onEdit: (CourseWithPeriods) -> Unit) {
    val ghost = card.ghost
    val ink = if (ghost) MaterialTheme.colorScheme.onSurfaceVariant else courseDisplayColors(course.course.id, course.course.color).second
    val fill = if (ghost) Modifier.background(ghostCourseColor(), RoundedCornerShape(16.dp))
        else Modifier.background(courseDisplayGradient(course.course.id, course.course.color), RoundedCornerShape(16.dp))
    val details = remember(course.course, period) {
        val room = period.classroom.ifBlank { course.course.classroom }
        listOf(period.teacher.ifBlank { course.course.teacher }, room).filter { it.isNotBlank() }.joinToString(" @ ")
    }
    BoxWithConstraints(modifier.padding(2.dp).then(fill).then(when {
        active -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        ghost && LocalBackgroundActive.current -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        else -> Modifier
    }).clip(RoundedCornerShape(16.dp))
        .semantics { contentDescription = "${course.course.name}，${period.startSection}至${period.endSection}节" +
            (if (ghost) "，非本周" else if (active) "，进行中" else "") }
        .combinedClickable(onClick = { onDetail(course) }, onLongClick = { onEdit(course) })
        .padding(start = 4.dp, end = 4.dp, top = 7.dp, bottom = 4.dp)) {
        val cardFont = (maxWidth.value * .24f).coerceIn(9f, 12f).sp
        val detailFont = (cardFont.value * .72f).coerceIn(8f, 9.5f).sp
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (active && card.sectionSpan >= 2) Text("进行中", fontSize = 9.sp, lineHeight = 11.sp, color = ink, fontWeight = FontWeight.Bold)
                if (ghost) Text("非本周", fontSize = 9.sp, lineHeight = 11.sp, color = ink)
            }
            Text(course.course.name, fontSize = cardFont, lineHeight = (cardFont.value * 1.2f).sp, color = ink,
                fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Clip)
            if (details.isNotBlank()) Text(details, fontSize = detailFont, lineHeight = (detailFont.value * 1.25f).sp,
                color = ink.copy(alpha = .82f), maxLines = 3, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun TimetableDay(data: ScheduleData, day: TimetableDayLayout, slots: List<TimeSlot>,
    today: Long, second: Int, now: LocalDateTime, height: androidx.compose.ui.unit.Dp, dayWidth: androidx.compose.ui.unit.Dp,
    showNonCurrentWeekCourses: Boolean, modifier: Modifier, onDetail: (CourseWithPeriods) -> Unit,
    onEdit: (CourseWithPeriods) -> Unit, onTodo: (Todo) -> Unit, onTodoGroup: (List<Todo>) -> Unit) {
    Box(modifier.height(height * slots.size)) {
        day.cards.forEach { card ->
            if (card.ghost && !showNonCurrentWeekCourses) return@forEach
            val course = data.courses[card.courseIndex]
            val period = course.periods[card.periodIndex]
            val width = dayWidth / card.laneCount
            val active = !card.ghost && day.epochDay == today && card.startSecond != null && card.endSecond != null &&
                second >= card.startSecond && second < card.endSecond
            key(card.courseIndex, card.periodIndex, card.ghost) {
                TimetableCourseCard(course, period, card, active,
                    Modifier.offset(x = width * card.lane, y = height * card.sectionIndex).width(width)
                        .height(height * card.sectionSpan), onDetail, onEdit)
            }
        }
        TodoSchedule.markers(data.todos, day.epochDay, slots).forEach { marker ->
            val todo = marker.todos.first()
            val due = Instant.ofEpochMilli(todo.dueAt).atZone(ZoneId.systemDefault()).toLocalTime()
            val overdue = !todo.completed && todo.dueAt < now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val color = when {
                marker.todos.all { it.completed } -> MaterialTheme.colorScheme.outline
                overdue -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.tertiary
            }
            Box(Modifier.offset(y = height * marker.position).zIndex(3f).fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(8.dp)).clickable { if (marker.todos.size == 1) onTodo(todo) else onTodoGroup(marker.todos) }
                .semantics { contentDescription = marker.todos.joinToString("，") { it.title } + "，${due} 截止" }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = color, contentColor = MaterialTheme.colorScheme.surface) {
                        Text((if (todo.completed) "✓ " else "") + due.toString().take(5) + if (marker.todos.size > 1) " +${marker.todos.size - 1}" else "",
                            Modifier.padding(horizontal = 4.dp, vertical = 3.dp), fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(color, RoundedCornerShape(2.dp)))
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)) {
                        Text(todo.title, Modifier.padding(horizontal = 3.dp, vertical = 2.dp), fontSize = 9.sp, lineHeight = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableDateHeader(layout: TimetableWeekLayout, today: Long) {
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
}

@Composable
private fun TimetableHeader(name: String, week: Int, current: Int, maxWeeks: Int, overdueCount: Int, collapsed: Boolean,
    onOverdue: () -> Unit, onPanel: () -> Unit, onWeek: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(backgroundChromeColor()).animateContentSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(4.dp).clip(MaterialTheme.shapes.large).clickable { onPanel() }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(name, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AnimatedContent(collapsed, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "weekHeader") { compact ->
                    if (compact) Text("第${week}周", style = MaterialTheme.typography.titleMedium)
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第${week}周", style = MaterialTheme.typography.headlineLarge)
                        Icon(Icons.Default.ExpandMore, "选择周次或课表", Modifier.padding(start = 4.dp).size(28.dp))
                    }
                }
            }
            if (overdueCount > 0) TextButton(onClick = { onOverdue() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("逾期 ${overdueCount}") }
            if (week != current) Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Text("非本周", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        AnimatedVisibility(!collapsed, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onWeek((week - 1).coerceAtLeast(1)) }, enabled = week > 1,
                modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一周") }
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { onWeek(current.coerceIn(1, maxWeeks)) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Today, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("回到本周")
            }
            FilledTonalIconButton(onClick = { onWeek((week + 1).coerceAtMost(maxWeeks)) },
                enabled = week < maxWeeks, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一周")
            }
        }
        }
    }
}
