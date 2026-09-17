package dev.wakeuppure.domain

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class CourseFilterTest {
    @Test fun parityAndInclusiveBounds() {
        val p = CoursePeriod(dayOfWeek = 7, startWeek = 2, endWeek = 6, weekType = WeekType.ODD)
        assertFalse(CourseFilter.matches(p, 2))
        assertTrue(CourseFilter.matches(p, 3))
        assertFalse(CourseFilter.matches(p, 7))
        assertTrue(CourseFilter.matches(p.copy(weekType = WeekType.EVEN), 6))
        assertFalse(CourseFilter.matches(p, 0))
    }
    @Test fun conflictingCoursesHaveSeparateLanes() {
        val a = CoursePeriod(id = 1, startSection = 1, endSection = 3)
        val b = CoursePeriod(id = 2, startSection = 2, endSection = 4)
        val c = CoursePeriod(id = 3, startSection = 5, endSection = 6)
        val layout = CourseFilter.lanes(listOf(a,b,c))
        assertNotEquals(layout.getValue(1).first, layout.getValue(2).first)
        assertEquals(2, layout.getValue(1).second)
        assertEquals(1, layout.getValue(3).second)
    }
}
