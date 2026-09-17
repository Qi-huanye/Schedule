package dev.wakeuppure

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class SemesterAdjustmentTest {
    @Test fun earlierSemesterKeepsActualCourseDates() {
        val original = CoursePeriod(startWeek = 1, endWeek = 18)
        val shifted = SemesterAdjustment.adjust(listOf(original), "2026-09-14", "2026-08-31", true, 0).single()
        assertEquals(3, shifted.startWeek)
        assertEquals(20, shifted.endWeek)
        assertEquals(WeekCalculator.date("2026-09-14", 1, 1), WeekCalculator.date("2026-08-31", shifted.startWeek, 1))
    }
    @Test fun alreadyCorrectStartCanMoveAllCoursesTwoWeeks() {
        val shifted = SemesterAdjustment.adjust(listOf(CoursePeriod(startWeek = 1, endWeek = 18)), "2026-08-31", "2026-08-31", true, 2).single()
        assertEquals(3, shifted.startWeek)
        assertEquals(20, shifted.endWeek)
    }
    @Test fun oddShiftFlipsParityToPreserveOccurrences() {
        val original = CoursePeriod(startWeek = 1, endWeek = 9, weekType = WeekType.ODD)
        val shifted = SemesterAdjustment.adjust(listOf(original), "2026-09-14", "2026-09-07", true, 0).single()
        assertEquals(WeekType.EVEN, shifted.weekType)
        (1..9).forEach { assertEquals(CourseFilter.matches(original, it), CourseFilter.matches(shifted, it + 1)) }
    }
    @Test fun keepingWeekNumbersIsExplicitlySupported() {
        val original = listOf(CoursePeriod())
        assertEquals(original, SemesterAdjustment.adjust(original, "2026-09-14", "2026-08-31", false, 0))
    }
    @Test fun outOfRangeShiftCannotDropCoursesSilently() {
        assertTrue(runCatching { SemesterAdjustment.adjust(listOf(CoursePeriod()), "2026-09-14", "2026-09-21", true, 0) }.isFailure)
        assertTrue(runCatching { SemesterAdjustment.adjust(listOf(CoursePeriod(endWeek=60)), "2026-09-14", "2026-09-07", true, 0) }.isFailure)
    }
}
