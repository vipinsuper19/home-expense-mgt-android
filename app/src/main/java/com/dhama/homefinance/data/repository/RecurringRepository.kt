package com.dhama.homefinance.data.repository

import androidx.room.withTransaction
import com.dhama.homefinance.data.local.AppDatabase
import com.dhama.homefinance.data.local.RecurringDao
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleAmountChange
import com.dhama.homefinance.data.model.SchedulePause
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.model.ScheduledOccurrence
import com.dhama.homefinance.domain.recurrence.RecurrenceEngine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Schedules and their occurrences. Every multi-step change runs in one DB transaction.
 * The pure [RecurrenceEngine] decides dates and amounts; this class only persists.
 */
@Singleton
class RecurringRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock
) {
    private val dao: RecurringDao get() = db.recurringDao()

    private fun today(): LocalDate = LocalDate.now(clock)

    /** Cleanup and repricing never reach back before today: overdue items stay as they were. */
    private fun notBeforeToday(from: LocalDate): LocalDate = maxOf(from, today())

    fun observeSchedules(): Flow<List<RecurringSchedule>> = dao.observeSchedules()
    suspend fun get(id: String): RecurringSchedule? = dao.getSchedule(id)
    suspend fun occurrences(scheduleId: String): List<ScheduledOccurrence> = dao.occurrencesFor(scheduleId)

    suspend fun create(schedule: RecurringSchedule) {
        val now = Instant.now(clock)
        dao.insertSchedule(schedule.copy(name = schedule.name.trim(), createdAt = now, updatedAt = now))
    }

    /**
     * Creates every planned occurrence in [from, to] that doesn't exist yet. Idempotent: the
     * unique (scheduleId, dueDate) + INSERT OR IGNORE means re-running never duplicates and
     * never overwrites an existing (possibly paid/skipped) occurrence. Returns rows inserted.
     */
    suspend fun materialize(from: LocalDate, to: LocalDate): Int = db.withTransaction {
        val schedules = dao.schedulesRelevantFrom(from)
        if (schedules.isEmpty()) return@withTransaction 0
        val ids = schedules.map { it.id }
        val pauses = dao.pausesFor(ids)
        val changes = dao.amountChangesFor(ids)
        val rows = schedules.flatMap { s ->
            RecurrenceEngine.plan(s, pauses, changes, from, to).map { p ->
                ScheduledOccurrence(
                    scheduleId = s.id,
                    type = s.type,               // snapshot
                    categoryId = s.categoryId,   // snapshot
                    dueDate = p.dueDate,
                    expectedAmountPaise = p.amountPaise
                )
            }
        }
        if (rows.isEmpty()) 0 else dao.insertOccurrencesIgnoringExisting(rows).count { it != -1L }
    }

    /**
     * New amount from [effectiveFrom] on. A second change on the same date replaces the first.
     * Each untouched PENDING occurrence from maxOf(effectiveFrom, today) is repriced from the
     * amount in force on *its own* due date using every change, so adding an earlier change
     * never overwrites a later one. Past, overdue, completed, partial, skipped and linked
     * occurrences keep their snapshot.
     */
    suspend fun changeAmount(scheduleId: String, effectiveFrom: LocalDate, amountPaise: Long) = db.withTransaction {
        val s = requireNotNull(dao.getSchedule(scheduleId)) { "No schedule $scheduleId" }
        require(!effectiveFrom.isBefore(s.startDate)) { "effectiveFrom before schedule start" }
        dao.upsertAmountChange(ScheduleAmountChange(scheduleId = scheduleId, effectiveFrom = effectiveFrom, amountPaise = amountPaise))
        val changes = dao.amountChangesFor(listOf(scheduleId))
        dao.untouchedFrom(scheduleId, notBeforeToday(effectiveFrom)).forEach { o ->
            val amount = RecurrenceEngine.amountOn(s, changes, o.dueDate)
            if (amount != o.expectedAmountPaise) dao.setExpectedAmount(o.id, amount)
        }
        touch(s)
    }

    /** Stop generating from [from] (inclusive) until resumed. Untouched future items are removed. */
    suspend fun pause(scheduleId: String, from: LocalDate) = db.withTransaction {
        val s = requireNotNull(dao.getSchedule(scheduleId)) { "No schedule $scheduleId" }
        check(s.status == ScheduleStatus.ACTIVE) { "Only an active schedule can be paused" }
        dao.insertPause(SchedulePause(scheduleId = scheduleId, startDate = from))
        dao.deleteOpenFrom(scheduleId, notBeforeToday(from))
        dao.setStatus(scheduleId, ScheduleStatus.PAUSED, s.endDate, Instant.now(clock))
    }

    /** Resume on [on] (inclusive). Dates inside the pause are never generated afterwards. */
    suspend fun resume(scheduleId: String, on: LocalDate) = db.withTransaction {
        val s = requireNotNull(dao.getSchedule(scheduleId)) { "No schedule $scheduleId" }
        check(s.status == ScheduleStatus.PAUSED) { "Schedule is not paused" }
        val open = requireNotNull(dao.openPause(scheduleId)) { "No open pause" }
        require(!on.isBefore(open.startDate)) { "Resume date before pause start" }
        dao.closePause(open.id, on)
        dao.setStatus(scheduleId, ScheduleStatus.ACTIVE, s.endDate, Instant.now(clock))
    }

    /** Permanent: [lastDueDate] is the last date that can still be due. Later untouched items go. */
    suspend fun stop(scheduleId: String, lastDueDate: LocalDate) = db.withTransaction {
        val s = requireNotNull(dao.getSchedule(scheduleId)) { "No schedule $scheduleId" }
        val end = s.endDate?.let { minOf(it, lastDueDate) } ?: lastDueDate
        require(!end.isBefore(s.startDate.minusDays(1))) { "Stop date before start" }
        dao.openPause(scheduleId)?.let { dao.closePause(it.id, maxOf(it.startDate, end.plusDays(1))) }
        dao.deleteOpenFrom(scheduleId, notBeforeToday(end.plusDays(1)))
        dao.setStatus(scheduleId, ScheduleStatus.STOPPED, end, Instant.now(clock))
    }

    /**
     * Skip one due date (PRD: "mark skipped without creating an actual transaction"). Creates the
     * occurrence first if it wasn't materialized yet, so a later materialize can't resurrect it.
     * Skipping a PARTIAL occurrence waives the remaining balance (it leaves expected totals);
     * receipts already linked stay actual.
     */
    suspend fun skip(scheduleId: String, dueDate: LocalDate) = db.withTransaction {
        val s = requireNotNull(dao.getSchedule(scheduleId)) { "No schedule $scheduleId" }
        val existing = dao.occurrenceOn(scheduleId, dueDate)
        val occurrence = existing ?: run {
            val ids = listOf(scheduleId)
            val planned = RecurrenceEngine.plan(s, dao.pausesFor(ids), dao.amountChangesFor(ids), dueDate, dueDate).singleOrNull()
            requireNotNull(planned) { "$dueDate is not a due date of this schedule" }
            ScheduledOccurrence(
                scheduleId = scheduleId, type = s.type, categoryId = s.categoryId,
                dueDate = dueDate, expectedAmountPaise = planned.amountPaise
            ).also { dao.insertOccurrencesIgnoringExisting(listOf(it)) }
        }
        check(occurrence.status.isOpen) { "Occurrence is already ${occurrence.status}" }
        dao.setOccurrenceStatus(occurrence.id, OccurrenceStatus.SKIPPED)
    }

    /** Next due date on or after [onOrAfter], or null if stopped/ended/paused indefinitely. */
    suspend fun nextDueDate(scheduleId: String, onOrAfter: LocalDate): LocalDate? {
        val s = dao.getSchedule(scheduleId) ?: return null
        if (s.status == ScheduleStatus.PAUSED) return null
        return RecurrenceEngine.nextDueDate(s, dao.pausesFor(listOf(scheduleId)), onOrAfter)
    }

    private suspend fun touch(s: RecurringSchedule) = dao.setStatus(s.id, s.status, s.endDate, Instant.now(clock))
}
