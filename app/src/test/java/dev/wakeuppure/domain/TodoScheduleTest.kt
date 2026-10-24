package dev.wakeuppure.domain

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.TodoSchedule
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TodoScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun todo(id: Long, time: String, completed: Boolean = false) = Todo(id = id, title = "事项 $id", dueAt = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli(), completed = completed)
    @Test fun unfinishedOverdueTasksCarryForwardButCompletedAndFutureTasksDoNot() {
        val data = listOf(todo(1, "2026-10-23T18:00"), todo(2, "2026-10-23T19:00", true), todo(3, "2026-10-24T12:00", true), todo(4, "2026-10-25T08:00"))
        assertEquals(listOf(1L, 3L), TodoSchedule.agenda(data, LocalDate.parse("2026-10-24"), zone).map { it.id })
    }
    @Test fun nearbyDeadlinesShareAClickableMarkerAndKeepEveryTodo() {
        val data = listOf(todo(1, "2026-10-24T08:05"), todo(2, "2026-10-24T08:10"), todo(3, "2026-10-24T20:00", true))
        val markers = TodoSchedule.markers(data, LocalDate.parse("2026-10-24").toEpochDay(), defaultTimeSlots(), zone)
        assertEquals(listOf(1L, 2L), markers.first().todos.map { it.id })
        assertEquals(3, markers.sumOf { it.todos.size })
        assertTrue(markers.last().position < defaultTimeSlots().size)
    }
    @Test fun deadlinesOutsideLessonHoursAndWithinBreaksHaveBoundedPositions() {
        val slots = listOf(TimeSlot(1,"08:00","09:00"), TimeSlot(2,"10:00","11:00"))
        assertEquals(0f, TodoSchedule.position(LocalTime.of(7,0), slots), 0.001f)
        assertEquals(0.5f, TodoSchedule.position(LocalTime.of(8,30), slots), 0.001f)
        assertEquals(1f, TodoSchedule.position(LocalTime.of(9,30), slots), 0.001f)
        assertEquals(2f, TodoSchedule.position(LocalTime.of(23,0), slots), 0.001f)
    }
}
