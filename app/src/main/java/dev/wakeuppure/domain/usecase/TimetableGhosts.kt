package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.*
import kotlin.math.abs
import java.util.BitSet

/** Other-week hints never cover a scheduled class or overlap another hint. */
object TimetableGhosts {
    fun select(courses: List<CourseWithPeriods>, week: Int): List<Pair<CourseWithPeriods, CoursePeriod>> {
        val all = courses.flatMap { course -> course.periods.map { course to it } }
        val occupied = Array(8) { BitSet() }
        all.forEach { (_, p) ->
            if (CourseFilter.matches(p, week)) occupied[p.dayOfWeek].set(p.startSection, p.endSection + 1)
        }
        val candidates = all.filterNot { CourseFilter.matches(it.second, week) }.mapNotNull { pair ->
            val p = pair.second
            val blocked = occupied[p.dayOfWeek].nextSetBit(p.startSection)
            if (blocked >= 0 && blocked <= p.endSection) return@mapNotNull null
            var first = maxOf(1, p.startWeek)
            if (p.weekType != WeekType.ALL && !CourseFilter.matches(p, first)) first++
            if (first > p.endWeek) return@mapNotNull null
            val step = if (p.weekType == WeekType.ALL) 1 else 2
            val last = first + (p.endWeek - first) / step * step
            val nearest = week.coerceIn(first, last)
            pair to (abs(nearest - week) + if (step == 2 && (nearest - first) % 2 != 0) 1 else 0)
        }.sortedWith(compareBy({ it.second }, { it.first.first.course.id }, { it.first.second.id }))
        return buildList {
            candidates.forEach { (pair, _) ->
                val p = pair.second
                val blocked = occupied[p.dayOfWeek].nextSetBit(p.startSection)
                if (blocked < 0 || blocked > p.endSection) {
                    add(pair)
                    occupied[p.dayOfWeek].set(p.startSection, p.endSection + 1)
                }
            }
        }
    }
}
