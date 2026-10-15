package com.dhama.homefinance.domain.recurrence

import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleAmountChange
import com.dhama.homefinance.data.model.SchedulePause
import com.dhama.homefinance.util.DueDates
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A due date the engine expects, with the amount in force on that date. */
data class PlannedOccurrence(val dueDate: LocalDate, val amountPaise: Long)

/**
 * Pure, deterministic recurrence engine: no clock, no I/O. Every date is computed from the
 * schedule's anchor (start date / configured day) by index, so clamped month-ends never drift.
 */
object RecurrenceEngine {

    /** Raw due dates of the rule in [from, to] (inclusive), honouring start and end bounds only. */
    fun dueDates(schedule: RecurringSchedule, from: LocalDate, to: LocalDate): List<LocalDate> {
        val lo = maxOf(from, schedule.startDate)
        val hi = schedule.endDate?.let { minOf(to, it) } ?: to
        if (lo.isAfter(hi)) return emptyList()
        val n = schedule.interval.toLong()
        return when (schedule.frequency) {
            Frequency.DAILY -> {
                val skip = ceilDiv(ChronoUnit.DAYS.between(schedule.startDate, lo), n)
                generateSequence(schedule.startDate.plusDays(skip * n)) { it.plusDays(n) }
                    .takeWhile { !it.isAfter(hi) }.toList()
            }
            Frequency.WEEKLY -> {
                val first = schedule.startDate.with(TemporalAdjusters.nextOrSame(schedule.weekday!!))
                if (first.isAfter(hi)) return emptyList()
                val step = 7 * n
                val skip = if (lo.isAfter(first)) ceilDiv(ChronoUnit.DAYS.between(first, lo), step) else 0
                generateSequence(first.plusDays(skip * step)) { it.plusDays(step) }
                    .takeWhile { !it.isAfter(hi) }.toList()
            }
            Frequency.MONTHLY -> byIndex(schedule, lo, hi, monthsPerStep = n) { ym ->
                DueDates.monthlyDueDate(schedule.dayOfMonth!!, ym)
            }
            Frequency.YEARLY -> byIndex(schedule, lo, hi, monthsPerStep = 12 * n) { ym ->
                DueDates.monthlyDueDate(schedule.dayOfMonth!!, ym)
            }
        }
    }

    /**
     * Due dates in [from, to] minus pause windows, each with the amount in force on that date
     * (latest [ScheduleAmountChange] with effectiveFrom <= dueDate, else the base amount).
     */
    fun plan(
        schedule: RecurringSchedule,
        pauses: List<SchedulePause>,
        amountChanges: List<ScheduleAmountChange>,
        from: LocalDate,
        to: LocalDate
    ): List<PlannedOccurrence> {
        val changes = amountChanges.filter { it.scheduleId == schedule.id }.sortedBy { it.effectiveFrom }
        val myPauses = pauses.filter { it.scheduleId == schedule.id }
        return dueDates(schedule, from, to)
            .filter { d -> myPauses.none { it.contains(d) } }
            .map { d -> PlannedOccurrence(d, amountOn(schedule, changes, d)) }
    }

    fun amountOn(schedule: RecurringSchedule, sortedChanges: List<ScheduleAmountChange>, date: LocalDate): Long =
        sortedChanges.lastOrNull { !it.effectiveFrom.isAfter(date) }?.amountPaise ?: schedule.amountPaise

    /** First planned due date on or after [onOrAfter], searching up to [horizonYears]. */
    fun nextDueDate(
        schedule: RecurringSchedule,
        pauses: List<SchedulePause>,
        onOrAfter: LocalDate,
        horizonYears: Long = 5
    ): LocalDate? = plan(schedule, pauses, emptyList(), onOrAfter, onOrAfter.plusYears(horizonYears)).firstOrNull()?.dueDate

    /** Monthly/yearly: k-th period = anchor month + k*step; date clamped; skip any before start. */
    private inline fun byIndex(
        schedule: RecurringSchedule,
        lo: LocalDate,
        hi: LocalDate,
        monthsPerStep: Long,
        dateIn: (YearMonth) -> LocalDate
    ): List<LocalDate> {
        val anchor = if (schedule.frequency == Frequency.YEARLY) {
            // The first configured month on/after the start month.
            val startYm = YearMonth.from(schedule.startDate)
            val sameYear = startYm.withMonth(schedule.monthOfYear!!)
            if (sameYear.isBefore(startYm)) sameYear.plusYears(1) else sameYear
        } else YearMonth.from(schedule.startDate)
        val monthsToLo = ChronoUnit.MONTHS.between(anchor, YearMonth.from(lo))
        var k = maxOf(0L, monthsToLo / monthsPerStep - 1)
        val out = ArrayList<LocalDate>()
        while (true) {
            val d = dateIn(anchor.plusMonths(k * monthsPerStep))
            if (d.isAfter(hi)) break
            if (!d.isBefore(lo) && !d.isBefore(schedule.startDate)) out += d
            k++
        }
        return out
    }

    private fun ceilDiv(a: Long, b: Long): Long = if (a <= 0) 0 else (a + b - 1) / b
}
