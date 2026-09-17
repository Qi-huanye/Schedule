package dev.wakeuppure.data.export

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.CourseFilter
import dev.wakeuppure.domain.usecase.WeekCalculator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object ScheduleExporter {
    private val codec = Json { encodeDefaults = true }
    private val localTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcTime = localTime.withZone(ZoneOffset.UTC)

    fun json(data: ScheduleData): String = envelope(data, false).toString()

    fun legacy(data: ScheduleData): String = "〖来自WakeUp课程表〗\n" + envelope(data, true).toString()

    private fun envelope(data: ScheduleData, encoded: Boolean): JsonObject = buildJsonObject {
        put("name", data.schedule.name)
        put("startDate", data.schedule.semesterStartDate)
        put("tableInfo", buildJsonObject {
            put("name", data.schedule.name)
            put("startDate", data.schedule.semesterStartDate)
            put("maxWeek", data.schedule.maxWeeks)
            put("timeList", buildJsonArray {
                data.timeSlots.forEach { slot -> add(buildJsonObject {
                    put("node", slot.section); put("startTime", slot.startTime); put("endTime", slot.endTime)
                }) }
            })
        })
        val courses = buildJsonArray {
            data.courses.forEach { item -> item.periods.forEach { period -> add(buildJsonObject {
                put("name", item.course.name); put("teacher", item.course.teacher)
                put("position", period.classroom.ifBlank { item.course.classroom })
                put("color", item.course.color); put("note", item.course.note)
                put("day", period.dayOfWeek); put("startNode", period.startSection)
                put("step", period.endSection - period.startSection + 1)
                put("startWeek", period.startWeek); put("endWeek", period.endWeek)
                put("type", period.weekType.ordinal)
            }) } }
        }
        if (encoded) put("courseDetailJson", URLEncoder.encode(courses.toString(), "UTF-8"))
        else put("courses", courses)
    }

    fun backup(list: List<ScheduleData>, appearance: String = "system"): String =
        codec.encodeToString(Backup(schedules = list, appearance = appearance))

    fun restore(text: String): Backup {
        val root = codec.parseToJsonElement(text).jsonObject
        require(root["format"]?.jsonPrimitive?.content == "WakeUpPure") { "Unsupported backup format" }
        require(root["version"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported backup version" }
        return codec.decodeFromString<Backup>(text)
    }

    fun ics(data: ScheduleData): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//WakeUpPure//Schedule//EN", "CALSCALE:GREGORIAN")
        val first = WeekCalculator.monday(data.schedule.semesterStartDate)
        for (offset in 0 until data.schedule.maxWeeks * 7) {
            CourseFilter.onDate(data, first.plusDays(offset.toLong())).forEach { occurrence ->
                // Indexes disambiguate unsaved models whose default database IDs are all zero.
                val courseIndex = data.courses.indexOfFirst { it.course === occurrence.course }
                val periodIndex = data.courses[courseIndex].periods.indexOfFirst { it === occurrence.period }
                val courseKey = if (occurrence.course.id != 0L) occurrence.course.id.toString() else "new-$courseIndex"
                val periodKey = if (occurrence.period.id != 0L) occurrence.period.id.toString() else "new-$periodIndex"
                val identity = "${data.schedule.id}/$courseKey/$periodKey/${occurrence.date}"
                val uid = UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8))
                val description = listOf(occurrence.course.teacher, occurrence.course.note).filter { it.isNotBlank() }.joinToString("\n")
                lines += listOf("BEGIN:VEVENT", "UID:$uid@wakeuppure.local",
                    "DTSTAMP:${utcTime.format(Instant.ofEpochMilli(data.schedule.updatedAt))}Z",
                    "DTSTART:${localTime.format(occurrence.start)}", "DTEND:${localTime.format(occurrence.end)}",
                    "SUMMARY:${escape(occurrence.course.name)}", "LOCATION:${escape(occurrence.classroom)}",
                    "DESCRIPTION:${escape(description)}", "END:VEVENT")
            }
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\")
        .replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n")
        .replace(";", "\\;").replace(",", "\\,")

    private fun fold(line: String): String = buildString {
        var bytes = 0
        var index = 0
        while (index < line.length) {
            val point = line.codePointAt(index)
            val chars = String(Character.toChars(point))
            val size = chars.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 75) { append("\r\n "); bytes = 1 }
            append(chars)
            bytes += size
            index += Character.charCount(point)
        }
    }
}
