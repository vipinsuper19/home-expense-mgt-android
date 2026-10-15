package com.dhama.homefinance.data.local

import androidx.room.Dao
import androidx.room.Query
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.DailyTotal
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Filtered queries for the Transactions list. All filtering, search and summing happen
 * in SQL on integer paise. Every filter is optional (NULL = no filter); search is a
 * bound, pre-escaped LIKE pattern (never string-concatenated SQL), case-insensitive for
 * ASCII, over description and notes.
 */
@Dao
interface TransactionListDao {
    @Query(
        """
        SELECT t.id, t.type, t.categoryId, c.name AS categoryName, c.icon AS categoryIcon,
               t.amountPaise, t.quantityMilli, t.description, t.notes, t.date
        FROM transactions t
        JOIN categories c ON c.id = t.categoryId
        WHERE t.date BETWEEN :from AND :to
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:pattern IS NULL OR t.description LIKE :pattern ESCAPE '\' OR t.notes LIKE :pattern ESCAPE '\')
        ORDER BY t.date DESC, t.createdAt DESC, t.id
        """
    )
    fun observeItems(
        from: LocalDate, to: LocalDate,
        type: TransactionType?, categoryId: String?, pattern: String?
    ): Flow<List<TransactionListItem>>

    @Query(
        """
        SELECT date,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountPaise END), 0) AS incomePaise,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountPaise END), 0) AS expensePaise
        FROM transactions
        WHERE date BETWEEN :from AND :to
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:pattern IS NULL OR description LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\')
        GROUP BY date
        ORDER BY date DESC
        """
    )
    fun observeDailyTotals(
        from: LocalDate, to: LocalDate,
        type: TransactionType?, categoryId: String?, pattern: String?
    ): Flow<List<DailyTotal>>

    /** Same shape as SummaryDao.observeActualTotals, but honouring the list filters. */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountPaise END), 0) AS incomePaise,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountPaise END), 0) AS expensePaise,
            COUNT(*) AS transactionCount
        FROM transactions
        WHERE date BETWEEN :from AND :to
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:pattern IS NULL OR description LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\')
        """
    )
    fun observeTotals(
        from: LocalDate, to: LocalDate,
        type: TransactionType?, categoryId: String?, pattern: String?
    ): Flow<ActualTotals>

    /** Active categories plus any (even archived) used in the range, optionally of one type. */
    @Query(
        """
        SELECT * FROM categories
        WHERE (:type IS NULL OR type = :type)
          AND (isActive = 1 OR id IN (
                SELECT DISTINCT categoryId FROM transactions WHERE date BETWEEN :from AND :to))
        ORDER BY type, sortOrder, name
        """
    )
    fun observeFilterCategories(from: LocalDate, to: LocalDate, type: TransactionType?): Flow<List<Category>>
}
