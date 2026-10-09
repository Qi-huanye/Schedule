package dev.wakeuppure.ui

import dev.wakeuppure.domain.model.CoursePeriod
import dev.wakeuppure.domain.model.WeekType
import dev.wakeuppure.ui.components.CellState
import dev.wakeuppure.ui.components.RangePick
import dev.wakeuppure.ui.components.rangeState
import dev.wakeuppure.ui.course.periodWeeks
import dev.wakeuppure.ui.today.dayLabel
import dev.wakeuppure.ui.today.relativeStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class UiLabelsTest {
    @Test fun rangePickClosesOnEitherSideOfTheFirstTap() {
        val first = RangePick(3, 18).tap(5)
        assertEquals(RangePick(5, 5, 5), first)
        assertEquals(RangePick(5, 9), first.tap(9))
        assertEquals(RangePick(2, 5), first.tap(2))
        assertEquals(RangePick(7, 7, 7), first.tap(9).tap(7))
    }

    @Test fun rangeStateKeepsEndsVisibleAndFiltersInside() {
        val odd = { w: Int -> w % 2 == 1 }
        assertEquals(CellState.EDGE, rangeState(18, 3, 18, odd))
        assertEquals(CellState.INSIDE, rangeState(5, 3, 18, odd))
        assertEquals(CellState.NONE, rangeState(4, 3, 18, odd))
        assertEquals(CellState.NONE, rangeState(19, 3, 18))
    }

    @Test fun relativeStartUsesMinutesThenHours() {
        val now = LocalDateTime.of(2026, 10, 9, 8, 52)
        assertEquals("即将开始", relativeStart(now, now.plusSeconds(20)))
        assertEquals("20 分钟后", relativeStart(now, now.plusMinutes(20)))
        assertEquals("5 小时后", relativeStart(now, LocalDateTime.of(2026, 10, 9, 14, 0)))
    }

    @Test fun dayLabelAvoidsRawDates() {
        val today = LocalDate.of(2026, 10, 9)
        assertEquals("明天 08:00", dayLabel(today, LocalDateTime.of(2026, 10, 10, 8, 0)))
        assertEquals("周日 10:00", dayLabel(today, LocalDateTime.of(2026, 10, 11, 10, 0)))
        assertEquals("10月20日 08:00", dayLabel(today, LocalDateTime.of(2026, 10, 20, 8, 0)))
    }

    @Test fun periodSummaryShowsOnlyDifferences() {
        assertEquals("第 3–18 周", periodWeeks(CoursePeriod(startWeek = 3, endWeek = 18, classroom = "A101"), "A101"))
        assertEquals("第 3–17 周 · 单周 · 实验楼 101",
            periodWeeks(CoursePeriod(startWeek = 3, endWeek = 17, weekType = WeekType.ODD, classroom = "实验楼 101"), "A101"))
    }
}
