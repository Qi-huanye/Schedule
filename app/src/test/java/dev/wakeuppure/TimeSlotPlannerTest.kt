package dev.wakeuppure

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.TimeSlotPlanner
import org.junit.Assert.*
import org.junit.Test

class TimeSlotPlannerTest {
    @Test fun extendingImportedEightPreservesTimesAndAddsEvening() {
        val original = defaultTimeSlots().take(8).toMutableList().apply { this[7] = TimeSlot(8, "17:15", "18:00") }
        val result = TimeSlotPlanner.resize(original, 12, 8)
        assertEquals(original, result.take(8))
        assertEquals("19:00", result[8].startTime)
        assertEquals(12, result.size)
    }
    @Test fun thirtySectionsRemainWithinDayAndOrdered() {
        val result = TimeSlotPlanner.resize(defaultTimeSlots().take(8), 30, 8)
        assertEquals(30, result.size)
        result.zipWithNext().forEach { (a,b) -> assertTrue(a.endTime <= b.startTime) }
        result.forEach { assertTrue(it.startTime < it.endTime) }
    }
    @Test fun occupiedSectionsCannotBeRemoved() {
        assertTrue(runCatching { TimeSlotPlanner.resize(defaultTimeSlots(), 7, 8) }.isFailure)
        assertEquals(8, TimeSlotPlanner.resize(defaultTimeSlots(), 8, 8).size)
    }
    @Test fun exhaustedDayIsReportedWithoutChangingOriginal() {
        val original = listOf(TimeSlot(1, "23:00", "23:59"))
        assertTrue(runCatching { TimeSlotPlanner.resize(original, 2, 1) }.isFailure)
        assertEquals("23:59", original.single().endTime)
    }
}
