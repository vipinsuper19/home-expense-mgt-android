package com.dhama.homefinance.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * An actual (realised) income or expense. Expected/unpaid items are never stored here.
 *
 * [scheduleId] links to the schedule it came from (FK, SET NULL if the schedule row goes).
 * [occurrenceId] links a receipt/payment to one expected occurrence; adding, editing or
 * deleting a linked transaction recomputes that occurrence's status (TransactionDao).
 * Both links are foreign keys with SET NULL. Deleting an occurrence leaves nothing to
 * recompute (the occurrence is gone); in practice untouched cleanup never deletes linked ones.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = RecurringSchedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ScheduledOccurrence::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId"),
        Index("date"),
        Index("scheduleId"),
        Index("occurrenceId")
    ]
)
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: TransactionType,
    val categoryId: String,
    val scheduleId: String? = null,
    val occurrenceId: String? = null,
    /** Always > 0, in paise. */
    val amountPaise: Long,
    /** Optional quantity (e.g. litres of milk), in thousandths to avoid floating point. */
    val quantityMilli: Long? = null,
    val unitRatePaise: Long? = null,
    val description: String = "",
    /** The date the money moved, used by all reports. */
    val date: LocalDate,
    val notes: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt
) {
    init {
        require(amountPaise > 0) { "amountPaise must be positive" }
        require(quantityMilli == null || quantityMilli > 0) { "quantity must be positive" }
        require(unitRatePaise == null || unitRatePaise >= 0) { "unit rate must not be negative" }
    }
}
