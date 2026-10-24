package dev.wakeuppure.data

import dev.wakeuppure.data.export.ScheduleExporter
import dev.wakeuppure.data.wakeup.json.WakeUpJsonImporter
import dev.wakeuppure.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ExportTest {
    private fun sample() = ScheduleData(
        Schedule(id = 7, name = "Test", todoReminderMinutes = 15, semesterStartDate = "2025-12-29", maxWeeks = 3),
        listOf(CourseWithPeriods(Course(id = 8, scheduleId = 7, name = "课,程;\\\n".repeat(20),
            teacher = "Teacher", classroom = "Default", note = "Notes\nsecond"),
            listOf(CoursePeriod(id = 9, courseId = 8, dayOfWeek = 7, startWeek = 1, endWeek = 3,
                weekType = WeekType.ODD, classroom = "Override")))))

    @Test fun calendarResolvesOddWeeksAndSundayAcrossYear() {
        val data = sample()
        val ics = ScheduleExporter.ics(data)
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART:20260104T080000\r\n"))
        assertTrue(ics.contains("DTSTART:20260118T080000\r\n"))
        assertFalse(ics.contains("20260111T080000"))
        assertEquals(ics, ScheduleExporter.ics(data))
        val even = data.copy(courses = data.courses.map { c -> c.copy(periods = c.periods.map { it.copy(weekType = WeekType.EVEN) }) })
        assertTrue(ScheduleExporter.ics(even).contains("DTSTART:20260111T080000"))
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ScheduleExporter.ics(even)).count())
    }

    @Test fun calendarEscapesAndFoldsByUtf8Bytes() {
        val ics = ScheduleExporter.ics(sample())
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse(ics.replace("\r\n", "").contains('\n'))
        ics.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        val unfolded = ics.replace("\r\n ", "")
        assertTrue(unfolded.contains("SUMMARY:课\\,程\\;\\\\\\n"))
        assertTrue(unfolded.contains("LOCATION:Override\r\n"))
        assertTrue(unfolded.contains("Teacher"))
        assertTrue(unfolded.contains("Notes\\nsecond"))
    }

    @Test fun jsonAndLegacyRoundTripCourseDetails() {
        val data = sample()
        listOf(ScheduleExporter.json(data), ScheduleExporter.legacy(data)).forEach { text ->
            val actual = if (text.startsWith("〖")) dev.wakeuppure.data.wakeup.legacy.LegacyWakeUpImporter.parse(text) else WakeUpJsonImporter.parse(text)
            assertEquals(data.schedule.semesterStartDate, actual.schedule.semesterStartDate)
            assertEquals(data.timeSlots, actual.timeSlots)
            assertEquals(data.courses.single().course.name, actual.courses.single().course.name)
            assertEquals("Teacher", actual.courses.single().course.teacher)
            assertEquals("Notes\nsecond", actual.courses.single().course.note)
            assertEquals("Override", actual.courses.single().periods.single().classroom)
            assertEquals(WeekType.ODD, actual.courses.single().periods.single().weekType)
        }
    }

    @Test fun todosExportAsCalendarTasksAndRemainOnlyInAppJson() {
        val todo = Todo(id = 77, scheduleId = 7, title = "交报告", dueAt = 1_766_000_000_000, courseId = 8)
        val data = sample().copy(todos = listOf(todo))
        val ics = ScheduleExporter.ics(data)
        assertTrue(ics.contains("BEGIN:VTODO\r\n"))
        assertTrue(ics.contains("SUMMARY:交报告\r\n"))
        assertTrue(ics.contains("STATUS:NEEDS-ACTION\r\n"))
        assertFalse(ScheduleExporter.json(data).contains("交报告"))
        assertEquals(listOf(todo), ScheduleExporter.restore(ScheduleExporter.appJson(listOf(data))).schedules.single().todos)
    }

    @Test fun calendarRoundTripPreservesTodoDeadlinesCompletionAndEscapedText() {
        val todo = Todo(id = 5, title = "报告,;\\\n多行", dueAt = 1_766_000_000_000, completed = true)
        val mixed = dev.wakeuppure.data.ics.IcsImporter.parse(ScheduleExporter.ics(sample().copy(todos = listOf(todo))))
        assertEquals(todo.title, mixed.todos.single().title)
        assertEquals(todo.dueAt, mixed.todos.single().dueAt)
        assertTrue(mixed.todos.single().completed)
        val tasksOnly = dev.wakeuppure.data.ics.IcsImporter.parse(ScheduleExporter.ics(sample().copy(courses = emptyList(), todos = listOf(todo))))
        assertTrue(tasksOnly.courses.isEmpty())
        assertEquals(todo.dueAt, tasksOnly.todos.single().dueAt)
        assertFalse(ScheduleExporter.legacy(sample().copy(todos = listOf(todo))).contains("多行"))
    }

    @Test fun backupPreservesAllFields() {
        val data = sample()
        assertEquals(Backup(schedules = listOf(data), appearance = "dark"),
            ScheduleExporter.restore(ScheduleExporter.backup(listOf(data), "dark")))
    }

    @Test fun backupRejectsUnknownVersionFormatAndMissingMetadata() {
        listOf("{\"format\":\"WakeUpPure\",\"version\":2,\"schedules\":[]}",
            "{\"format\":\"Other\",\"version\":1,\"schedules\":[]}",
            "{\"schedules\":[]}").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { ScheduleExporter.restore(text) }
        }
    }
}
