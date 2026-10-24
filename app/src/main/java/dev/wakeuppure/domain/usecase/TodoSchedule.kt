package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.TimeSlot
import dev.wakeuppure.domain.model.Todo
import java.time.*

/** Shared rules keep yesterday's unfinished deadlines visible in the app and widget. */
object TodoSchedule {
    fun agenda(todos: List<Todo>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Todo> = todos.filter {
        val dueDate = Instant.ofEpochMilli(it.dueAt).atZone(zone).toLocalDate()
        dueDate == date || (!it.completed && dueDate < date)
    }.sortedBy { it.dueAt }

    fun position(time: LocalTime, slots: List<TimeSlot>): Float {
        if (slots.isEmpty()) return 0f
        val seconds = time.toSecondOfDay()
        slots.forEachIndexed { index, slot ->
            val start = LocalTime.parse(slot.startTime).toSecondOfDay()
            val end = LocalTime.parse(slot.endTime).toSecondOfDay()
            if (seconds < start) {
                if (index == 0) return 0f
                val previousEnd = LocalTime.parse(slots[index - 1].endTime).toSecondOfDay()
                // Breaks lie at the shared boundary between the two lesson rows.
                return index.toFloat() - 0.1f + 0.2f * (seconds - previousEnd).toFloat() / (start - previousEnd).coerceAtLeast(1)
            }
            if (seconds <= end) return index + (seconds - start).toFloat() / (end - start).coerceAtLeast(1)
        }
        return slots.size.toFloat()
    }

    fun markers(todos: List<Todo>, epochDay: Long, slots: List<TimeSlot>, zone: ZoneId = ZoneId.systemDefault()): List<TodoMarker> {
        val result = mutableListOf<TodoMarker>()
        todos.filter { Instant.ofEpochMilli(it.dueAt).atZone(zone).toLocalDate().toEpochDay() == epochDay }
            .sortedBy { it.dueAt }.forEach { todo ->
                val position = position(Instant.ofEpochMilli(todo.dueAt).atZone(zone).toLocalTime(), slots)
                    .coerceIn(0f, (slots.size - 0.7f).coerceAtLeast(0f))
                val previous = result.lastOrNull()
                if (previous != null && position - previous.position < 0.7f) {
                    result[result.lastIndex] = previous.copy(todos = previous.todos + todo)
                } else result += TodoMarker(position, listOf(todo))
            }
        return result
    }
}

data class TodoMarker(val position: Float, val todos: List<Todo>)
