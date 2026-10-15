package com.dhama.homefinance.util

import java.time.LocalDate
import java.time.YearMonth

object DueDates {
    /**
     * The due date for a monthly schedule on [dayOfMonth] in [month]. Days the month
     * doesn't have clamp to its last day: 31 in February -> 28th (29th in leap years),
     * 31 in April -> 30th. Never rolls into the next month.
     */
    fun monthlyDueDate(dayOfMonth: Int, month: YearMonth): LocalDate {
        require(dayOfMonth in 1..31) { "dayOfMonth must be 1..31, was $dayOfMonth" }
        return month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
    }
}
