package dev.wakeuppure.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.data.local.PureDatabase
import dev.wakeuppure.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleRepositoryTest {
    private lateinit var db: PureDatabase
    private lateinit var repository: ScheduleRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PureDatabase::class.java).build()
        repository = ScheduleRepository(db)
    }
    @After fun close() = db.close()
    private fun schedule(name: String = "Spring") = Schedule(name = name, semesterStartDate = "2026-09-07")

    @Test fun courseCanExtendEightSlotScheduleAtomically() = runBlocking {
        val id = repository.saveSchedule(schedule(), defaultTimeSlots().take(8))
        repository.saveCourse(Course(scheduleId = id, name = "Evening"),
            listOf(CoursePeriod(startSection = 9, endSection = 10)), defaultTimeSlots().take(10))
        val result = repository.snapshot().single()
        assertEquals(10, result.timeSlots.size)
        assertEquals(9, result.courses.single().periods.single().startSection)
        assertEquals(10, result.courses.single().periods.single().endSection)
    }

    @Test fun invalidCourseRollsBackSlotExtension() = runBlocking {
        val id = repository.saveSchedule(schedule(), defaultTimeSlots().take(8))
        assertTrue(runCatching { repository.saveCourse(Course(scheduleId = id, name = ""),
            listOf(CoursePeriod(startSection = 9, endSection = 10)), defaultTimeSlots().take(10)) }.isFailure)
        assertEquals(8, repository.snapshot().single().timeSlots.size)
    }

    @Test fun semesterCorrectionMovesWeeksAndPreservesCourseIds() = runBlocking {
        val id = repository.saveSchedule(schedule().copy(semesterStartDate = "2026-09-14", maxWeeks = 18), defaultTimeSlots())
        repository.saveCourse(Course(scheduleId = id, name = "Autumn"), listOf(CoursePeriod(startWeek = 1, endWeek = 18)))
        val before = repository.snapshot().single()
        repository.saveSchedule(before.schedule.copy(semesterStartDate = "2026-08-31"), before.timeSlots, preserveDates = true)
        val after = repository.snapshot().single()
        assertEquals(20, after.schedule.maxWeeks)
        assertEquals(3, after.courses.single().periods.single().startWeek)
        assertEquals(before.courses.single().periods.single().id, after.courses.single().periods.single().id)
        assertEquals(before.courses.single().course, after.courses.single().course)
    }

    @Test fun alreadyEditedSemesterCanSetFirstClassToThirdWeek() = runBlocking {
        val id = repository.saveSchedule(schedule().copy(semesterStartDate = "2026-08-31"), defaultTimeSlots())
        repository.saveCourse(Course(scheduleId = id, name = "Autumn"), listOf(CoursePeriod(startWeek = 1, endWeek = 18)))
        val before = repository.snapshot().single()
        repository.saveSchedule(before.schedule, before.timeSlots, preserveDates = true, weekOffset = 2)
        val after = repository.snapshot().single()
        assertEquals("2026-08-31", after.schedule.semesterStartDate)
        assertEquals(3, after.courses.single().periods.single().startWeek)
        assertEquals(20, after.courses.single().periods.single().endWeek)
        assertEquals(before.timeSlots, after.timeSlots)
    }

    @Test fun invalidWeekShiftLeavesScheduleAndCoursesUnchanged() = runBlocking {
        val id = repository.saveSchedule(schedule(), defaultTimeSlots())
        repository.saveCourse(Course(scheduleId = id, name = "Autumn"), listOf(CoursePeriod()))
        val before = repository.snapshot().single()
        assertTrue(runCatching { repository.saveSchedule(before.schedule.copy(semesterStartDate = "2026-09-14"), before.timeSlots, preserveDates = true) }.isFailure)
        assertEquals(before, repository.snapshot().single())
    }

    @Test fun paletteUpdatesOnlyItsScheduleAndKeepsIndividualEdits() = runBlocking {
        val id = repository.saveSchedule(schedule(), defaultTimeSlots())
        val other = repository.saveSchedule(schedule("Other"), defaultTimeSlots())
        repository.saveCourse(Course(scheduleId=id,name="A"), listOf(CoursePeriod()))
        repository.saveCourse(Course(scheduleId=other,name="B",color="#123456"), listOf(CoursePeriod()))
        val original = repository.snapshot().first()
        repository.saveSchedule(original.schedule.copy(colorPalette="sky", fixedLessonMinutes=45), original.timeSlots)
        var changed = repository.snapshot().first()
        assertEquals(CoursePalettes.color("sky",0), changed.courses.single().course.color)
        assertEquals(45, changed.schedule.fixedLessonMinutes)
        assertEquals("#123456", repository.snapshot().last().courses.single().course.color)
        repository.saveCourse(changed.courses.single().course.copy(color="#ABCDEF"), changed.courses.single().periods)
        repository.saveSchedule(changed.schedule, changed.timeSlots)
        changed = repository.snapshot().first()
        assertEquals("#ABCDEF", changed.courses.single().course.color)
    }

    @Test fun fixedDurationCannotPersistMismatchedEndTimes() = runBlocking {
        assertTrue(runCatching { repository.saveSchedule(schedule().copy(fixedLessonMinutes=50), defaultTimeSlots()) }.isFailure)
        assertTrue(repository.snapshot().isEmpty())
    }

    @Test fun writesReadsAndCascades() = runBlocking {
        val id = repository.saveSchedule(schedule(), defaultTimeSlots())
        repository.saveCourse(Course(scheduleId = id, name = "Math"), listOf(CoursePeriod()))
        val data = repository.schedules.first().single()
        assertEquals("Math", data.courses.single().course.name)
        assertEquals(id, data.courses.single().course.scheduleId)
        assertEquals(defaultTimeSlots(), data.timeSlots)
        repository.deleteSchedule(id)
        assertTrue(repository.snapshot().isEmpty())
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM course_periods").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun switchesCurrentAndDeletesCurrent() = runBlocking {
        val first = repository.saveSchedule(schedule(), defaultTimeSlots())
        val second = repository.saveSchedule(schedule("Autumn"), defaultTimeSlots())
        repository.selectSchedule(second)
        assertEquals(second, repository.snapshot().single { it.schedule.current }.schedule.id)
        repository.deleteSchedule(second)
        assertEquals(first, repository.snapshot().single { it.schedule.current }.schedule.id)
    }

    @Test fun importsRemapIdsCollapsePeriodsAndKeepConflicts() = runBlocking {
        val course = Course(id = 99, scheduleId = 42, name = "Math")
        val period = CoursePeriod(id = 71, courseId = 99)
        val source = ScheduleData(schedule().copy(id = 42, current = true), listOf(
            CourseWithPeriods(course, listOf(period, period.copy(id = 72))),
            CourseWithPeriods(course.copy(id = 100, name = "Physics"), listOf(period.copy(courseId = 100)))
        ))
        val ids = repository.importSchedules(listOf(source, source))
        assertEquals(2, ids.distinct().size)
        val result = repository.snapshot()
        assertEquals(1, result.count { it.schedule.current })
        result.forEach { data ->
            assertNotEquals(42L, data.schedule.id)
            assertEquals(2, data.courses.size)
            data.courses.forEach { c ->
                assertEquals(1, c.periods.size)
                assertEquals(data.schedule.id, c.course.scheduleId)
                assertEquals(c.course.id, c.periods.single().courseId)
            }
        }
    }

    @Test fun invalidImportRollsBackWholeBatch() = runBlocking {
        val before = repository.saveSchedule(schedule(), defaultTimeSlots())
        expectInvalid { repository.importSchedules(listOf(ScheduleData(schedule().copy(current = true)), ScheduleData(schedule().copy(semesterStartDate = "2026-02-30")))) }
        assertEquals(listOf(before), repository.snapshot().map { it.schedule.id })
        assertTrue(repository.snapshot().single().schedule.current)
    }

    @Test fun rejectsInvalidRangesAndOrphaningUpdates() = runBlocking {
        val id = repository.saveSchedule(schedule(), defaultTimeSlots())
        val courseId = repository.saveCourse(Course(scheduleId = id, name = "Math"), listOf(CoursePeriod(endSection = 12)))
        expectInvalid { repository.saveSchedule(schedule().copy(id = id), defaultTimeSlots().take(10)) }
        expectInvalid { repository.saveSchedule(schedule().copy(id = id, maxWeeks = 10), defaultTimeSlots()) }
        expectInvalid { repository.saveCourse(Course(id = courseId, scheduleId = id, name = "Math"), listOf(CoursePeriod(dayOfWeek = 8))) }
        expectInvalid { repository.saveSchedule(schedule(), listOf(TimeSlot(1, "10:00", "09:00"))) }
        assertEquals(12, repository.snapshot().single().courses.single().periods.single().endSection)
    }

    @Test fun rejectsMovingCourseOrForeignPeriods() = runBlocking {
        val a = repository.saveSchedule(schedule(), defaultTimeSlots())
        val b = repository.saveSchedule(schedule("Other"), defaultTimeSlots())
        val course = repository.saveCourse(Course(scheduleId = a, name = "Math"), listOf(CoursePeriod()))
        expectInvalid { repository.saveCourse(Course(id = course, scheduleId = b, name = "Math"), listOf(CoursePeriod())) }
        expectInvalid { repository.saveCourse(Course(scheduleId = a, name = "Other"), listOf(CoursePeriod(courseId = course))) }
    }

    @Test fun deletingCoursePreservesScheduleAndSlots() = runBlocking {
        val id = repository.saveSchedule(schedule(), defaultTimeSlots())
        val courseId = repository.saveCourse(Course(scheduleId = id, name = "Math"), listOf(CoursePeriod()))
        repository.deleteCourse(courseId)
        val data = repository.snapshot().single()
        assertTrue(data.courses.isEmpty())
        assertEquals(defaultTimeSlots(), data.timeSlots)
        expectInvalid { repository.selectSchedule(id + 100) }
        assertTrue(repository.snapshot().single().schedule.current)
    }

    @Test fun persistsAcrossReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "repository-reopen-test.db"
        context.deleteDatabase(name)
        try {
            Room.databaseBuilder(context, PureDatabase::class.java, name).build().let { fileDb ->
                try { ScheduleRepository(fileDb).saveSchedule(schedule(), defaultTimeSlots()) } finally { fileDb.close() }
            }
            Room.databaseBuilder(context, PureDatabase::class.java, name).build().let { fileDb ->
                try { assertEquals("Spring", ScheduleRepository(fileDb).snapshot().single().schedule.name) } finally { fileDb.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    private suspend fun expectInvalid(block: suspend () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }
}
