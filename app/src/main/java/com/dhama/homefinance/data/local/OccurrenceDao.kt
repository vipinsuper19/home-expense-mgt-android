package com.dhama.homefinance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.ScheduledOccurrence

/** Minimal access needed now; the recurring milestone adds the rest. */
@Dao
interface OccurrenceDao {
    /** IGNORE + unique (scheduleId, dueDate) makes regeneration duplicate-free. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(occurrence: ScheduledOccurrence): Long

    @Query("SELECT * FROM scheduled_occurrences WHERE id = :id")
    suspend fun getById(id: String): ScheduledOccurrence?

    @Query("UPDATE scheduled_occurrences SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: OccurrenceStatus)
}
