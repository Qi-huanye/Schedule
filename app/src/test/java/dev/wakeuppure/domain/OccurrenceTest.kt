package dev.wakeuppure.domain

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class OccurrenceTest {
    private val data = ScheduleData(Schedule(name = "Term", semesterStartDate = "2025-12-29", maxWeeks = 2),
        listOf(CourseWithPeriods(Course(name = "Sunday", classroom = "Default"),
            listOf(CoursePeriod(dayOfWeek = 7, startSection = 1, endSection = 2, startWeek = 1, endWeek = 2, classroom = "Override")))))
    @Test fun sundayAndSemesterBounds() {
        assertTrue(CourseFilter.onDate(data, LocalDate.parse("2025-12-28")).isEmpty())
        assertEquals("Override", CourseFilter.onDate(data, LocalDate.parse("2026-01-04")).single().classroom)
        assertEquals(1, CourseFilter.onDate(data, LocalDate.parse("2026-01-11")).size)
        assertTrue(CourseFilter.onDate(data, LocalDate.parse("2026-01-18")).isEmpty())
    }
    @Test fun nextBeforeTermAndAfterEnd() {
        assertEquals(LocalDate.parse("2026-01-04"), CourseFilter.next(data, LocalDateTime.parse("2025-12-01T00:00"))!!.date)
        assertNull(CourseFilter.next(data, LocalDateTime.parse("2026-01-11T08:00")))
    }
}
