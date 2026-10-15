package com.dhama.homefinance.data.model

/** Actual totals for a date range, in paise. Rows come straight from SQL SUMs. */
data class ActualTotals(
    val incomePaise: Long,
    val expensePaise: Long,
    val transactionCount: Int
)

/** Actual total for one category in a date range. */
data class CategoryTotal(
    val categoryId: String,
    val categoryName: String,
    val icon: String,
    val type: TransactionType,
    val totalPaise: Long,
    val transactionCount: Int
)

/** Still-outstanding expected amount for open occurrences in a date range. */
data class ExpectedTotals(
    val incomePaise: Long,
    val expensePaise: Long,
    val openCount: Int
)
