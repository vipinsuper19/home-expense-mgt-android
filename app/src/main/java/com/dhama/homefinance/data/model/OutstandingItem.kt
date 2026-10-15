package com.dhama.homefinance.data.model

import java.time.LocalDate

/**
 * An open (PENDING/PARTIAL) occurrence with what has been received/paid so far.
 * Outstanding = expected − linked actual transactions, never below 0 (PRD integrity rules).
 */
data class OutstandingItem(
    val occurrenceId: String,
    val scheduleId: String,
    val scheduleName: String,
    val type: TransactionType,
    val dueDate: LocalDate,
    val expectedAmountPaise: Long,
    val paidPaise: Long
) {
    val outstandingPaise: Long get() = (expectedAmountPaise - paidPaise).coerceAtLeast(0)
}
