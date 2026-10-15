package com.dhama.homefinance.data.repository

import com.dhama.homefinance.data.local.TransactionDao
import com.dhama.homefinance.data.model.Transaction
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val clock: Clock
) : TransactionRepository {
    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>> =
        dao.observeBetween(from, to)
    override fun observeRecent(limit: Int) = dao.observeRecent(limit)
    override fun observe(id: String) = dao.observeById(id)
    override suspend fun get(id: String) = dao.getById(id)

    override suspend fun add(transaction: Transaction) {
        val now = Instant.now(clock)
        dao.insert(transaction.trimmed().copy(createdAt = now, updatedAt = now))
    }

    override suspend fun update(transaction: Transaction) {
        val existing = requireNotNull(dao.getById(transaction.id)) { "No transaction ${transaction.id}" }
        dao.update(
            transaction.trimmed().copy(createdAt = existing.createdAt, updatedAt = Instant.now(clock))
        )
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun Transaction.trimmed() = copy(description = description.trim(), notes = notes.trim())
}
