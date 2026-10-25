package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.PriorityQueue
import java.util.TreeSet

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
    val teacher: String get() = period.teacher.ifBlank { course.teacher }
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
        val monday = WeekCalculator.monday(data.schedule.semesterStartDate)
        val currentWeek = WeekCalculator.week(data.schedule.semesterStartDate, now.toLocalDate())
        val slots = data.timeSlots.associateBy { it.section }
        var best: Occurrence? = null
        for (course in data.courses) for (period in course.periods) {
            val start = slots[period.startSection]?.startTime?.let(LocalTime::parse) ?: continue
            val end = slots[period.endSection]?.endTime?.let(LocalTime::parse) ?: continue
            var week = maxOf(1, currentWeek, period.startWeek)
            val lastWeek = minOf(period.endWeek, data.schedule.maxWeeks)
            if (week > lastWeek) continue
            var date = monday.plusWeeks((week - 1).toLong()).plusDays((period.dayOfWeek - 1).toLong())
            if (!date.atTime(start).isAfter(now)) week++
            if (period.weekType != WeekType.ALL && !matches(period, week)) week++
            if (week > lastWeek) continue
            date = monday.plusWeeks((week - 1).toLong()).plusDays((period.dayOfWeek - 1).toLong())
            val candidate = Occurrence(course.course, period, date, date.atTime(start), date.atTime(end))
            val previous = best
            if (previous == null || candidate.start < previous.start ||
                (candidate.start == previous.start && candidate.course.name < previous.course.name)) best = candidate
        }
        return best
    }

    // Partition each connected interval cluster; adjacent non-overlapping sections reset widths.
    fun lanes(periods: List<CoursePeriod>): Map<Long, Pair<Int, Int>> {
        val result = mutableMapOf<Long, Pair<Int, Int>>()
        periods.groupBy { it.dayOfWeek }.values.forEach { day ->
            val cluster = mutableListOf<CoursePeriod>()
            fun flush() {
                val active = PriorityQueue(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
                val free = TreeSet<Int>()
                var laneCount = 0
                val assigned = cluster.map { p ->
                    while (true) {
                        val earliest = active.peek() ?: break
                        if (earliest.first >= p.startSection) break
                        free.add(active.remove().second)
                    }
                    val lane = if (free.isEmpty()) laneCount++ else free.pollFirst()
                    active.add(p.endSection to lane)
                    p.id to lane
                }
                assigned.forEach { (id, lane) -> result[id] = lane to laneCount }
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
