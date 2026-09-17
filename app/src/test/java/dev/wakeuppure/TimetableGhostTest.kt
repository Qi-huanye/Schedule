package dev.wakeuppure

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.TimetableGhosts
import org.junit.Assert.*
import org.junit.Test

class TimetableGhostTest {
    private fun course(id: Long, vararg periods: CoursePeriod) = CourseWithPeriods(Course(id=id, name="Course $id"), periods.toList())
    @Test fun otherWeeksAppearInEmptySlots() {
        val c = course(1, CoursePeriod(id=1,startWeek=3,endWeek=8))
        assertEquals(listOf(c to c.periods.single()), TimetableGhosts.select(listOf(c), 1))
        assertTrue(TimetableGhosts.select(listOf(c), 3).isEmpty())
    }
    @Test fun actualCourseWinsEvenWithPartialOverlap() {
        val actual = course(1, CoursePeriod(id=1,startSection=2,endSection=3))
        val other = course(2, CoursePeriod(id=2,startSection=1,endSection=2,startWeek=5,endWeek=8))
        assertTrue(TimetableGhosts.select(listOf(actual,other), 1).isEmpty())
    }
    @Test fun importedWeeklyCopiesOnlyAppearOnce() {
        val c = course(1, CoursePeriod(id=1,startWeek=3,endWeek=3), CoursePeriod(id=2,startWeek=4,endWeek=4))
        assertEquals(1, TimetableGhosts.select(listOf(c), 1).size)
    }
    @Test fun oddEvenAndNearestWeekAreRespected() {
        val odd = course(1, CoursePeriod(id=1,startWeek=1,endWeek=9,weekType=WeekType.ODD))
        val later = course(2, CoursePeriod(id=2,startWeek=8,endWeek=10))
        assertEquals(1L, TimetableGhosts.select(listOf(later,odd), 2).single().first.course.id)
        assertTrue(TimetableGhosts.select(listOf(odd), 3).isEmpty())
    }
    @Test fun differentDaysDoNotBlockEachOther() {
        val actual=course(1,CoursePeriod(id=1,dayOfWeek=1))
        val other=course(2,CoursePeriod(id=2,dayOfWeek=2,startWeek=5,endWeek=8))
        assertEquals(2L,TimetableGhosts.select(listOf(actual,other),1).single().first.course.id)
    }
}
