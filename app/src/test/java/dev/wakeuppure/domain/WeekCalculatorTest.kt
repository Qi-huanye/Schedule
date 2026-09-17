package dev.wakeuppure.domain

import dev.wakeuppure.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeekCalculatorTest {
    @Test fun mondayNormalizationAndYearBoundary() {
        assertEquals(1, WeekCalculator.week("2025-12-31", LocalDate.parse("2026-01-04")))
        assertEquals(2, WeekCalculator.week("2025-12-31", LocalDate.parse("2026-01-05")))
        assertEquals(0, WeekCalculator.week("2025-12-31", LocalDate.parse("2025-12-28")))
    }
    @Test fun finalWeek() {
        assertEquals(20, WeekCalculator.week("2026-01-05", LocalDate.parse("2026-05-24")))
        assertEquals(21, WeekCalculator.week("2026-01-05", LocalDate.parse("2026-05-25")))
    }
}
