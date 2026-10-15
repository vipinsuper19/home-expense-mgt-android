package com.dhama.homefinance.util

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DueDatesTest {
    private fun due(day: Int, ym: String) = DueDates.monthlyDueDate(day, YearMonth.parse(ym))

    @Test fun existingDayIsUnchanged() {
        assertEquals(LocalDate.of(2026, 3, 15), due(15, "2026-03"))
        assertEquals(LocalDate.of(2026, 1, 31), due(31, "2026-01"))
        assertEquals(LocalDate.of(2026, 2, 1), due(1, "2026-02"))
    }

    @Test fun missingDayClampsToLastDay() {
        assertEquals(LocalDate.of(2026, 2, 28), due(31, "2026-02"))
        assertEquals(LocalDate.of(2026, 2, 28), due(29, "2026-02"))
        assertEquals(LocalDate.of(2026, 4, 30), due(31, "2026-04"))
        assertEquals(LocalDate.of(2026, 9, 30), due(31, "2026-09"))
    }

    @Test fun leapYears() {
        assertEquals(LocalDate.of(2028, 2, 29), due(31, "2028-02"))
        assertEquals(LocalDate.of(2028, 2, 29), due(29, "2028-02"))
        assertEquals(LocalDate.of(2100, 2, 28), due(29, "2100-02")) // century, not leap
        assertEquals(LocalDate.of(2000, 2, 29), due(30, "2000-02")) // 400-year rule
    }

    @Test fun clampDoesNotDriftAcrossMonths() {
        // A 31st schedule must come back to the 31st after a short month.
        val dates = (1..12).map { due(31, "2026-%02d".format(it)).dayOfMonth }
        assertEquals(listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31), dates)
    }

    @Test(expected = IllegalArgumentException::class) fun zeroRejected() { due(0, "2026-01") }
    @Test(expected = IllegalArgumentException::class) fun thirtyTwoRejected() { due(32, "2026-01") }
}
