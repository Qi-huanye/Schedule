package dev.wakeuppure.domain.usecase

import dev.wakeuppure.domain.model.CoursePeriod
import dev.wakeuppure.domain.model.WeekType
import java.time.temporal.ChronoUnit

object SemesterAdjustment {
    fun dateOffset(oldStart: String, newStart: String): Int =
        ChronoUnit.WEEKS.between(WeekCalculator.monday(newStart), WeekCalculator.monday(oldStart)).toInt()

    fun adjust(periods: List<CoursePeriod>, oldStart: String, newStart: String,
        preserveDates: Boolean, weekOffset: Int): List<CoursePeriod> {
        val offset = (if (preserveDates) dateOffset(oldStart, newStart) else 0) + weekOffset
        return periods.map { period ->
            val start = period.startWeek + offset
            val end = period.endWeek + offset
            require(start in 1..60 && end in start..60) { "调整后课程超出第 1–60 周，请检查开学日期或首课周次" }
            period.copy(startWeek = start, endWeek = end, weekType = if (offset % 2 == 0) period.weekType else when (period.weekType) {
                WeekType.ODD -> WeekType.EVEN
                WeekType.EVEN -> WeekType.ODD
                WeekType.ALL -> WeekType.ALL
            })
        }
    }
}
