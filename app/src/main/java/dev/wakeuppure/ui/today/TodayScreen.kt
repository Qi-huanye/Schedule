package dev.wakeuppure.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import dev.wakeuppure.ui.background.*
import dev.wakeuppure.ui.components.WEEKDAYS
import java.time.*
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(data: ScheduleData, now: LocalDateTime, onCourse: (CourseWithPeriods) -> Unit) {
    val date = now.toLocalDate()
    val today = CourseFilter.onDate(data, date)
    val next = CourseFilter.next(data, now)
    val week = WeekCalculator.week(data.schedule.semesterStartDate, date)
    fun open(o: Occurrence) { data.courses.firstOrNull { it.course.id == o.course.id }?.let(onCourse) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(backgroundPanelColor(), RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text("${date.monthValue}月${date.dayOfMonth}日 周${WEEKDAYS[date.dayOfWeek.value - 1]}",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(when {
                    week < 1 -> "尚未开学"
                    week > data.schedule.maxWeeks -> "本学期已结束"
                    else -> "第 $week 周"
                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (today.isEmpty()) item {
            Text("今天没有课程", Modifier.fillMaxWidth().background(backgroundPanelColor(), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 24.dp), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(today, key = { it.period.id }) { o ->
            val chip = when {
                !now.isBefore(o.end) -> null
                !now.isBefore(o.start) -> "进行中"
                o == next -> relativeStart(now, o.start)
                else -> null
            }
            OccurrenceCard(o, "${o.start.toLocalTime()} – ${o.end.toLocalTime()}", chip, past = !now.isBefore(o.end)) { open(o) }
        }
        if (next != null && next.date != date) item {
            OccurrenceCard(next, dayLabel(date, next.start), null, past = false, upcoming = true) { open(next) }
        }
    }
}

@Composable
private fun OccurrenceCard(o: Occurrence, time: String, chip: String?, past: Boolean, upcoming: Boolean = false, onClick: () -> Unit) {
    val (base, ink) = if (upcoming) backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow) to MaterialTheme.colorScheme.onSurface
        else courseDisplayColors(o.course.id, o.course.color)
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = base, contentColor = ink,
        modifier = Modifier.fillMaxWidth().alpha(if (past || upcoming) 0.6f else 1f)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.bodySmall)
                Text(o.course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val detail = listOf(o.classroom, o.course.teacher).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            if (chip != null) Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent, contentColor = ink,
                border = BorderStroke(1.dp, ink.copy(alpha = 0.3f))) {
                Text(chip, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** "20 分钟后" under an hour, otherwise whole hours. */
internal fun relativeStart(now: LocalDateTime, start: LocalDateTime): String {
    val minutes = Duration.between(now, start).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "即将开始"
        minutes < 60 -> "$minutes 分钟后"
        else -> "${(minutes + 30) / 60} 小时后"
    }
}

/** "明天 08:00", "周日 10:00" within a week, otherwise "10月20日 08:00". */
internal fun dayLabel(today: LocalDate, start: LocalDateTime): String {
    val days = ChronoUnit.DAYS.between(today, start.toLocalDate())
    val time = start.toLocalTime()
    return when {
        days == 1L -> "明天 $time"
        days in 2..6 -> "周${WEEKDAYS[start.dayOfWeek.value - 1]} $time"
        else -> "${start.monthValue}月${start.dayOfMonth}日 $time"
    }
}
