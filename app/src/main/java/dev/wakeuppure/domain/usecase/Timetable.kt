package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.*
import java.time.*
import java.time.temporal.ChronoUnit

object WeekCalculator {
    fun monday(start: String): LocalDate = LocalDate.parse(start).with(DayOfWeek.MONDAY)
    fun week(start: String, date: LocalDate = LocalDate.now()): Int =
        Math.floorDiv(ChronoUnit.DAYS.between(monday(start), date), 7L).toInt() + 1
    fun date(start: String, week: Int, day: Int): LocalDate =
        monday(start).plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong())
}

data class Occurrence(val course: Course, val period: CoursePeriod,
    val date: LocalDate, val start: LocalDateTime, val end: LocalDateTime) {
    val classroom: String get() = period.classroom.ifBlank { course.classroom }
}

object CourseFilter {
    fun matches(p: CoursePeriod, week: Int): Boolean = week >= 1 && week in p.startWeek..p.endWeek &&
        when (p.weekType) { WeekType.ALL -> true; WeekType.ODD -> week % 2 == 1; WeekType.EVEN -> week % 2 == 0 }

    fun onDate(data: ScheduleData, date: LocalDate): List<Occurrence> {
        val week = WeekCalculator.week(data.schedule.semesterStartDate, date)
        if (week !in 1..data.schedule.maxWeeks) return emptyList()
        return data.courses.flatMap { c -> c.periods.filter { it.dayOfWeek == date.dayOfWeek.value && matches(it, week) }
            .mapNotNull { p ->
                val start = data.timeSlots.find { it.section == p.startSection }?.startTime ?: return@mapNotNull null
                val end = data.timeSlots.find { it.section == p.endSection }?.endTime ?: return@mapNotNull null
                Occurrence(c.course, p, date, date.atTime(LocalTime.parse(start)), date.atTime(LocalTime.parse(end)))
            } }.sortedWith(compareBy({ it.start }, { it.course.name }))
    }

    fun next(data: ScheduleData, now: LocalDateTime): Occurrence? {
        val end = WeekCalculator.date(data.schedule.semesterStartDate, data.schedule.maxWeeks, 7)
        var day = maxOf(now.toLocalDate(), WeekCalculator.monday(data.schedule.semesterStartDate))
        while (!day.isAfter(end)) {
            onDate(data, day).firstOrNull { it.start.isAfter(now) }?.let { return it }
            day = day.plusDays(1)
        }
        return null
    }

    // Partition each connected interval cluster; adjacent non-overlapping sections reset widths.
    fun lanes(periods: List<CoursePeriod>): Map<Long, Pair<Int, Int>> {
        val result = mutableMapOf<Long, Pair<Int, Int>>()
        periods.groupBy { it.dayOfWeek }.values.forEach { day ->
            val cluster = mutableListOf<CoursePeriod>()
            fun flush() {
                val ends = mutableListOf<Int>()
                val assigned = cluster.map { p ->
                    var lane = ends.indexOfFirst { it < p.startSection }
                    if (lane < 0) { lane = ends.size; ends.add(p.endSection) } else ends[lane] = p.endSection
                    p.id to lane
                }
                assigned.forEach { (id, lane) -> result[id] = lane to ends.size }
                cluster.clear()
            }
            var end = 0
            day.sortedWith(compareBy({ it.startSection }, { it.endSection }, { it.id })).forEach { p ->
                if (cluster.isNotEmpty() && p.startSection > end) { flush(); end = 0 }
                cluster.add(p); end = maxOf(end, p.endSection)
            }
            flush()
        }
        return result
    }
}
