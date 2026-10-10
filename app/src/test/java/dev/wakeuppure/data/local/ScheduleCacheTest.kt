package dev.wakeuppure.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.wakeuppure.domain.model.Backup
import dev.wakeuppure.domain.model.Course
import dev.wakeuppure.domain.model.CoursePeriod
import dev.wakeuppure.domain.model.CourseWithPeriods
import dev.wakeuppure.domain.model.Schedule
import dev.wakeuppure.domain.model.ScheduleData
import dev.wakeuppure.domain.model.defaultTimeSlots
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScheduleCacheTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cache get() = ScheduleCache(app)

    private fun sample() = ScheduleData(
        schedule = Schedule(id = 3, name = "秋季", semesterStartDate = "2026-09-07", maxWeeks = 20, current = true),
        courses = listOf(CourseWithPeriods(
            course = Course(id = 1, scheduleId = 3, name = "高等数学", color = "#123456"),
            periods = listOf(CoursePeriod(id = 1, courseId = 1, dayOfWeek = 2, startSection = 1, endSection = 2)),
        )),
        timeSlots = defaultTimeSlots(),
    )

    @Test fun emptyCacheReportsNoSchedules() {
        assertTrue(cache.load().isEmpty())
    }

    @Test fun snapshotSurvivesRoundTrip() {
        val data = sample()
        cache.save(listOf(data))
        // Timestamps are not part of the backup format, so only the timetable itself is compared.
        val restored = cache.load().single()
        assertEquals(data.schedule.copy(createdAt = 0, updatedAt = 0), restored.schedule.copy(createdAt = 0, updatedAt = 0))
        assertEquals(data.courses, restored.courses)
        assertEquals(data.timeSlots, restored.timeSlots)
    }

    @Test fun corruptEntryIsIgnored() {
        app.getSharedPreferences("scheduleCache", 0).edit().putString("schedules", "{not json").commit()
        assertTrue(cache.load().isEmpty())
    }

    @Test fun entryFromAnotherFormatIsIgnored() {
        val foreign = Json.encodeToString(Backup(format = "OtherApp", schedules = listOf(sample())))
        app.getSharedPreferences("scheduleCache", 0).edit().putString("schedules", foreign).commit()
        assertTrue(cache.load().isEmpty())
    }

    @Test fun newestSnapshotReplacesTheOldOne() {
        cache.save(listOf(sample()))
        assertTrue(cache.load().isNotEmpty())
        cache.save(emptyList())
        assertTrue("删除全部课表后不应再显示旧快照", cache.load().isEmpty())
    }
}
