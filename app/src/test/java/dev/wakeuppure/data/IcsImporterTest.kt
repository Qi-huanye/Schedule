package dev.wakeuppure.data

import dev.wakeuppure.data.ics.IcsImporter
import dev.wakeuppure.domain.usecase.CourseFilter
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IcsImporterTest {
    private fun calendar(rule: String = "FREQ=WEEKLY;COUNT=3;INTERVAL=2") = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
SUMMARY:Synthetic Math
DTSTART;TZID=Asia/Shanghai:20261227T080000
DTEND;TZID=Asia/Shanghai:20261227T094000
RRULE:$rule
LOCATION:Room\, A
DESCRIPTION:第1 - 2节\nTeacher
BEGIN:VALARM
DESCRIPTION:Alarm text must not become course notes
TRIGGER:-PT10M
ACTION:DISPLAY
END:VALARM
END:VEVENT
END:VCALENDAR
""".trimIndent()

    @Test fun wakeupRecurrencePreservesSundayTimeAndNotesAcrossYear() {
        val data = IcsImporter.parse(calendar())
        assertEquals("2026-12-21", data.schedule.semesterStartDate)
        assertEquals(3, data.courses.single().periods.size)
        val c = CourseFilter.onDate(data, LocalDate.parse("2027-01-10")).single()
        assertEquals("08:00", c.start.toLocalTime().toString())
        assertEquals("09:40", c.end.toLocalTime().toString())
        assertEquals("Room, A", c.classroom)
        assertFalse(c.course.note.contains("Alarm"))
        assertTrue(CourseFilter.onDate(data, LocalDate.parse("2027-01-03")).isEmpty())
    }

    @Test fun untilUsesTimezoneAndIncludesLastOccurrence() {
        val data = IcsImporter.parse(calendar("FREQ=WEEKLY;UNTIL=20270103T160000Z;INTERVAL=1"))
        assertEquals(2, data.courses.single().periods.size)
    }

    @Test fun rejectsUnsupportedRecurrenceInsteadOfDroppingClasses() {
        assertThrows(IllegalArgumentException::class.java) { IcsImporter.parse(calendar("FREQ=MONTHLY;COUNT=3")) }
    }

    @Test fun foldedSummaryAndExclusions() {
        val text = calendar().replace("Synthetic Math", "Synthetic\r\n  Math")
            .replace("LOCATION:", "EXDATE;TZID=Asia/Shanghai:20270110T080000\nLOCATION:")
        val data = IcsImporter.parse(text)
        assertEquals("Synthetic Math", data.courses.single().course.name)
        assertEquals(2, data.courses.single().periods.size)
    }

    @Test fun fileImportDispatchRecognizesIcsWithBom() {
        val shares = dev.wakeuppure.data.wakeup.WakeUpShareRepository(
            dev.wakeuppure.data.wakeup.token.WakeUpTokenImporter(identityProvider = { "unused" }))
        assertEquals(3, shares.parseLocal("\uFEFF" + calendar()).courses.single().periods.size)
    }

    @Test fun ownExportWithoutSectionDescriptionCanBeImported() {
        val original = IcsImporter.parse(calendar())
        val restored = IcsImporter.parse(dev.wakeuppure.data.export.ScheduleExporter.ics(original))
        assertEquals(3, restored.courses.single().periods.size)
        assertEquals("09:40", restored.timeSlots.last().endTime)
    }
}
