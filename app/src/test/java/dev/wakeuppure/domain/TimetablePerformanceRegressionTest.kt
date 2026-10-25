package dev.wakeuppure.domain

import dev.wakeuppure.domain.model.*
import dev.wakeuppure.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureNanoTime

/** Compare optimized queries with the previous implementation, including tie-breaking. */
class TimetablePerformanceRegressionTest {
    private val random = Random(5831)

    private fun sample(count: Int): ScheduleData = ScheduleData(
        Schedule(name = "密集课表", semesterStartDate = "2026-01-05", maxWeeks = 60),
        List(count) { i ->
            CourseWithPeriods(Course(id = i.toLong(), name = "课程 ${i % 9}"), List(4) { j ->
                val first = random.nextInt(1, 61)
                val section = random.nextInt(1, 11)
                CoursePeriod(id = i * 4L + j, dayOfWeek = random.nextInt(1, 8),
                    startSection = section, endSection = section + 1, startWeek = first,
                    endWeek = random.nextInt(first, 61), weekType = WeekType.entries.random(random))
            })
        },
    )

    private fun previousNext(data: ScheduleData, now: LocalDateTime): Occurrence? {
        val end = WeekCalculator.date(data.schedule.semesterStartDate, data.schedule.maxWeeks, 7)
        var day = maxOf(now.toLocalDate(), WeekCalculator.monday(data.schedule.semesterStartDate))
        while (!day.isAfter(end)) {
            CourseFilter.onDate(data, day).firstOrNull { it.start.isAfter(now) }?.let { return it }
            day = day.plusDays(1)
        }
        return null
    }

    private fun previousGhosts(courses: List<CourseWithPeriods>, week: Int): List<Pair<CourseWithPeriods, CoursePeriod>> {
        val all = courses.flatMap { c -> c.periods.map { c to it } }
        val occupied = all.filter { CourseFilter.matches(it.second, week) }.map { it.second }.toMutableList()
        val candidates = all.filterNot { CourseFilter.matches(it.second, week) }.mapNotNull { pair ->
            val nearest = (pair.second.startWeek..pair.second.endWeek).filter { CourseFilter.matches(pair.second, it) }
                .minOfOrNull { abs(it - week) } ?: return@mapNotNull null
            pair to nearest
        }.sortedWith(compareBy({ it.second }, { it.first.first.course.id }, { it.first.second.id }))
        return buildList {
            candidates.forEach { (pair, _) ->
                val p = pair.second
                if (occupied.none { it.dayOfWeek == p.dayOfWeek && it.startSection <= p.endSection && p.startSection <= it.endSection }) {
                    add(pair); occupied.add(p)
                }
            }
        }
    }

    private fun previousLanes(periods: List<CoursePeriod>): Map<Long, Pair<Int, Int>> {
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

    @Test fun optimizedQueriesPreserveDatesParityTiesAndLaneAssignments() {
        repeat(25) {
            val data = sample(40)
            for (week in listOf(-1, 1, 2, 19, 38, 60, 61)) {
                assertEquals(previousGhosts(data.courses, week), TimetableGhosts.select(data.courses, week))
                val now = WeekCalculator.date(data.schedule.semesterStartDate, week, random.nextInt(1, 8)).atTime(8, 0)
                assertEquals(previousNext(data, now), CourseFilter.next(data, now))
            }
            val periods = data.courses.flatMap { it.periods }
            assertEquals(previousLanes(periods), CourseFilter.lanes(periods))
        }
    }

    @Test fun sparseSemesterQueryBenchmark() {
        // A valid but sparse import: the first class is in week 60. No timing threshold in CI.
        for (count in listOf(100, 500, 2000)) {
            val data = sample(count).let { d -> d.copy(courses = d.courses.map { c ->
                c.copy(periods = c.periods.map { it.copy(startWeek = 60, endWeek = 60, weekType = WeekType.ALL) })
            }) }
            val now = LocalDateTime.parse("2026-01-05T08:00:00")
            repeat(3) { previousNext(data, now); CourseFilter.next(data, now) }
            val before = List(7) { measureNanoTime { previousNext(data, now) } }.sorted()[3] / 1_000_000.0
            val after = List(7) { measureNanoTime { CourseFilter.next(data, now) } }.sorted()[3] / 1_000_000.0
            assertEquals(previousNext(data, now), CourseFilter.next(data, now))
            println("NEXT_QUERY courses=$count periods=${count * 4} oldMedianMs=$before newMedianMs=$after")
        }
    }
}
