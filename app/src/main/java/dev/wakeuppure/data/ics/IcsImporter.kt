package dev.wakeuppure.data.ics

import dev.wakeuppure.domain.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A bounded, strict importer for timed weekly course calendars. */
object IcsImporter {
    private data class Property(val name: String, val params: Map<String, String>, val value: String)
    private data class Event(val name: String, val room: String, val note: String,
        val start: ZonedDateTime, val end: ZonedDateTime, val dates: List<ZonedDateTime>, val sections: IntRange?)
    private val stamp = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss")
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(text: String): ScheduleData {
        try { return read(text) }
        catch (e: IllegalArgumentException) { throw IllegalArgumentException("ICS 导入失败：${e.message ?: "数据格式不正确"}", e) }
        catch (e: Exception) { throw IllegalArgumentException("ICS 导入失败：日期、时区或文件结构不正确", e) }
    }

    private fun read(text: String): ScheduleData {
        require(text.toByteArray().size <= 5_000_000) { "文件超过 5 MB" }
        val lines = text.trim().removePrefix("\uFEFF").replace("\r\n", "\n")
            .replace(Regex("\n[ \t]"), "").lines()
        require(lines.first() == "BEGIN:VCALENDAR" && lines.last() == "END:VCALENDAR") { "不是完整的日历文件" }
        val records = mutableListOf<List<Property>>()
        var event: MutableList<Property>? = null
        var nesting = 0
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> { require(event == null); event = mutableListOf(); nesting = 0 }
                line == "END:VEVENT" -> { records += requireNotNull(event).toList(); event = null }
                event != null && line.startsWith("BEGIN:") -> nesting++
                event != null && line.startsWith("END:") -> nesting--
                event != null && nesting == 0 && ':' in line -> {
                    val header = line.substringBefore(':').split(';')
                    event.add(Property(header.first().uppercase(), header.drop(1).associate {
                        it.substringBefore('=').uppercase() to it.substringAfter('=').trim('"')
                    }, line.substringAfter(':')))
                }
            }
        }
        require(records.isNotEmpty() && records.size <= 5000) { "没有课程，或课程记录超过 5000 条" }
        val events = records.map { properties ->
            fun prop(name: String) = properties.firstOrNull { it.name == name }
            require(prop("RECURRENCE-ID") == null) { "暂不支持单次改期记录，请先在日历中展开重复事件" }
            val start = date(requireNotNull(prop("DTSTART")) { "课程缺少开始时间" })
            val end = date(requireNotNull(prop("DTEND")) { "课程缺少结束时间" }, start.zone)
            require(start.toLocalDate() == end.toLocalDate() && start < end) { "暂不支持全天或跨午夜课程" }
            val rule = prop("RRULE")?.value?.split(';')?.associate { it.substringBefore('=') to it.substringAfter('=') }
            val dates = mutableListOf<ZonedDateTime>()
            if (rule == null) dates += start else {
                require(rule["FREQ"] == "WEEKLY") { "目前支持每周或隔周重复，暂不支持其他重复频率" }
                require(rule.keys.all { it in setOf("FREQ", "UNTIL", "COUNT", "INTERVAL", "BYDAY", "WKST") }) { "包含暂不支持的重复规则" }
                val interval = rule["INTERVAL"]?.toInt() ?: 1
                val count = rule["COUNT"]?.toInt() ?: 420
                require(interval in 1..60 && count in 1..420 && (rule.containsKey("UNTIL") || rule.containsKey("COUNT"))) { "重复规则必须有有效的结束日期或次数" }
                val until = rule["UNTIL"]?.let { date(Property("UNTIL", emptyMap(), it), start.zone) }
                val weekdays = listOf("MO","TU","WE","TH","FR","SA","SU")
                val days = rule["BYDAY"]?.split(',')?.map { weekdays.indexOf(it) + 1 } ?: listOf(start.dayOfWeek.value)
                require(days.all { it in 1..7 }) { "不支持带序号的 BYDAY" }
                val weekStart = rule["WKST"]?.let { weekdays.indexOf(it) + 1 } ?: 1
                require(weekStart in 1..7)
                val anchor = start.toLocalDate().minusDays(((start.dayOfWeek.value - weekStart + 7) % 7).toLong())
                for (offset in 0..420) {
                    val next = start.plusDays(offset.toLong())
                    if (until != null && next.toInstant() > until.toInstant()) break
                    val week = ChronoUnit.DAYS.between(anchor, next.toLocalDate()) / 7
                    if (week % interval == 0L && next.dayOfWeek.value in days) dates += next
                    if (dates.size >= count) break
                    require(offset < 420) { "课程日期跨度超过 60 周" }
                }
            }
            properties.filter { it.name == "RDATE" }.forEach { p -> p.value.split(',').forEach { dates += date(p.copy(value = it), start.zone) } }
            val excluded = properties.filter { it.name == "EXDATE" }.flatMap { p -> p.value.split(',').map { date(p.copy(value = it), start.zone).toInstant() } }.toSet()
            val note = unescape(prop("DESCRIPTION")?.value.orEmpty())
            val match = Regex("第\\s*(\\d+)\\s*[-–]\\s*(\\d+)\\s*节").find(note)
            val sections = match?.let { it.groupValues[1].toInt()..it.groupValues[2].toInt() }
            require(sections == null || (sections.first in 1..30 && sections.last in sections.first..30)) { "节次范围无效" }
            val name = unescape(prop("SUMMARY")?.value.orEmpty())
            require(name.isNotBlank()) { "课程名称为空" }
            Event(name, unescape(prop("LOCATION")?.value.orEmpty()), note, start, end,
                dates.filter { it.toInstant() !in excluded }.distinct().sorted(), sections)
        }.filter { it.dates.isNotEmpty() }
        require(events.isNotEmpty()) { "日历中没有有效上课日期" }
        val base = events.minOf { it.dates.first().toLocalDate() }.with(DayOfWeek.MONDAY)
        val maxWeeks = (ChronoUnit.DAYS.between(base, events.maxOf { it.dates.last().toLocalDate() }) / 7 + 1).toInt()
        require(maxWeeks in 1..60) { "课程日期跨度超过 60 周" }

        // Preserve WakeUp's section numbers when provided. Anchor every known
        // event boundary; infer only interior breaks, never event start/end.
        val useSections = events.all { it.sections != null }
        val slots: List<TimeSlot>
        val ranges: Map<Event, IntRange>
        if (useSections) {
            val size = events.maxOf { it.sections!!.last }
            val anchors = sortedMapOf<Int, Int>()
            fun anchor(index: Int, time: LocalTime) {
                val minute = time.hour * 60 + time.minute
                require(anchors[index] == null || anchors[index] == minute) { "同一节次存在不同作息时间，无法无损合并" }
                anchors[index] = minute
            }
            events.forEach { anchor((it.sections!!.first - 1) * 2, it.start.toLocalTime()); anchor(it.sections.last * 2 - 1, it.end.toLocalTime()) }
            val first = anchors.firstKey()
            if (first > 0) {
                val inferred = anchors.getValue(first) - first * 25
                require(inferred >= 0) { "缺少前面节次的作息时间" }
                anchors[0] = inferred
            }
            val values = IntArray(size * 2)
            anchors.entries.zipWithNext().forEach { (a, b) ->
                require(b.value > a.value) { "作息时间有重叠或顺序错误" }
                val weights = (a.key until b.key).map { if (it % 2 == 0) 45 else 10 }
                val total = weights.sum(); var sum = 0
                for (i in a.key until b.key) { values[i] = a.value + (b.value-a.value)*sum/total; sum += weights[i-a.key] }
                values[b.key] = b.value
            }
            slots = (1..size).map { n ->
                val a = values[(n-1)*2]; val b = values[n*2-1]
                require(a < b) { "无法确定节次时间" }
                TimeSlot(n, LocalTime.of(a/60,a%60).format(hm), LocalTime.of(b/60,b%60).format(hm))
            }
            ranges = events.associateWith { it.sections!! }
        } else {
            val times = events.flatMap { listOf(it.start.toLocalTime(), it.end.toLocalTime()) }.distinct().sorted()
            require(times.size in 2..31) { "独立时间段超过 30 节" }
            slots = times.zipWithNext().mapIndexed { i, (a,b) -> TimeSlot(i+1,a.format(hm),b.format(hm)) }
            ranges = events.associateWith { (times.indexOf(it.start.toLocalTime())+1)..times.indexOf(it.end.toLocalTime()) }
        }
        val courses = events.groupBy { Triple(it.name, it.room, it.note) }.entries.mapIndexed { i, (key, group) ->
            val periods = group.flatMap { e -> e.dates.map { day ->
                val week = (ChronoUnit.DAYS.between(base, day.toLocalDate()) / 7 + 1).toInt()
                CoursePeriod(dayOfWeek=day.dayOfWeek.value, startSection=ranges.getValue(e).first,
                    endSection=ranges.getValue(e).last,startWeek=week,endWeek=week,classroom=e.room)
            } }.distinct()
            CourseWithPeriods(Course(name=key.first,classroom=key.second,note=key.third,
                color=listOf("#C1E8DE","#BFDDF1","#E4D5F3","#F3D3D2","#F4E3B5","#CFE4BD")[i%6]),periods)
        }
        return ScheduleData(Schedule(name="导入的 ICS 课表",semesterStartDate=base.toString(),maxWeeks=maxWeeks),courses,slots)
    }

    private fun date(p: Property, fallback: ZoneId = ZoneId.systemDefault()): ZonedDateTime {
        require(p.value.length >= 15 && p.params["VALUE"] != "DATE") { "暂不支持全天课程" }
        val utc = p.value.endsWith('Z')
        val zone = if (utc) ZoneOffset.UTC else p.params["TZID"]?.let(ZoneId::of) ?: fallback
        return LocalDateTime.parse(p.value.removeSuffix("Z"), stamp).atZone(zone)
    }
    private fun unescape(value: String): String = Regex("\\\\(.)").replace(value) {
        when (it.groupValues[1]) { "n", "N" -> "\n"; else -> it.groupValues[1] }
    }
}
