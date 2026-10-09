package dev.wakeuppure.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import dev.wakeuppure.ui.background.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(data: ScheduleData, now: LocalDateTime, onCourse: (CourseWithPeriods) -> Unit) {
    val today = CourseFilter.onDate(data, now.toLocalDate())
    val next = CourseFilter.next(data, now)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(backgroundPanelColor(), RoundedCornerShape(16.dp))
                .padding(if (LocalBackgroundActive.current) 16.dp else 0.dp)) {
                Text("今天 · 周${listOf("一","二","三","四","五","六","日")[now.dayOfWeek.value-1]}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(now.format(DateTimeFormatter.ofPattern("M月d日")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (next != null) {
                    val minutes = Duration.between(now, next.start).toMinutes().coerceAtLeast(0)
                    Text(if (minutes < 1440) "下一节课还有 $minutes 分钟" else "下一节课 · ${next.date} ${next.start.toLocalTime()}", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (today.isEmpty()) item { Text("今天没有课程", Modifier.fillMaxWidth()
            .background(backgroundPanelColor(), RoundedCornerShape(16.dp)).padding(vertical = 40.dp, horizontal = 16.dp), style = MaterialTheme.typography.titleLarge) }
        items(today, key = { it.period.id }) { occurrence ->
            val courseColors = if (LocalCourseColors.current != null) courseDisplayColors(occurrence.course.id, occurrence.course.color) else null
            val colors = courseColors?.let { (base, ink) -> ListItemDefaults.colors(containerColor = base, headlineColor = ink,
                overlineColor = ink, supportingColor = ink, trailingIconColor = ink) }
                ?: ListItemDefaults.colors(containerColor = backgroundPanelColor(MaterialTheme.colorScheme.surface))
            ListItem(headlineContent = { Text(occurrence.course.name, fontWeight = FontWeight.SemiBold) },
                overlineContent = { Text("${occurrence.start.toLocalTime()} - ${occurrence.end.toLocalTime()}") },
                supportingContent = { Text(listOf(occurrence.classroom, occurrence.course.teacher).filter { it.isNotBlank() }.joinToString(" · ")) },
                trailingContent = { if (!now.isBefore(occurrence.start) && now.isBefore(occurrence.end)) Text("进行中", color = courseColors?.second ?: MaterialTheme.colorScheme.primary) },
                colors = colors,
                // Over a photo each course is its own rounded card, matching the summary panel above.
                modifier = Modifier.then(if (LocalBackgroundActive.current) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
                    .clickable { onCourse(data.courses.first { it.course.id == occurrence.course.id }) })
            if (!LocalBackgroundActive.current) HorizontalDivider()
        }
    }
}
