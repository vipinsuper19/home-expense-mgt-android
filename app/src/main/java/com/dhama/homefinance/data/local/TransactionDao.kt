package com.dhama.homefinance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dhama.homefinance.data.model.Transaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Inclusive date range, newest first. */
    @Query(
        "SELECT * FROM transactions WHERE date BETWEEN :from AND :to " +
            "ORDER BY date DESC, createdAt DESC"
    )
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<Transaction?>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Derives a linked occurrence's status from its receipts: none -> PENDING, less than
     * expected -> PARTIAL, otherwise COMPLETED. SKIPPED is a user decision and is kept.
     */
    @Query(
        """
        UPDATE scheduled_occurrences SET status = CASE
            WHEN (SELECT COALESCE(SUM(t.amountPaise), 0) FROM transactions t WHERE t.occurrenceId = :occurrenceId) = 0
                THEN 'PENDING'
            WHEN (SELECT COALESCE(SUM(t.amountPaise), 0) FROM transactions t WHERE t.occurrenceId = :occurrenceId) < expectedAmountPaise
                THEN 'PARTIAL'
            ELSE 'COMPLETED' END
        WHERE id = :occurrenceId AND status != 'SKIPPED'
        """
    )
    suspend fun recomputeOccurrenceStatus(occurrenceId: String)

    @androidx.room.Transaction
    suspend fun insertAndRecompute(transaction: Transaction) {
        insert(transaction)
        transaction.occurrenceId?.let { recomputeOccurrenceStatus(it) }
    }

    /** Recomputes both the old and the new occurrence when the link or amount changes. */
    @androidx.room.Transaction
    suspend fun updateAndRecompute(transaction: Transaction) {
        val old = getById(transaction.id)?.occurrenceId
        update(transaction)
        setOfNotNull(old, transaction.occurrenceId).forEach { recomputeOccurrenceStatus(it) }
    }

    @androidx.room.Transaction
    suspend fun deleteAndRecompute(id: String) {
        val old = getById(id)?.occurrenceId
        deleteById(id)
        old?.let { recomputeOccurrenceStatus(it) }
    }
}
