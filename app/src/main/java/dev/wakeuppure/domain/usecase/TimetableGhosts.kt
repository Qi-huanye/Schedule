package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.*
import kotlin.math.abs

/** Other-week hints never cover a scheduled class or overlap another hint. */
object TimetableGhosts {
    fun select(courses: List<CourseWithPeriods>, week: Int): List<Pair<CourseWithPeriods, CoursePeriod>> {
        val all = courses.flatMap { course -> course.periods.map { course to it } }
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
                    add(pair)
                    occupied.add(p)
                }
            }
        }
    }
}
