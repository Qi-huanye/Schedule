package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.*
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.util.IdentityHashMap

/** Screen-independent calculations. Colours, today's date and the current time stay live. */
@Serializable
data class TimetableWeekLayout(
    val week: Int,
    val month: String,
    val slots: List<TimeSlot>,
    val days: List<TimetableDayLayout>,
)

@Serializable
data class TimetableDayLayout(
    val dayOfWeek: Int,
    val epochDay: Long,
    val dayOfMonth: Int,
    val cards: List<TimetableCardLayout>,
)

@Serializable
data class TimetableCardLayout(
    val courseIndex: Int,
    val periodIndex: Int,
    val ghost: Boolean,
    val lane: Int,
    val laneCount: Int,
    val sectionIndex: Int,
    val sectionSpan: Int,
    val startSecond: Int? = null,
    val endSecond: Int? = null,
)

object TimetableLayout {
    fun calculate(data: ScheduleData, week: Int): TimetableWeekLayout {
        val visible = data.courses.flatMap { course ->
            course.periods.filter { CourseFilter.matches(it, week) }.map { course to it }
        }
        val ghosts = TimetableGhosts.select(data.courses, week)
        val lanes = CourseFilter.lanes(visible.map { it.second })
        // Retain the old ghost/visible draw order, including overlapping hint cards.
        val byDay = (ghosts + visible).groupBy { it.second.dayOfWeek }
        val courseIndexes = IdentityHashMap<CourseWithPeriods, Int>()
        val periodIndexes = IdentityHashMap<CoursePeriod, Int>()
        data.courses.forEachIndexed { courseIndex, course ->
            courseIndexes[course] = courseIndex
            course.periods.forEachIndexed { periodIndex, period -> periodIndexes[period] = periodIndex }
        }
        val slots = data.timeSlots.sortedBy { it.section }
        val sectionIndexes = slots.mapIndexed { index, slot -> slot.section to index }.toMap()
        val starts = slots.associate { it.section to LocalTime.parse(it.startTime).toSecondOfDay() }
        val ends = slots.associate { it.section to LocalTime.parse(it.endTime).toSecondOfDay() }
        val days = (0..6).map { (data.schedule.firstDay - 1 + it) % 7 + 1 }
            .filter { data.schedule.showWeekend || it <= 5 }
        val dates = days.associateWith { WeekCalculator.date(data.schedule.semesterStartDate, week, it) }
        val first = dates.getValue(days.first())
        val last = dates.getValue(days.last())
        return TimetableWeekLayout(
            week = week,
            month = if (first.monthValue == last.monthValue) "${first.monthValue}" else "${first.monthValue}/${last.monthValue}",
            slots = slots,
            days = days.map { day ->
                val date = dates.getValue(day)
                TimetableDayLayout(day, date.toEpochDay(), date.dayOfMonth, byDay[day].orEmpty().map { (course, period) ->
                    val ghost = !CourseFilter.matches(period, week)
                    val (lane, count) = if (ghost) 0 to 1 else lanes.getValue(period.id)
                    val occurs = !ghost && week in 1..data.schedule.maxWeeks
                    TimetableCardLayout(
                        courseIndex = courseIndexes.getValue(course),
                        periodIndex = periodIndexes.getValue(period),
                        ghost = ghost,
                        lane = lane,
                        laneCount = count,
                        sectionIndex = sectionIndexes[period.startSection] ?: 0,
                        sectionSpan = period.endSection - period.startSection + 1,
                        startSecond = starts[period.startSection].takeIf { occurs },
                        endSecond = ends[period.endSection].takeIf { occurs },
                    )
                })
            },
        )
    }
}
