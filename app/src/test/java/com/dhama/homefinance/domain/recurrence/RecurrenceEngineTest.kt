package com.dhama.homefinance.domain.recurrence

import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleAmountChange
import com.dhama.homefinance.data.model.SchedulePause
import com.dhama.homefinance.data.model.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceEngineTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun schedule(
        frequency: Frequency,
        start: String,
        interval: Int = 1,
        end: String? = null,
        dayOfMonth: Int? = null,
        dayOfWeek: Int? = null,
        monthOfYear: Int? = null,
        amount: Long = 100
    ) = RecurringSchedule(
        id = "s", name = "s", type = TransactionType.EXPENSE, categoryId = "c", amountPaise = amount,
        frequency = frequency, interval = interval, startDate = d(start), endDate = end?.let(::d),
        dayOfMonth = dayOfMonth, dayOfWeek = dayOfWeek, monthOfYear = monthOfYear
    )

    private fun dates(s: RecurringSchedule, from: String, to: String) =
        RecurrenceEngine.dueDates(s, d(from), d(to)).map { it.toString() }

    // --- DAILY ---
    @Test fun dailyEveryDayInclusiveBounds() {
        val s = schedule(Frequency.DAILY, "2026-10-01")
        assertEquals(listOf("2026-10-30", "2026-10-31", "2026-11-01"), dates(s, "2026-10-30", "2026-11-01"))
    }

    @Test fun dailyIntervalStaysOnAnchorEvenWhenWindowStartsLate() {
        val s = schedule(Frequency.DAILY, "2026-10-01", interval = 3)
        assertEquals(listOf("2026-10-10", "2026-10-13"), dates(s, "2026-10-09", "2026-10-14"))
        assertEquals(listOf("2026-10-01"), dates(s, "2026-09-01", "2026-10-02"))
    }

    // --- WEEKLY ---
    @Test fun weeklyFirstOnOrAfterStart() {
        // 2026-10-15 is a Thursday; Mondays from then.
        val s = schedule(Frequency.WEEKLY, "2026-10-15", dayOfWeek = DayOfWeek.MONDAY.value)
        assertEquals(listOf("2026-10-19", "2026-10-26", "2026-11-02"), dates(s, "2026-10-01", "2026-11-08"))
        val sameDay = schedule(Frequency.WEEKLY, "2026-10-15", dayOfWeek = DayOfWeek.THURSDAY.value)
        assertEquals("2026-10-15", dates(sameDay, "2026-10-15", "2026-10-15").single())
    }

    @Test fun biweeklyAnchored() {
        val s = schedule(Frequency.WEEKLY, "2026-01-05", interval = 2, dayOfWeek = 1)
        assertEquals(listOf("2026-03-02", "2026-03-16", "2026-03-30"), dates(s, "2026-03-01", "2026-03-31"))
    }

    // --- MONTHLY ---
    @Test fun monthly31stClampsWithoutDrift() {
        val s = schedule(Frequency.MONTHLY, "2026-01-31", dayOfMonth = 31)
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30"),
            dates(s, "2026-01-01", "2026-06-30")
        )
    }

    @Test fun monthlyLeapYearFebruary() {
        val s = schedule(Frequency.MONTHLY, "2027-12-31", dayOfMonth = 31)
        assertEquals(listOf("2028-01-31", "2028-02-29", "2028-03-31"), dates(s, "2028-01-01", "2028-03-31"))
        val s30 = schedule(Frequency.MONTHLY, "2028-01-30", dayOfMonth = 30)
        assertEquals(listOf("2028-02-29", "2029-02-28"), dates(s30, "2028-02-01", "2029-02-28").filter { it.contains("-02-") })
    }

    @Test fun monthlyDayBeforeStartBeginsNextMonth() {
        val s = schedule(Frequency.MONTHLY, "2026-10-16", dayOfMonth = 15)
        assertEquals(listOf("2026-11-15", "2026-12-15"), dates(s, "2026-10-01", "2026-12-31"))
    }

    @Test fun quarterlyViaInterval() {
        val s = schedule(Frequency.MONTHLY, "2026-01-10", interval = 3, dayOfMonth = 10)
        assertEquals(listOf("2026-01-10", "2026-04-10", "2026-07-10", "2026-10-10"), dates(s, "2026-01-01", "2026-12-31"))
        assertEquals(listOf("2030-01-10"), dates(s, "2029-12-01", "2030-02-28")) // far window, still anchored
    }

    // --- YEARLY ---
    @Test fun yearlyFeb29FallsOnFeb28InNonLeapYears() {
        val s = schedule(Frequency.YEARLY, "2028-01-01", dayOfMonth = 29, monthOfYear = 2)
        assertEquals(listOf("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29"), dates(s, "2028-01-01", "2032-12-31"))
    }

    @Test fun yearlyStartAfterThisYearsDateBeginsNextYear() {
        val s = schedule(Frequency.YEARLY, "2026-10-15", dayOfMonth = 1, monthOfYear = 4)
        assertEquals(listOf("2027-04-01", "2028-04-01"), dates(s, "2026-01-01", "2028-12-31"))
        val sameMonthLater = schedule(Frequency.YEARLY, "2026-04-20", dayOfMonth = 10, monthOfYear = 4)
        assertEquals(listOf("2027-04-10"), dates(sameMonthLater, "2026-01-01", "2027-12-31"))
    }

    @Test fun everyTwoYears() {
        val s = schedule(Frequency.YEARLY, "2026-01-01", interval = 2, dayOfMonth = 5, monthOfYear = 6)
        assertEquals(listOf("2026-06-05", "2028-06-05", "2030-06-05"), dates(s, "2026-01-01", "2031-12-31"))
    }

    // --- bounds ---
    @Test fun startAndEndBoundsAreInclusive() {
        val s = schedule(Frequency.DAILY, "2026-10-10", end = "2026-10-12")
        assertEquals(listOf("2026-10-10", "2026-10-11", "2026-10-12"), dates(s, "2026-10-01", "2026-10-31"))
        assertTrue(dates(s, "2026-10-13", "2026-12-31").isEmpty())
        assertTrue(dates(s, "2026-09-01", "2026-10-09").isEmpty())
        assertTrue(dates(s, "2026-10-12", "2026-10-11").isEmpty()) // inverted window
    }

    // --- pause gaps ---
    @Test fun pauseGapIsExcludedResumeDateIncluded() {
        val s = schedule(Frequency.DAILY, "2026-10-01")
        val pauses = listOf(SchedulePause(scheduleId = "s", startDate = d("2026-10-03"), endDate = d("2026-10-06")))
        val planned = RecurrenceEngine.plan(s, pauses, emptyList(), d("2026-10-01"), d("2026-10-07"))
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-06", "2026-10-07"), planned.map { it.dueDate.toString() })
    }

    @Test fun openPauseExcludesEverythingAfter() {
        val s = schedule(Frequency.MONTHLY, "2026-01-05", dayOfMonth = 5)
        val pauses = listOf(SchedulePause(scheduleId = "s", startDate = d("2026-03-01")))
        val planned = RecurrenceEngine.plan(s, pauses, emptyList(), d("2026-01-01"), d("2026-12-31"))
        assertEquals(listOf("2026-01-05", "2026-02-05"), planned.map { it.dueDate.toString() })
        assertNull(RecurrenceEngine.nextDueDate(s, pauses, d("2026-03-01")))
    }

    @Test fun pausesOfOtherSchedulesAreIgnored() {
        val s = schedule(Frequency.DAILY, "2026-10-01")
        val other = listOf(SchedulePause(scheduleId = "other", startDate = d("2026-10-01")))
        assertEquals(3, RecurrenceEngine.plan(s, other, emptyList(), d("2026-10-01"), d("2026-10-03")).size)
    }

    // --- amount changes ---
    @Test fun amountChangeAppliesFromEffectiveDateOnly() {
        val s = schedule(Frequency.MONTHLY, "2026-01-10", dayOfMonth = 10, amount = 1_500_000)
        val changes = listOf(
            ScheduleAmountChange(scheduleId = "s", effectiveFrom = d("2026-04-10"), amountPaise = 1_600_000),
            ScheduleAmountChange(scheduleId = "s", effectiveFrom = d("2026-06-01"), amountPaise = 1_700_000)
        )
        val planned = RecurrenceEngine.plan(s, emptyList(), changes.reversed(), d("2026-01-01"), d("2026-07-31"))
        assertEquals(
            listOf(1_500_000L, 1_500_000L, 1_500_000L, 1_600_000L, 1_600_000L, 1_700_000L, 1_700_000L),
            planned.map { it.amountPaise }
        )
    }

    @Test fun nextDueDate() {
        val s = schedule(Frequency.MONTHLY, "2026-01-31", dayOfMonth = 31)
        assertEquals(d("2026-02-28"), RecurrenceEngine.nextDueDate(s, emptyList(), d("2026-02-01")))
        assertEquals(d("2026-03-31"), RecurrenceEngine.nextDueDate(s, emptyList(), d("2026-03-01")))
        val ended = schedule(Frequency.DAILY, "2026-01-01", end = "2026-01-05")
        assertNull(RecurrenceEngine.nextDueDate(ended, emptyList(), d("2026-01-06")))
    }

    @Test fun ruleValidation() {
        val bad = listOf(
            { schedule(Frequency.WEEKLY, "2026-01-01") },
            { schedule(Frequency.MONTHLY, "2026-01-01", dayOfMonth = 32) },
            { schedule(Frequency.YEARLY, "2026-01-01", dayOfMonth = 1) },
            { schedule(Frequency.DAILY, "2026-01-01", interval = 0) },
            { schedule(Frequency.DAILY, "2026-01-10", end = "2026-01-09") },
            { schedule(Frequency.DAILY, "2026-01-01", amount = 0) }
        )
        bad.forEachIndexed { i, make -> assertTrue("case $i", runCatching(make).isFailure) }
    }
}
