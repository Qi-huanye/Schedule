package dev.wakeuppure.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(data: ScheduleData, now: LocalDateTime, onCourse: (CourseWithPeriods) -> Unit) {
    val today = CourseFilter.onDate(data, now.toLocalDate())
    val next = CourseFilter.next(data, now)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("今天 · 周${listOf("一","二","三","四","五","六","日")[now.dayOfWeek.value-1]}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(now.format(DateTimeFormatter.ofPattern("M月d日")), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (next != null) {
                val minutes = Duration.between(now, next.start).toMinutes().coerceAtLeast(0)
                Text(if (minutes < 1440) "下一节课还有 $minutes 分钟" else "下一节课 · ${next.date} ${next.start.toLocalTime()}", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (today.isEmpty()) item { Text("今天没有课程", Modifier.padding(vertical = 40.dp), style = MaterialTheme.typography.titleLarge) }
        items(today, key = { it.period.id }) { occurrence ->
            ListItem(headlineContent = { Text(occurrence.course.name, fontWeight = FontWeight.SemiBold) },
                overlineContent = { Text("${occurrence.start.toLocalTime()} - ${occurrence.end.toLocalTime()}") },
                supportingContent = { Text(listOf(occurrence.classroom, occurrence.course.teacher).filter { it.isNotBlank() }.joinToString(" · ")) },
                trailingContent = { if (!now.isBefore(occurrence.start) && now.isBefore(occurrence.end)) Text("进行中", color = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onCourse(data.courses.first { it.course.id == occurrence.course.id }) })
            HorizontalDivider()
        }
    }
}
