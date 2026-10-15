package com.dhama.homefinance.domain

import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.repository.SummaryRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveMonthlySummaryUseCase @Inject constructor(
    private val repository: SummaryRepository
) {
    operator fun invoke(month: YearMonth): Flow<MonthlySummary> {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        return combine(
            repository.actualTotals(from, to),
            repository.categoryTotals(from, to),
            repository.expectedTotals(from, to)
        ) { actual, categories, expected ->
            MonthlySummary(
                month = month,
                actualIncomePaise = actual.incomePaise,
                actualExpensePaise = actual.expensePaise,
                transactionCount = actual.transactionCount,
                expenseByCategory = categories.filter { it.type == TransactionType.EXPENSE },
                incomeByCategory = categories.filter { it.type == TransactionType.INCOME },
                expectedIncomePaise = expected.incomePaise,
                expectedExpensePaise = expected.expensePaise,
                openExpectedCount = expected.openCount
            )
        }
    }
}
