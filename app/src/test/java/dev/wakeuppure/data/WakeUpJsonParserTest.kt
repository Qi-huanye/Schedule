package dev.wakeuppure.data

import dev.wakeuppure.data.wakeup.json.WakeUpJsonImporter
import org.junit.Assert.*
import org.junit.Test

class WakeUpJsonParserTest {
    @Test fun nativeUnusedPlaceholderSlotsDoNotBlockRealCourses() {
        val source = """{"name":"作息"}
[{"node":1,"startTime":"08:00","endTime":"08:45"},{"node":2,"startTime":"08:55","endTime":"09:40"},{"node":3,"startTime":"00:00","endTime":"00:45"}]
{"tableName":"合成","startDate":"2026-9-14","maxWeek":20}
[{"id":9,"courseName":"Test","color":"#C1E8DE"}]
[{"id":9,"day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0}]"""
        assertEquals(2, WakeUpJsonImporter.parse(source).timeSlots.size)
        assertThrows(IllegalArgumentException::class.java) { WakeUpJsonImporter.parse(source.replace("\"step\":2", "\"step\":3")) }
    }
    @Test fun officialUnpaddedSemesterDateIsNormalized() {
        val data = WakeUpJsonImporter.parse("""{"startDate":"2026-8-31","courses":[]}""")
        assertEquals("2026-08-31", data.schedule.semesterStartDate)
    }

    @Test fun invalidCalendarDateIsNotSilentlyRolledForward() {
        assertThrows(IllegalArgumentException::class.java) {
            WakeUpJsonImporter.parse("""{"startDate":"2026-2-30","courses":[]}""")
        }
    }

    @Test(expected = IllegalArgumentException::class) fun malformedNumberRejected() {
        WakeUpJsonImporter.parse("""{"courses":[{"name":"数学","day":"wrong"}]}""")
    }
    @Test fun externalCourseSchema() {
        val d = WakeUpJsonImporter.parse("""{"name":"秋季","startDate":"2026-09-01","courses":[{"name":"数学","day":7,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":1,"position":"A101"}]}""")
        assertEquals("数学", d.courses.single().course.name)
        assertEquals(7, d.courses.single().periods.single().dayOfWeek)
        assertEquals(2, d.courses.single().periods.single().endSection)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidDayRejected() {
        WakeUpJsonImporter.parse("""{"courses":[{"name":"数学","day":9,"startNode":1,"step":2}]}""")
    }
    @Test fun nativeFiveRecordsJoinBaseAndDetail() {
        val data = WakeUpJsonImporter.parse("""{"name":"作息"}
[{"node":1,"startTime":"08:00","endTime":"08:45"}]
{"tableName":"春季","startDate":"2026-02-23","maxWeek":20}
[{"id":9,"courseName":"物理","color":"#C1E8DE"}]
[{"id":9,"day":1,"startNode":1,"step":1,"startWeek":1,"endWeek":12,"type":0,"room":"北楼"}]""")
        assertEquals("物理", data.courses.single().course.name)
        assertEquals("北楼", data.courses.single().periods.single().classroom)
    }
}
