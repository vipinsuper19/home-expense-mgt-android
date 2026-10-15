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
 * [scheduleId]/[occurrenceId] are plain nullable columns for now; foreign keys to the
 * recurring tables are added when those tables exist.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
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
