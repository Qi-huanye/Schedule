package dev.wakeuppure.ui.timetable

import androidx.compose.foundation.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import dev.wakeuppure.ui.background.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimetableScreen(data: ScheduleData, now: LocalDateTime, onDetail: (CourseWithPeriods) -> Unit,
    onEdit: (CourseWithPeriods) -> Unit, onSettings: () -> Unit) {
    val current = WeekCalculator.week(data.schedule.semesterStartDate, now.toLocalDate())
    val pager = key(data.schedule.id, current, data.schedule.maxWeeks) {
        rememberPagerState(initialPage = current.coerceIn(1, data.schedule.maxWeeks) - 1, pageCount = { data.schedule.maxWeeks })
    }
    val week = pager.currentPage + 1
    val scope = rememberCoroutineScope()
    val allDays = (0..6).map { (data.schedule.firstDay - 1 + it) % 7 + 1 }
    val days = allDays.filter { data.schedule.showWeekend || it <= 5 }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(backgroundChromeColor()).heightIn(min = 64.dp).padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val weekday = "周" + listOf("一", "二", "三", "四", "五", "六", "日")[now.dayOfWeek.value - 1]
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(if (week == current) "第${week}周  $weekday" else "第${week}周 · 非本周",
                    fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text((if (week != current) "今天$weekday · " else "") + now.format(DateTimeFormatter.ofPattern("yyyy/M/d")),
                    fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = week > 1) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一周") }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(data.schedule.maxWeeks - 1)) } }, enabled = week < data.schedule.maxWeeks) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一周") }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(current.coerceIn(1, data.schedule.maxWeeks) - 1) } }) { Icon(Icons.Default.Today, "回到本周") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "课表设置") }
        }
        if (current < 1 || current > data.schedule.maxWeeks) Text(if (current < 1) "尚未开学" else "本学期已结束",
            Modifier.align(Alignment.CenterHorizontally).background(backgroundPanelColor()).padding(bottom = 8.dp), color = MaterialTheme.colorScheme.secondary)
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { page ->
            TimetableWeek(data, now, page + 1, days, onDetail, onEdit)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableWeek(data: ScheduleData, now: LocalDateTime, week: Int, days: List<Int>,
    onDetail: (CourseWithPeriods) -> Unit, onEdit: (CourseWithPeriods) -> Unit) {
    val visible = data.courses.flatMap { course -> course.periods.filter { CourseFilter.matches(it, week) }.map { course to it } }
    val ghosts = TimetableGhosts.select(data.courses, week)
    val lanes = CourseFilter.lanes(visible.map { it.second })
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(backgroundChromeColor()).padding(end = 6.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            val firstDate = WeekCalculator.date(data.schedule.semesterStartDate, week, days.first())
            val lastDate = WeekCalculator.date(data.schedule.semesterStartDate, week, days.last())
            val month = if (firstDate.monthValue == lastDate.monthValue) "${firstDate.monthValue}" else "${firstDate.monthValue}/${lastDate.monthValue}"
            Column(Modifier.width(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(month, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                Text("月", fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            days.forEach { day ->
                val date = WeekCalculator.date(data.schedule.semesterStartDate, week, day)
                val today = date == now.toLocalDate()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(listOf("一", "二", "三", "四", "五", "六", "日")[day-1], fontSize = 11.sp, lineHeight = 15.sp,
                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                        color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(27.dp).background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                        Text("${date.dayOfMonth}", fontSize = 14.sp, lineHeight = 18.sp,
                            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                            color = if (today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val slots = data.timeSlots.sortedBy { it.section }
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
            days.forEach { day ->
                BoxWithConstraints(Modifier.weight(1f).height(height * slots.size)) {
                    (ghosts + visible).filter { it.second.dayOfWeek == day }.forEach { (course, period) ->
                        val ghost = !CourseFilter.matches(period, week)
                        val (lane, total) = if (ghost) 0 to 1 else lanes.getValue(period.id)
                        val width = maxWidth / total
                        val (base, courseInk) = courseDisplayColors(course.course.id, course.course.color)
                        val ink = if (ghost) MaterialTheme.colorScheme.onSurfaceVariant else courseInk
                        val dayDate = WeekCalculator.date(data.schedule.semesterStartDate, week, day)
                        val occurrence = CourseFilter.onDate(data, dayDate).find { it.period.id == period.id }
                        val active = !ghost && occurrence != null && !now.isBefore(occurrence.start) && now.isBefore(occurrence.end)
                        val idx = slots.indexOfFirst { it.section == period.startSection }.coerceAtLeast(0)
                        Box(Modifier.offset(x = width * lane, y = height * idx).width(width)
                            .height(height * (period.endSection - period.startSection + 1)).padding(2.dp)
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
