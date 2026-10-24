package dev.wakeuppure.notification

import dev.wakeuppure.domain.model.ScheduleData
import java.time.Instant

internal data class TodoReminder(val key: String, val scheduleId: Long, val todoId: Long, val dueAt: Long, val lead: Int, val notifyAt: Long)

internal object TodoReminders {
    fun pending(data: List<ScheduleData>, now: Instant): List<TodoReminder> = data.flatMap { schedule ->
        val lead = schedule.schedule.todoReminderMinutes ?: return@flatMap emptyList()
        schedule.todos.filter { !it.completed && it.dueAt > now.toEpochMilli() }.map { todo ->
            TodoReminder("todo-${schedule.schedule.id}-${todo.id}-${todo.dueAt}-$lead", schedule.schedule.id, todo.id,
                todo.dueAt, lead, (todo.dueAt - lead * 60_000L).coerceAtLeast(now.toEpochMilli()))
        }
    }
}
