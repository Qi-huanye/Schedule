package dev.wakeuppure.notification

import dev.wakeuppure.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TodoRemindersTest {
    private val now = Instant.parse("2026-10-24T08:00:00Z")
    private val due = now.plusSeconds(3600).toEpochMilli()
    private val schedule = ScheduleData(Schedule(id = 1, name = "课表", semesterStartDate = "2026-10-19", todoReminderMinutes = 15), emptyList(), todos = listOf(Todo(id = 2, title = "任务", dueAt = due)))
    @Test fun remindersAreOffByDefaultAndExcludeCompletedOrAlreadyOverdueTasks() {
        assertTrue(TodoReminders.pending(listOf(schedule.copy(schedule = schedule.schedule.copy(todoReminderMinutes = null))), now).isEmpty())
        assertTrue(TodoReminders.pending(listOf(schedule.copy(todos = schedule.todos.map { it.copy(completed = true) })), now).isEmpty())
        assertTrue(TodoReminders.pending(listOf(schedule), now.plusSeconds(3601)).isEmpty())
    }
    @Test fun remindersUseDeadlineLeadAndRescheduleWhenTimeOrLeadChanges() {
        val first = TodoReminders.pending(listOf(schedule), now).single()
        assertEquals(due - 900_000, first.notifyAt)
        assertEquals(now.plusSeconds(3500).toEpochMilli(), TodoReminders.pending(listOf(schedule), now.plusSeconds(3500)).single().notifyAt)
        assertNotEquals(first.key, TodoReminders.pending(listOf(schedule.copy(schedule = schedule.schedule.copy(todoReminderMinutes = 30))), now).single().key)
        assertNotEquals(first.key, TodoReminders.pending(listOf(schedule.copy(todos = schedule.todos.map { it.copy(dueAt = due + 60_000) })), now).single().key)
    }
}
