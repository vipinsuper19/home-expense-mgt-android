package com.dhama.homefinance.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.repository.RecurringRepository
import com.dhama.homefinance.data.repository.RoomTransactionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Milestone 9 follow-ups: transactions.scheduleId FK (SET NULL) and occurrence status recompute. */
@RunWith(RobolectricTestRunner::class)
class TransactionOccurrenceLinkTest {
    private lateinit var db: AppDatabase
    private lateinit var recurring: RecurringRepository
    private lateinit var transactions: RoomTransactionRepository
    private fun d(s: String) = LocalDate.parse(s)
    private val clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC)

    private val rent = RecurringSchedule(
        id = "rent", name = "Shop rent", type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID,
        amountPaise = 1_500_000, frequency = Frequency.MONTHLY, startDate = d("2026-10-10"), dayOfMonth = 10
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .addCallback(SeedDefaultsCallback).allowMainThreadQueries().build()
        recurring = RecurringRepository(db, clock)
        transactions = RoomTransactionRepository(db.transactionDao(), clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun octOccurrence() = recurring.occurrences("rent").single { it.dueDate == d("2026-10-10") }
    private suspend fun status() = octOccurrence().status

    private suspend fun setup(): String {
        recurring.create(rent)
        recurring.materialize(d("2026-10-01"), d("2026-11-30"))
        return octOccurrence().id
    }

    private fun receipt(occurrenceId: String?, amount: Long, id: String = "r1") = Transaction(
        id = id, type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID, scheduleId = "rent",
        occurrenceId = occurrenceId, amountPaise = amount, date = d("2026-10-12")
    )

    @Test fun recordPaymentPartialThenFull() = runTest {
        val occ = setup()
        recurring.recordPayment(occ, 500_000, d("2026-10-12"))
        assertEquals(OccurrenceStatus.PARTIAL, status())
        recurring.recordPayment(occ, 1_000_000, d("2026-10-20"))
        assertEquals(OccurrenceStatus.COMPLETED, status())
        val linked = transactions.observeBetween(d("2026-10-01"), d("2026-10-31")).first()
        assertEquals(2, linked.size)
        assertTrue(linked.all { it.scheduleId == "rent" && it.occurrenceId == occ && it.type == INCOME && it.description == "Shop rent" })
        // Closed now: no more payments or skips.
        assertTrue(runCatching { recurring.recordPayment(occ, 1, d("2026-10-21")) }.isFailure)
        assertTrue(runCatching { recurring.recordPayment(octOccurrence().id, 0, d("2026-10-21")) }.isFailure)
    }

    @Test fun editingLinkedAmountRecomputesStatus() = runTest {
        val occ = setup()
        transactions.add(receipt(occ, 1_500_000))
        assertEquals(OccurrenceStatus.COMPLETED, status())
        transactions.update(receipt(occ, 400_000))
        assertEquals(OccurrenceStatus.PARTIAL, status())
        transactions.update(receipt(occ, 2_000_000)) // overpaid still counts as done
        assertEquals(OccurrenceStatus.COMPLETED, status())
    }

    @Test fun deletingLinkedTransactionReopensOccurrence() = runTest {
        val occ = setup()
        transactions.add(receipt(occ, 1_500_000))
        transactions.delete("r1")
        assertEquals(OccurrenceStatus.PENDING, status())
    }

    @Test fun relinkingRecomputesBothOldAndNewOccurrences() = runTest {
        val occ = setup()
        val nov = recurring.occurrences("rent").single { it.dueDate == d("2026-11-10") }.id
        transactions.add(receipt(occ, 1_500_000))
        transactions.update(receipt(nov, 1_500_000))
        assertEquals(OccurrenceStatus.PENDING, status())
        assertEquals(OccurrenceStatus.COMPLETED, recurring.occurrences("rent").single { it.id == nov }.status)
        transactions.update(receipt(null, 1_500_000)) // unlink
        assertEquals(OccurrenceStatus.PENDING, recurring.occurrences("rent").single { it.id == nov }.status)
    }

    @Test fun skippedStaysSkippedWhenReceiptsChange() = runTest {
        val occ = setup()
        transactions.add(receipt(occ, 500_000))
        recurring.skip("rent", d("2026-10-10"))
        transactions.update(receipt(occ, 1_500_000))
        assertEquals(OccurrenceStatus.SKIPPED, status())
        transactions.delete("r1")
        assertEquals(OccurrenceStatus.SKIPPED, status())
    }

    @Test fun unlinkedTransactionsDoNotTouchOccurrences() = runTest {
        setup()
        transactions.add(Transaction(type = EXPENSE, categoryId = DefaultCategories.MILK_ID, amountPaise = 100, date = d("2026-10-10")))
        assertEquals(OccurrenceStatus.PENDING, status())
    }

    @Test fun deletingScheduleSetsTransactionScheduleIdToNull() = runTest {
        // A schedule without occurrences can be deleted (occurrences RESTRICT); linked transactions keep their data.
        recurring.create(rent.copy(id = "tmp"))
        transactions.add(receipt(null, 1_000).copy(scheduleId = "tmp"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_schedules WHERE id = 'tmp'")
        val t = transactions.get("r1")!!
        assertNull(t.scheduleId)
        assertEquals(1_000L, t.amountPaise)
    }

    @Test fun historyReportsPaidAndRemaining() = runTest {
        val occ = setup()
        recurring.recordPayment(occ, 600_000, d("2026-10-12"))
        val history = recurring.observeHistory("rent").first()
        assertEquals(listOf(d("2026-11-10"), d("2026-10-10")), history.map { it.occurrence.dueDate })
        val oct = history.last()
        assertEquals(600_000L, oct.paidPaise)
        assertEquals(900_000L, oct.remainingPaise)
        recurring.skip("rent", d("2026-10-10"))
        assertEquals(0L, recurring.observeHistory("rent").first().last().remainingPaise)
    }

    @Test fun renameTrimsAndRejectsBlank() = runTest {
        recurring.create(rent)
        recurring.rename("rent", "  Rent (shop 2) ")
        assertEquals("Rent (shop 2)", recurring.get("rent")!!.name)
        assertTrue(runCatching { recurring.rename("rent", " ") }.isFailure)
    }
}
