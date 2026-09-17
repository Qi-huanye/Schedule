package dev.wakeuppure
import dev.wakeuppure.domain.usecase.FixedLessonTime
import dev.wakeuppure.domain.model.TimeSlot
import org.junit.Assert.*
import org.junit.Test
class FixedLessonTimeTest {
    @Test fun calculatesAcrossHourBoundary() { assertEquals("09:15", FixedLessonTime.end("08:30",45)) }
    @Test fun rejectsOvernightAndInvalidDurations() {
        assertTrue(runCatching { FixedLessonTime.end("23:45",45) }.isFailure)
        assertTrue(runCatching { FixedLessonTime.end("08:00",0) }.isFailure)
    }
    @Test fun keepsStartsAndBreaksWhenApplyingDuration() {
        val result=FixedLessonTime.apply(listOf(TimeSlot(1,"08:00","08:30"),TimeSlot(2,"09:00","09:30")),45)
        assertEquals(listOf(TimeSlot(1,"08:00","08:45"),TimeSlot(2,"09:00","09:45")),result)
    }
}
