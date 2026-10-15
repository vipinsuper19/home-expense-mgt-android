package com.dhama.homefinance.data.local

import androidx.room.Dao
import androidx.room.Query
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.CategoryTotal
import com.dhama.homefinance.data.model.ExpectedTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * All money math happens here in SQL on integer paise. Date bounds are inclusive and
 * compare ISO yyyy-MM-dd strings, which sort chronologically.
 *
 * Actual figures read only the `transactions` table; expected figures read only open
 * `scheduled_occurrences`. They are never mixed.
 */
@Dao
interface SummaryDao {
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountPaise END), 0) AS incomePaise,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountPaise END), 0) AS expensePaise,
            COUNT(*) AS transactionCount
        FROM transactions
        WHERE date BETWEEN :from AND :to
        """
    )
    fun observeActualTotals(from: LocalDate, to: LocalDate): Flow<ActualTotals>

    @Query(
        """
        SELECT
            t.categoryId AS categoryId,
            c.name AS categoryName,
            c.icon AS icon,
            t.type AS type,
            SUM(t.amountPaise) AS totalPaise,
            COUNT(*) AS transactionCount
        FROM transactions t
        JOIN categories c ON c.id = t.categoryId
        WHERE t.date BETWEEN :from AND :to
        GROUP BY t.categoryId, t.type, c.name, c.icon
        ORDER BY t.type, totalPaise DESC, c.name
        """
    )
    fun observeCategoryTotals(from: LocalDate, to: LocalDate): Flow<List<CategoryTotal>>

    /**
     * Outstanding = expected minus receipts already linked to the occurrence (partial
     * payments), floored at 0. Completed and skipped occurrences contribute nothing.
     *
     * Note: linked receipts are subtracted whatever month they are dated in (e.g. October
     * rent received on 2 November still reduces October's outstanding), because the
     * subquery matches on occurrenceId only, not on the transaction date.
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN outstanding END), 0) AS incomePaise,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN outstanding END), 0) AS expensePaise,
            COUNT(*) AS openCount
        FROM (
            SELECT o.type AS type,
                MAX(o.expectedAmountPaise - COALESCE(
                    (SELECT SUM(t.amountPaise) FROM transactions t WHERE t.occurrenceId = o.id), 0
                ), 0) AS outstanding
            FROM scheduled_occurrences o
            WHERE o.dueDate BETWEEN :from AND :to
              AND o.status IN ('PENDING', 'PARTIAL')
        )
        """
    )
    fun observeExpectedTotals(from: LocalDate, to: LocalDate): Flow<ExpectedTotals>
}
