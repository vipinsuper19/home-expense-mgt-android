package com.dhama.homefinance.data.repository

import com.dhama.homefinance.data.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>>
    fun observeMonth(month: YearMonth): Flow<List<Transaction>> =
        observeBetween(month.atDay(1), month.atEndOfMonth())
    fun observeRecent(limit: Int = 20): Flow<List<Transaction>>
    fun observe(id: String): Flow<Transaction?>
    suspend fun get(id: String): Transaction?
    suspend fun add(transaction: Transaction)
    /** Bumps updatedAt; createdAt is preserved. */
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: String)
}
