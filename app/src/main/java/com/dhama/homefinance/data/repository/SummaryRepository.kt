package com.dhama.homefinance.data.repository

import com.dhama.homefinance.data.local.SummaryDao
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.CategoryTotal
import com.dhama.homefinance.data.model.ExpectedTotals
import com.dhama.homefinance.data.model.OutstandingItem
import com.dhama.homefinance.data.model.TransactionType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    fun actualTotals(from: LocalDate, to: LocalDate): Flow<ActualTotals>
    fun categoryTotals(from: LocalDate, to: LocalDate): Flow<List<CategoryTotal>>
    fun expectedTotals(from: LocalDate, to: LocalDate): Flow<ExpectedTotals>
    /** Open occurrences of [type] due up to [to], including earlier overdue ones. */
    fun outstanding(type: TransactionType, to: LocalDate): Flow<List<OutstandingItem>>
}

@Singleton
class RoomSummaryRepository @Inject constructor(private val dao: SummaryDao) : SummaryRepository {
    override fun actualTotals(from: LocalDate, to: LocalDate) = dao.observeActualTotals(from, to)
    override fun categoryTotals(from: LocalDate, to: LocalDate) = dao.observeCategoryTotals(from, to)
    override fun expectedTotals(from: LocalDate, to: LocalDate) = dao.observeExpectedTotals(from, to)
    override fun outstanding(type: TransactionType, to: LocalDate) = dao.observeOutstanding(type, to)
}
