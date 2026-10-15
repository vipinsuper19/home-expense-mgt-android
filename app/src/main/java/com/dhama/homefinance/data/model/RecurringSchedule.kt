package com.dhama.homefinance.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** PRD 5.4: daily, weekly, monthly, yearly; "custom" = any of these with [RecurringSchedule.interval] > 1. */
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** ACTIVE generates; PAUSED stops generating until resumed; STOPPED is permanent (endDate set). */
enum class ScheduleStatus { ACTIVE, PAUSED, STOPPED }

/**
 * A recurring expected income or expense (PRD 7.2). It defines *when* and *how much*
 * is expected; nothing here is ever actual money.
 *
 * Due-date rule (anchored, never drifting):
 * - DAILY: every [interval] days from [startDate].
 * - WEEKLY: every [interval] weeks on [dayOfWeek] (1 = Monday … 7 = Sunday), first on/after start.
 * - MONTHLY: every [interval] months on [dayOfMonth] (1–31); a day the month lacks falls on its
 *   last day (DueDates), recomputed from the original day each month.
 * - YEARLY: every [interval] years on [monthOfYear]/[dayOfMonth]; 29 Feb falls on 28 Feb in
 *   non-leap years.
 *
 * [amountPaise] is the amount at [startDate]; later changes live in [ScheduleAmountChange] so
 * past occurrences keep their snapshot. "Next due date" (PRD) is derived by the engine, not stored.
 */
@Entity(
    tableName = "recurring_schedules",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("categoryId"), Index("status")]
)
data class RecurringSchedule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TransactionType,
    val categoryId: String,
    val amountPaise: Long,
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    /** Inclusive last possible due date; set by stop() or by the user. */
    val endDate: LocalDate? = null,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val monthOfYear: Int? = null,
    val status: ScheduleStatus = ScheduleStatus.ACTIVE,
    val reminderEnabled: Boolean = false,
    /** Local time "HH:mm"; reminders come in milestone 12. */
    val reminderTime: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt
) {
    init {
        require(name.isNotBlank()) { "name is required" }
        require(amountPaise > 0) { "amountPaise must be positive" }
        require(interval >= 1) { "interval must be >= 1" }
        require(endDate == null || !endDate.isBefore(startDate)) { "endDate before startDate" }
        when (frequency) {
            Frequency.DAILY -> Unit
            Frequency.WEEKLY -> require(dayOfWeek in 1..7) { "WEEKLY needs dayOfWeek 1..7" }
            Frequency.MONTHLY -> require(dayOfMonth in 1..31) { "MONTHLY needs dayOfMonth 1..31" }
            Frequency.YEARLY -> {
                require(monthOfYear in 1..12) { "YEARLY needs monthOfYear 1..12" }
                require(dayOfMonth in 1..31) { "YEARLY needs dayOfMonth 1..31" }
            }
        }
    }

    val weekday: DayOfWeek? get() = dayOfWeek?.let(DayOfWeek::of)
}

/** A pause window: no due dates in [startDate, endDate). Open (endDate null) while paused. */
@Entity(
    tableName = "schedule_pauses",
    foreignKeys = [
        ForeignKey(entity = RecurringSchedule::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("scheduleId")]
)
data class SchedulePause(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val scheduleId: String,
    val startDate: LocalDate,
    /** Exclusive: the resume date. Null = still paused. */
    val endDate: LocalDate? = null
) {
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && (endDate == null || date.isBefore(endDate))
}

/** From [effectiveFrom] (inclusive) on, occurrences expect [amountPaise]. Earlier ones keep theirs. */
@Entity(
    tableName = "schedule_amount_changes",
    foreignKeys = [
        ForeignKey(entity = RecurringSchedule::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["scheduleId", "effectiveFrom"], unique = true)]
)
data class ScheduleAmountChange(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val scheduleId: String,
    val effectiveFrom: LocalDate,
    val amountPaise: Long
) {
    init { require(amountPaise > 0) { "amountPaise must be positive" } }
}
