package com.dhama.homefinance.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * One expected (not yet actual) income or expense, e.g. this month's shop rent or electricity bill.
 *
 * Generated from a [RecurringSchedule] by RecurringRepository.materialize. [type],
 * [categoryId] and [expectedAmountPaise] are snapshots taken when generated, so a later
 * schedule edit doesn't rewrite history and expected totals need no join.
 *
 * Expected amounts never count as actual. Money only becomes actual through [Transaction]
 * rows (linked back here via Transaction.occurrenceId, possibly several for partial receipts).
 */
@Entity(
    tableName = "scheduled_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        // Schedules are stopped, not deleted; RESTRICT keeps occurrence history intact.
        ForeignKey(
            entity = RecurringSchedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["scheduleId", "dueDate"], unique = true),
        Index("categoryId"),
        Index("dueDate")
    ]
)
data class ScheduledOccurrence(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val scheduleId: String,
    val type: TransactionType,
    val categoryId: String,
    val dueDate: LocalDate,
    val expectedAmountPaise: Long,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING
) {
    init {
        require(expectedAmountPaise > 0) { "expectedAmountPaise must be positive" }
    }
}

/**
 * Stored lifecycle state. "Overdue" is derived (PENDING or PARTIAL with dueDate before today),
 * so it never goes stale when the app isn't opened.
 */
enum class OccurrenceStatus {
    /** Nothing received/paid yet. */
    PENDING,
    /** Some linked receipts, less than expected. */
    PARTIAL,
    /** Fully paid/received. */
    COMPLETED,
    /** User skipped it; never creates an actual transaction. */
    SKIPPED;

    val isOpen: Boolean get() = this == PENDING || this == PARTIAL
}
