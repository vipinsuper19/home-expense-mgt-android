package com.dhama.homefinance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleAmountChange
import com.dhama.homefinance.data.model.SchedulePause
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.model.ScheduledOccurrence
import com.dhama.homefinance.data.model.Transaction
import java.time.Instant
import java.time.LocalDate
import androidx.room.Embedded
import kotlinx.coroutines.flow.Flow

data class OccurrenceWithPaid(
    @Embedded val occurrence: ScheduledOccurrence,
    val paidPaise: Long
) {
    /** What is still expected; 0 once completed or skipped (a skip waives the rest). */
    val remainingPaise: Long
        get() = if (occurrence.status.isOpen) (occurrence.expectedAmountPaise - paidPaise).coerceAtLeast(0) else 0
}

@Dao
interface RecurringDao {
    // --- schedules ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(schedule: RecurringSchedule)

    @Update
    suspend fun updateSchedule(schedule: RecurringSchedule)

    @Query("SELECT * FROM recurring_schedules WHERE id = :id")
    suspend fun getSchedule(id: String): RecurringSchedule?

    @Query("SELECT * FROM recurring_schedules ORDER BY status, name")
    fun observeSchedules(): Flow<List<RecurringSchedule>>

    @Query("SELECT * FROM recurring_schedules WHERE status != 'STOPPED' OR endDate >= :from")
    suspend fun schedulesRelevantFrom(from: LocalDate): List<RecurringSchedule>

    @Query("UPDATE recurring_schedules SET status = :status, endDate = :endDate, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: ScheduleStatus, endDate: LocalDate?, now: Instant)

    @Query("SELECT * FROM recurring_schedules WHERE id = :id")
    fun observeSchedule(id: String): Flow<RecurringSchedule?>

    @Query("UPDATE recurring_schedules SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Instant)

    // --- pauses ---
    @Query("SELECT * FROM schedule_pauses")
    fun observeAllPauses(): Flow<List<SchedulePause>>

    @Insert
    suspend fun insertPause(pause: SchedulePause)

    @Query("SELECT * FROM schedule_pauses WHERE scheduleId IN (:scheduleIds)")
    suspend fun pausesFor(scheduleIds: List<String>): List<SchedulePause>

    @Query("SELECT * FROM schedule_pauses WHERE scheduleId = :scheduleId AND endDate IS NULL LIMIT 1")
    suspend fun openPause(scheduleId: String): SchedulePause?

    @Query("UPDATE schedule_pauses SET endDate = :endDate WHERE id = :id")
    suspend fun closePause(id: String, endDate: LocalDate)

    // --- amount changes ---
    /** REPLACE: a second change on the same effective date replaces the first (Tech Lead call 5, PRD 5.4). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAmountChange(change: ScheduleAmountChange)

    @Query("SELECT * FROM schedule_amount_changes WHERE scheduleId IN (:scheduleIds) ORDER BY effectiveFrom")
    suspend fun amountChangesFor(scheduleIds: List<String>): List<ScheduleAmountChange>

    @Query("SELECT * FROM schedule_amount_changes ORDER BY effectiveFrom")
    fun observeAllAmountChanges(): Flow<List<ScheduleAmountChange>>

    // --- occurrences ---
    @Query("SELECT * FROM scheduled_occurrences WHERE id = :id")
    suspend fun getOccurrence(id: String): ScheduledOccurrence?

    /** History, newest first, with what has actually been paid/received against each. */
    @Query(
        """
        SELECT o.*, COALESCE((SELECT SUM(t.amountPaise) FROM transactions t WHERE t.occurrenceId = o.id), 0) AS paidPaise
        FROM scheduled_occurrences o
        WHERE o.scheduleId = :scheduleId
        ORDER BY o.dueDate DESC
        """
    )
    fun observeHistory(scheduleId: String): Flow<List<OccurrenceWithPaid>>

    /** Every actual transaction linked to one of the schedule's occurrences. */
    @Query(
        """
        SELECT t.* FROM transactions t
        JOIN scheduled_occurrences o ON o.id = t.occurrenceId
        WHERE o.scheduleId = :scheduleId
        ORDER BY t.date, t.createdAt
        """
    )
    fun observeReceipts(scheduleId: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrencesIgnoringExisting(occurrences: List<ScheduledOccurrence>): List<Long>

    @Query("SELECT * FROM scheduled_occurrences WHERE scheduleId = :scheduleId ORDER BY dueDate")
    suspend fun occurrencesFor(scheduleId: String): List<ScheduledOccurrence>

    @Query("SELECT * FROM scheduled_occurrences WHERE scheduleId = :scheduleId AND dueDate = :dueDate")
    suspend fun occurrenceOn(scheduleId: String, dueDate: LocalDate): ScheduledOccurrence?

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE occurrenceId = :occurrenceId")
    suspend fun paidFor(occurrenceId: String): Long

    @Query("UPDATE scheduled_occurrences SET status = :status WHERE id = :id")
    suspend fun setOccurrenceStatus(id: String, status: OccurrenceStatus)

    /**
     * "Untouched" occurrences on/after [from]: PENDING with no linked receipt. Completed, partial,
     * skipped and paid items are never returned, so they keep their snapshot. Callers pass
     * maxOf(from, today) so overdue items are never rewritten either.
     */
    @Query(
        """
        SELECT * FROM scheduled_occurrences
        WHERE scheduleId = :scheduleId AND dueDate >= :from AND status = 'PENDING'
          AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.occurrenceId = scheduled_occurrences.id)
        ORDER BY dueDate
        """
    )
    suspend fun untouchedFrom(scheduleId: String, from: LocalDate): List<ScheduledOccurrence>

    @Query("UPDATE scheduled_occurrences SET expectedAmountPaise = :amountPaise WHERE id = :id")
    suspend fun setExpectedAmount(id: String, amountPaise: Long)

    /** Removes untouched pre-generated items from [from] on (pause/stop). Same safety rule; callers pass maxOf(from, today). */
    @Query(
        """
        DELETE FROM scheduled_occurrences
        WHERE scheduleId = :scheduleId AND dueDate >= :from AND status = 'PENDING'
          AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.occurrenceId = scheduled_occurrences.id)
        """
    )
    suspend fun deleteOpenFrom(scheduleId: String, from: LocalDate): Int
}
