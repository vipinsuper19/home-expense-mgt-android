package com.dhama.homefinance.data.model

import java.time.LocalDate

/** A transaction joined with its category's display fields, for list screens. */
data class TransactionListItem(
    val id: String,
    val type: TransactionType,
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val amountPaise: Long,
    val quantityMilli: Long?,
    val description: String,
    val notes: String,
    val date: LocalDate
)

/** Income and expense totals for one calendar day, in paise. */
data class DailyTotal(
    val date: LocalDate,
    val incomePaise: Long,
    val expensePaise: Long
)

/**
 * Filters for the Transactions list. Null means "any". [searchPattern] is a LIKE
 * pattern already escaped by [TransactionFilter.likePattern].
 */
data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val search: String = ""
) {
    /** `%term%` with \, % and _ escaped (used with ESCAPE '\'), or null when blank. */
    val searchPattern: String? get() = likePattern(search)

    companion object {
        fun likePattern(term: String): String? {
            val t = term.trim()
            if (t.isEmpty()) return null
            val escaped = t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
