package dev.wakeuppure.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import dev.wakeuppure.ui.background.*
import dev.wakeuppure.ui.components.*
import java.time.*
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(data: ScheduleData, now: LocalDateTime, onCourse: (CourseWithPeriods) -> Unit, onToggleTodo: (Todo) -> Unit = {}, onTodo: (Todo) -> Unit = {}) {
    val date = now.toLocalDate()
    val today = remember(data, date) { CourseFilter.onDate(data, date) }
    val next = remember(data, now) { CourseFilter.next(data, now) }
    val active = today.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    val focus = active ?: next
    val completed = today.count { !now.isBefore(it.end) }
    val todos = TodoSchedule.agenda(data.todos, date)
    val agenda = (today.map { Triple(it.start, it, null as Todo?) } + todos.map { todo -> Triple(Instant.ofEpochMilli(todo.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime(), null, todo) }).sortedBy { it.first }
    val week = WeekCalculator.week(data.schedule.semesterStartDate, date)
    fun open(o: Occurrence) { data.courses.firstOrNull { it.course.id == o.course.id }?.let(onCourse) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PageHeading("今日", "${date.monthValue}月${date.dayOfMonth}日，周${WEEKDAYS[date.dayOfWeek.value - 1]}，" + when {
                week < 1 -> "尚未开学"
                week > data.schedule.maxWeeks -> "本学期已结束"
                else -> "第 $week 周"
            })
        }
        item {
            if (focus != null) Surface(onClick = { open(focus) },
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 12.dp),
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (active != null) "正在上课" else "下一节课", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "查看课程", Modifier.size(24.dp))
                    }
                    Text(focus.course.name, style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(if (active != null) "${focus.start.toLocalTime()} – ${focus.end.toLocalTime()}，进行中"
                        else if (focus.date == date) "${focus.start.toLocalTime()}，${relativeStart(now, focus.start)}"
                        else dayLabel(date, focus.start), style = MaterialTheme.typography.titleMedium)
                    if (focus.classroom.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, null, Modifier.size(20.dp))
                        Text(focus.classroom, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (active != null) LinearProgressIndicator(progress = {
                        (Duration.between(active.start, now).seconds.toFloat() / Duration.between(active.start, active.end).seconds.coerceAtLeast(1)).coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                }
            } else FocusPanel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                ScheduleMark(Modifier.size(48.dp))
                Text("留一点时间，\n给课表之外。", style = MaterialTheme.typography.headlineMedium)
                Text("暂无待上的课程", style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("今日安排", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Text(if (today.isEmpty() && todos.isEmpty()) "自由安排" else listOfNotNull("${completed} / ${today.size} 已结束".takeIf { today.isNotEmpty() }, "${todos.count { it.completed }} / ${todos.size} 待办已完成".takeIf { todos.isNotEmpty() }).joinToString("，"),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (today.isEmpty() && todos.isEmpty()) item {
            Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
                color = backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("今天没有课程", style = MaterialTheme.typography.titleMedium)
                    Text("休息、自习，或做点喜欢的事。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(agenda, key = { _, row -> row.third?.let { "todo-${it.id}" } ?: "course-${row.second!!.period.id}" }) { index, row ->
            val occurrence = row.second
            val todo = row.third
            if (occurrence != null) {
                val past = !now.isBefore(occurrence.end)
                val chip = when {
                    past -> "已结束"
                    !now.isBefore(occurrence.start) -> "进行中"
                    occurrence == next -> relativeStart(now, occurrence.start)
                    else -> null
                }
                OccurrenceCard(occurrence, index + 1, chip, past) { open(occurrence) }
            } else if (todo != null) {
                TodoCard(todo, now, data.courses.firstOrNull { it.course.id == todo.courseId }?.course?.name, onToggle = { onToggleTodo(todo) }, onClick = { onTodo(todo) })
            }
        }
    }
}


@Composable
private fun TodoCard(todo: Todo, now: LocalDateTime, courseName: String?, onToggle: () -> Unit, onClick: () -> Unit) {
    val due = Instant.ofEpochMilli(todo.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val overdue = !todo.completed && due.isBefore(now)
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = onToggle) { Icon(if (todo.completed) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                if (todo.completed) "已完成" else "标记完成", tint = if (todo.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(todo.title, style = MaterialTheme.typography.titleMedium, color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text("截止 " + (if (due.toLocalDate() == now.toLocalDate()) due.toLocalTime().toString() else due.format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm"))) + (courseName?.let { "，$it" } ?: "") + (if (overdue) "，已逾期" else ""),
                    style = MaterialTheme.typography.bodyMedium, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OccurrenceCard(o: Occurrence, index: Int, chip: String?, past: Boolean, onClick: () -> Unit) {
    val (base, ink) = if (past) backgroundPanelColor(MaterialTheme.colorScheme.surfaceContainerLow) to MaterialTheme.colorScheme.onSurface
        else courseDisplayColors(o.course.id, o.course.color)
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = base, contentColor = ink,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = if (past) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
                color = ink.copy(alpha = 0.08f), contentColor = ink) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (past) Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                    else Text(index.toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${o.start.toLocalTime()} – ${o.end.toLocalTime()}", style = MaterialTheme.typography.labelLarge)
                Text(o.course.name, style = MaterialTheme.typography.titleLarge)
                val detail = listOf(o.classroom, o.teacher).filter { it.isNotBlank() }.joinToString("\n")
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium)
                if (chip != null) Text(chip, style = MaterialTheme.typography.labelLarge)
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
