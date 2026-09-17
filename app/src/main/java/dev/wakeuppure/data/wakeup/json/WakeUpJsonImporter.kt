package dev.wakeuppure.data.wakeup.json

import dev.wakeuppure.domain.model.*
import kotlinx.serialization.json.*
import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Boundary DTO: no WakeUp field names escape this parser. */
private data class WakeUpCourseDto(val name: String, val teacher: String, val room: String,
    val day: Int, val node: Int, val step: Int, val first: Int, val last: Int,
    val type: Int, val color: String, val note: String)

object WakeUpJsonImporter {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): ScheduleData {
        require(text.toByteArray().size <= 5_000_000) { "文件过大，请选择小于 5 MB 的课表" }
        try {
            val clean = text.trim().removePrefix("\uFEFF")
            val root = runCatching { json.parseToJsonElement(clean) }.getOrNull()
            if (root is JsonObject && root["shareData"] is JsonPrimitive) return parse(root.getValue("shareData").jsonPrimitive.content)
            if (root is JsonObject) return envelope(root)
            if (root is JsonArray) return envelope(buildJsonObject { put("courses", root) })
            val rows = clean.lineSequence().filter { it.isNotBlank() }.map { json.parseToJsonElement(it) }.toList()
            require(rows.size >= 5) { "未找到完整的 WakeUp 课表数据" }
            val bases = rows[3].jsonArray.associateBy { it.jsonObject.string("id") }
            val courses = rows[4].jsonArray.map { detail ->
                val d = detail.jsonObject
                val base = bases[d.string("id")]?.jsonObject ?: bases[d.string("courseId")]?.jsonObject
                    ?: error("课程详情引用了不存在的课程")
                JsonObject(base + d + mapOf("name" to JsonPrimitive(base.string("courseName", "name"))))
            }
            val required = courses.maxOfOrNull { it.int("startNode", 1) + it.int("step", 1) - 1 } ?: 1
            val usableSlots = mutableListOf<JsonElement>()
            var previousEnd: LocalTime? = null
            for (slot in rows[1].jsonArray.sortedBy { it.jsonObject.int("node", 0) }) {
                val o = slot.jsonObject
                val valid = runCatching {
                    val a = parseTime(o.string("startTime", "start"))
                    val b = parseTime(o.string("endTime", "end"))
                    require(o.int("node", 0) == usableSlots.size + 1 && a < b && (previousEnd == null || a >= previousEnd))
                    previousEnd = b
                }.isSuccess
                if (!valid || usableSlots.size == 30) break
                usableSlots += slot
            }
            require(usableSlots.size >= required) { "课程引用的作息时间无效" }
            return envelope(buildJsonObject {
                put("tableInfo", JsonObject(rows[2].jsonObject + ("timeList" to JsonArray(usableSlots))))
                put("courses", JsonArray(courses))
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("课表格式不正确，请检查日期、课程及节次数据", e)
        }
    }

    private fun envelope(root: JsonObject): ScheduleData {
        val info = (root["tableInfo"] as? JsonObject) ?: root
        val encoded = root["courseDetailJson"]?.jsonPrimitive?.content
        val array = if (encoded != null) json.parseToJsonElement(
            if (encoded.trim().startsWith("[")) encoded else URLDecoder.decode(encoded, "UTF-8")).jsonArray
            else (root["courses"] ?: info["courses"] ?: error("没有 courses" )).jsonArray
        val dtos = array.map { item ->
            val o = item.jsonObject
            WakeUpCourseDto(o.string("name", "courseName"), o.string("teacher"), o.string("position", "room", "classroom"),
                o.int("day", 1), o.int("startNode", 1), o.int("step", 1), o.int("startWeek", 1), o.int("endWeek", 16),
                o.int("type", 0), o.string("color").ifBlank { "#C1E8DE" }, o.string("note"))
        }.distinct()
        val start = root.string("startDate").ifBlank { info.string("startDate") }.ifBlank { LocalDate.now().toString() }
        val startDate = LocalDate.parse(start, DateTimeFormatter.ofPattern("uuuu-M-d")
            .withResolverStyle(ResolverStyle.STRICT)).toString()
        val maxWeeks = maxOf(info.int("maxWeek", 20), dtos.maxOfOrNull { it.last } ?: 1)
        require(maxWeeks in 1..60)
        val courses = dtos.groupBy { listOf(it.name, it.teacher, it.color, it.note) }.values.map { group ->
            val first = group.first()
            require(first.name.isNotBlank())
            CourseWithPeriods(Course(name = first.name, teacher = first.teacher, color = normalizeColor(first.color), note = first.note),
                group.map { d ->
                    require(d.day in 1..7 && d.node in 1..30 && d.step in 1..30 && d.node + d.step - 1 <= 30)
                    require(d.first in 1..maxWeeks && d.last in d.first..maxWeeks && d.type in 0..2)
                    CoursePeriod(dayOfWeek = d.day, startSection = d.node, endSection = d.node + d.step - 1,
                        startWeek = d.first, endWeek = d.last, weekType = WeekType.entries[d.type], classroom = d.room)
                })
        }
        val rawSlots = info["timeList"] ?: info["time"]?.let { t ->
            if (t is JsonPrimitive) json.parseToJsonElement(t.content) else t
        }
        val slots = (rawSlots as? JsonArray)?.mapIndexed { i, element ->
            val o = element.jsonObject
            val startTime = o.string("startTime", "start")
            val endTime = o.string("endTime", "end")
            val a = parseTime(startTime); val b = parseTime(endTime)
            require(a < b)
            TimeSlot(o.int("node", i + 1), a.toString(), b.toString())
        }?.sortedBy { it.section } ?: defaultTimeSlots()
        val required = courses.flatMap { it.periods }.maxOfOrNull { it.endSection } ?: 1
        require(slots.map { it.section }.containsAll((1..required).toList())) { "作息表缺少课程对应节次" }
        return ScheduleData(Schedule(name = root.string("name").ifBlank { info.string("name", "tableName") }.ifBlank { "导入的课表" },
            semesterStartDate = startDate, maxWeeks = maxWeeks), courses, slots)
    }

    private fun parseTime(value: String): LocalTime {
        val parts = value.split(':')
        return if (parts.size == 2) LocalTime.of(parts[0].toInt(), parts[1].toInt()) else LocalTime.parse(value)
    }
    private fun normalizeColor(value: String): String = when {
        Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(value) -> value
        value.toLongOrNull() != null -> "#%08X".format(value.toLong() and 0xffffffffL)
        else -> "#C1E8DE"
    }
    private fun JsonObject.string(vararg keys: String): String = keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull } ?: ""
    private fun JsonObject.int(key: String, fallback: Int): Int {
        if (!containsKey(key)) return fallback
        return requireNotNull((this[key] as? JsonPrimitive)?.intOrNull) { "数值字段不正确" }
    }
}
