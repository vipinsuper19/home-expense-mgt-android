package com.dhama.homefinance.data.repository

import com.dhama.homefinance.data.local.TransactionListDao
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.DailyTotal
import com.dhama.homefinance.data.model.TransactionFilter
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.model.TransactionType
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface TransactionListRepository {
    fun items(month: YearMonth, filter: TransactionFilter): Flow<List<TransactionListItem>>
    fun dailyTotals(month: YearMonth, filter: TransactionFilter): Flow<List<DailyTotal>>
    fun recent(month: YearMonth, limit: Int): Flow<List<TransactionListItem>>
    fun filterCategories(month: YearMonth, type: TransactionType?): Flow<List<Category>>
}

@Singleton
class RoomTransactionListRepository @Inject constructor(
    private val dao: TransactionListDao
) : TransactionListRepository {
    override fun items(month: YearMonth, filter: TransactionFilter) =
        dao.observeItems(month.atDay(1), month.atEndOfMonth(), filter.type, filter.categoryId, filter.searchPattern)

    override fun dailyTotals(month: YearMonth, filter: TransactionFilter) =
        dao.observeDailyTotals(month.atDay(1), month.atEndOfMonth(), filter.type, filter.categoryId, filter.searchPattern)

    override fun recent(month: YearMonth, limit: Int) =
        dao.observeRecent(month.atDay(1), month.atEndOfMonth(), limit)

    override fun filterCategories(month: YearMonth, type: TransactionType?) =
        dao.observeFilterCategories(month.atDay(1), month.atEndOfMonth(), type)
}
