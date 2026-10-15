package com.dhama.homefinance.domain

import com.dhama.homefinance.data.model.CategoryTotal
import java.time.YearMonth

/**
 * Everything the Home/Reports screens need for one month, in paise.
 *
 * "Actual" figures come only from recorded transactions. "Expected" figures are the
 * still-outstanding part of open (pending/partial) scheduled occurrences and are shown
 * separately; they are never added into the actual totals.
 */
data class MonthlySummary(
    val month: YearMonth,
    val actualIncomePaise: Long,
    val actualExpensePaise: Long,
    val transactionCount: Int,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
    val expectedIncomePaise: Long,
    val expectedExpensePaise: Long,
    val openExpectedCount: Int
) {
    /** Actual net balance for the month: actual income minus actual expense. */
    val netPaise: Long get() = actualIncomePaise - actualExpensePaise

    /** What net would be if every open expected item settled. Always labelled as projected. */
    val projectedNetPaise: Long get() = netPaise + expectedIncomePaise - expectedExpensePaise
}
