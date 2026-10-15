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
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

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

    // --- pauses ---
    @Insert
    suspend fun insertPause(pause: SchedulePause)

    @Query("SELECT * FROM schedule_pauses WHERE scheduleId IN (:scheduleIds)")
    suspend fun pausesFor(scheduleIds: List<String>): List<SchedulePause>

    @Query("SELECT * FROM schedule_pauses WHERE scheduleId = :scheduleId AND endDate IS NULL LIMIT 1")
    suspend fun openPause(scheduleId: String): SchedulePause?

    @Query("UPDATE schedule_pauses SET endDate = :endDate WHERE id = :id")
    suspend fun closePause(id: String, endDate: LocalDate)

    // --- amount changes ---
    /** REPLACE: a second change on the same effective date overrides the first. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAmountChange(change: ScheduleAmountChange)

    @Query("SELECT * FROM schedule_amount_changes WHERE scheduleId IN (:scheduleIds) ORDER BY effectiveFrom")
    suspend fun amountChangesFor(scheduleIds: List<String>): List<ScheduleAmountChange>

    // --- occurrences ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrencesIgnoringExisting(occurrences: List<ScheduledOccurrence>): List<Long>

    @Query("SELECT * FROM scheduled_occurrences WHERE scheduleId = :scheduleId ORDER BY dueDate")
    suspend fun occurrencesFor(scheduleId: String): List<ScheduledOccurrence>

    @Query("SELECT * FROM scheduled_occurrences WHERE scheduleId = :scheduleId AND dueDate = :dueDate")
    suspend fun occurrenceOn(scheduleId: String, dueDate: LocalDate): ScheduledOccurrence?

    @Query("UPDATE scheduled_occurrences SET status = :status WHERE id = :id")
    suspend fun setOccurrenceStatus(id: String, status: OccurrenceStatus)

    /**
     * Only untouched future items are rewritten: PENDING with no linked receipt. Completed,
     * partial, skipped and anything already paid keep their snapshot.
     */
    @Query(
        """
        UPDATE scheduled_occurrences SET expectedAmountPaise = :amountPaise
        WHERE scheduleId = :scheduleId AND dueDate >= :from AND status = 'PENDING'
          AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.occurrenceId = scheduled_occurrences.id)
        """
    )
    suspend fun repriceOpenFrom(scheduleId: String, from: LocalDate, amountPaise: Long): Int

    /** Removes untouched pre-generated items from [from] on (pause/stop). Same safety rule. */
    @Query(
        """
        DELETE FROM scheduled_occurrences
        WHERE scheduleId = :scheduleId AND dueDate >= :from AND status = 'PENDING'
          AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.occurrenceId = scheduled_occurrences.id)
        """
    )
    suspend fun deleteOpenFrom(scheduleId: String, from: LocalDate): Int
}
