package dev.wakeuppure.data

import dev.wakeuppure.data.shiguangxu.ShiguangxuImporter
import dev.wakeuppure.domain.model.WeekType
import dev.wakeuppure.domain.usecase.CourseFilter
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ShiguangxuImporterTest {
    private val term = Json.parseToJsonElement("""{"id":"term-a","termName":"测试学期","startDatetime":"20260831000000","weekCount":22,"school":"忽略","lessonConfigNew":[{"lessonConfig":[{"lessonNo":1,"classBeginTime":"080000","classEndTime":"084500"},{"lessonNo":2,"classBeginTime":"085500","classEndTime":"094000"}]}]}""").jsonObject
    private val course = Json.parseToJsonElement("""{"id":"course-a","version":"2","termId":"term-a","courseName":"测试课程","teacher":"教师","classroom":"A101","colour":"#4BA2F3","courseWeeksBitSetList":[3,4,7],"weekBitSetList":[1,7],"lessonBitSetList":[1,2],"beginLessonNo":1,"duration":2,"deleted":false}""").jsonObject

    private fun parse(row: JsonObject = course, info: JsonObject = term) =
        ShiguangxuImporter.parse(info, JsonArray(listOf(row)))

    @Test fun preservesExplicitWeeksDaysTimesAndMetadata() {
        val data = parse()
        assertEquals("2026-08-31", data.schedule.semesterStartDate)
        assertEquals(22, data.schedule.maxWeeks)
        assertEquals("08:55", data.timeSlots[1].startTime)
        val course = data.courses.single()
        assertEquals("教师", course.course.teacher)
        assertEquals(4, course.periods.size)
        assertEquals(setOf(3 to 4, 7 to 7), course.periods.map { it.startWeek to it.endWeek }.toSet())
        assertTrue(course.periods.all { it.classroom == "A101" && it.endSection == 2 })
        assertEquals(setOf(1, 7), course.periods.map { it.dayOfWeek }.toSet())
    }

    private fun row(weeks: List<Int>, room: String = "A101") = JsonObject(course + mapOf(
        "courseWeeksBitSetList" to JsonArray(weeks.map(::JsonPrimitive)),
        "weekBitSetList" to JsonArray(listOf(JsonPrimitive(1))), "classroom" to JsonPrimitive(room)))

    @Test fun consecutiveWeeksBecomeOneRangeAndOddEvenWeeksRetainParity() {
        for ((weeks, type) in listOf((3..18).toList() to WeekType.ALL,
            (3..17 step 2).toList() to WeekType.ODD, (4..18 step 2).toList() to WeekType.EVEN)) {
            val period = parse(row(weeks)).courses.single().periods.single()
            assertEquals(weeks.first(), period.startWeek)
            assertEquals(weeks.last(), period.endWeek)
            assertEquals(type, period.weekType)
        }
    }

    @Test fun joinsAdjacentRowsWithoutCombiningDifferentClassrooms() {
        val data = ShiguangxuImporter.parse(term, JsonArray(listOf(row(listOf(3, 4)), row(listOf(4, 5, 6)), row(listOf(7, 8), "B202"))))
        val periods = data.courses.single().periods
        assertEquals(2, periods.size)
        assertEquals(3 to 6, periods.single { it.classroom == "A101" }.let { it.startWeek to it.endWeek })
        assertEquals(7 to 8, periods.single { it.classroom == "B202" }.let { it.startWeek to it.endWeek })
    }

    @Test fun arbitraryWeekSetsNeverGainOrLoseOccurrencesWhenCompacted() {
        for (mask in 1 until (1 shl 10)) {
            val weeks = (1..10).filter { mask and (1 shl (it - 1)) != 0 }
            val periods = parse(row(weeks.reversed() + weeks.first())).courses.single().periods
            val expanded = periods.flatMap { p -> (1..22).filter { CourseFilter.matches(p, it) } }
            assertEquals(weeks, expanded.sorted())
        }
    }

    @Test fun rejectsOutOfRangeAndInconsistentSections() {
        for ((key, value) in listOf("courseWeeksBitSetList" to "[0]", "weekBitSetList" to "[8]",
            "lessonBitSetList" to "[1,3]", "duration" to "1")) {
            assertThrows(IllegalArgumentException::class.java) {
                parse(JsonObject(course + (key to Json.parseToJsonElement(value))))
            }
        }
    }

    @Test fun rejectsInvalidDatesAndOverlappingTimes() {
        assertThrows(Exception::class.java) { parse(info = JsonObject(term + ("startDatetime" to JsonPrimitive("20260230000000")))) }
        assertThrows(IllegalArgumentException::class.java) {
            parse(info = Json.parseToJsonElement(term.toString().replace("085500", "083000")).jsonObject)
        }
    }

}
