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
}
