package dev.wakeuppure.data.shiguangxu

import dev.wakeuppure.domain.model.*
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Maps remote course fields into local models without retaining account data. */
object ShiguangxuImporter {
    private val dateFormat = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat = DateTimeFormatter.ofPattern("HHmmss").withResolverStyle(ResolverStyle.STRICT)

    fun parse(term: JsonObject, rows: JsonArray): ScheduleData {
        try { return parseTerm(term, rows) }
        catch (e: Exception) { throw IllegalArgumentException("时光序导入失败：课程日期、周次或作息数据不完整", e) }
    }

    private fun parseTerm(term: JsonObject, rows: JsonArray): ScheduleData {
        val weeks = term.int("weekCount")
        require(weeks in 1..60) { "时光序学期周数须为 1–60" }
        val start = LocalDateTime.parse(term.string("startDatetime"), dateFormat).toLocalDate()
        val slots = term.getValue("lessonConfigNew").jsonArray.flatMap { group ->
            group.jsonObject.getValue("lessonConfig").jsonArray.map { element ->
                val slot = element.jsonObject
                val begin = LocalTime.parse(slot.string("classBeginTime"), timeFormat)
                val end = LocalTime.parse(slot.string("classEndTime"), timeFormat)
                require(begin.second == 0 && end.second == 0 && begin < end) { "时光序作息时间无效或包含秒数" }
                TimeSlot(slot.int("lessonNo"), begin.format(DateTimeFormatter.ofPattern("HH:mm")),
                    end.format(DateTimeFormatter.ofPattern("HH:mm")))
            }
        }.sortedBy { it.section }
        require(slots.size in 1..30 && slots.map { it.section } == (1..slots.size).toList()) { "时光序节次必须连续且为 1–30 节" }
        require(slots.zipWithNext().all { (a, b) -> a.endTime <= b.startTime }) { "时光序作息时间重叠" }
        val active = rows.map { it.jsonObject }.filter { it["deleted"]?.jsonPrimitive?.booleanOrNull != true }
        val courses = active.groupBy { listOf(it.string("courseName"), it.string("teacher"), it.string("colour")) }
            .values.map { group ->
                val first = group.first()
                val name = first.string("courseName")
                val color = first.string("colour").ifBlank { "#C1E8DE" }
                require(name.isNotBlank() && name.length <= 200) { "时光序课程名称无效" }
                require(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(color)) { "时光序课程颜色无效" }
                val periods = group.flatMap { row ->
                    require(row.string("termId").let { it.isBlank() || it == term.string("id") }) { "课程不属于该时光序学期" }
                    val courseWeeks = row.ints("courseWeeksBitSetList")
                    val days = row.ints("weekBitSetList")
                    val sections = row.ints("lessonBitSetList")
                    require(courseWeeks.isNotEmpty() && courseWeeks.all { it in 1..weeks }) { "时光序课程周次无效" }
                    require(days.isNotEmpty() && days.all { it in 1..7 }) { "时光序课程星期无效" }
                    require(sections.isNotEmpty() && sections.all { it in 1..slots.size }) { "时光序课程节次无效" }
                    require(row.int("beginLessonNo") == sections.first() && row.int("duration") == sections.size &&
                        sections == (sections.first()..sections.last()).toList()) { "不支持不连续或不一致的时光序课程节次" }
                    days.flatMap { day -> courseWeeks.map { week ->
                        CoursePeriod(dayOfWeek = day, startSection = sections.first(), endSection = sections.last(),
                            startWeek = week, endWeek = week, classroom = row.string("classroom"))
                    } }
                }.distinct()
                CourseWithPeriods(Course(name = name, teacher = first.string("teacher"), color = color), compactWeeks(periods))
            }
        return ScheduleData(Schedule(name = term.string("termName").ifBlank { "时光序课表" },
            semesterStartDate = start.toString(), maxWeeks = weeks), courses, slots)
    }

    private fun compactWeeks(periods: List<CoursePeriod>): List<CoursePeriod> =
        periods.groupBy { it.copy(startWeek = 1, endWeek = 1) }.flatMap { (base, entries) ->
            val weeks = entries.map { it.startWeek }.distinct().sorted()
            if (weeks.size > 1 && weeks.zipWithNext().all { (a, b) -> b - a == 2 }) {
                listOf(base.copy(startWeek = weeks.first(), endWeek = weeks.last(),
                    weekType = if (weeks.first() % 2 == 1) WeekType.ODD else WeekType.EVEN))
            } else buildList {
                var first = weeks.first()
                var last = first
                for (week in weeks.drop(1)) {
                    if (week != last + 1) {
                        add(base.copy(startWeek = first, endWeek = last))
                        first = week
                    }
                    last = week
                }
                add(base.copy(startWeek = first, endWeek = last))
            }
        }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
    private fun JsonObject.int(key: String) = requireNotNull(this[key]?.jsonPrimitive?.intOrNull) { "时光序数值字段缺失或无效：$key" }
    private fun JsonObject.ints(key: String) = getValue(key).jsonArray.map {
        requireNotNull(it.jsonPrimitive.intOrNull) { "时光序列表数值无效：$key" }
    }.distinct().sorted()
}
