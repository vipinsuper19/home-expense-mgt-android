package com.dhama.homefinance.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.repository.RecordResult
import com.dhama.homefinance.data.repository.RecurringRepository
import com.dhama.homefinance.data.repository.RoomSummaryRepository
import com.dhama.homefinance.data.repository.RoomTransactionRepository
import com.dhama.homefinance.domain.ObserveMonthlySummaryUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Milestone 10: shop rent with partial receipts and outstanding balance (PRD section 8). */
@RunWith(RobolectricTestRunner::class)
class RentReceiptTest {
    private lateinit var db: AppDatabase
    private lateinit var recurring: RecurringRepository
    private lateinit var summary: RoomSummaryRepository
    private fun d(s: String) = LocalDate.parse(s)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    private val rent = RecurringSchedule(
        id = "rent", name = "Shop rent", type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID,
        amountPaise = 1_500_000, frequency = Frequency.MONTHLY, startDate = d("2026-10-01"), dayOfMonth = 5
    )

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .addCallback(SeedDefaultsCallback).allowMainThreadQueries().build()
        recurring = RecurringRepository(db, Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC))
        summary = RoomSummaryRepository(db.summaryDao())
        recurring.create(rent)
        recurring.materialize(d("2026-10-01"), d("2026-11-30"))
    }

    @After fun tearDown() = db.close()

    private suspend fun octRent() = recurring.occurrences("rent").single { it.dueDate == d("2026-10-05") }
    private suspend fun monthly(m: YearMonth) = ObserveMonthlySummaryUseCase(summary)(m).first()

    @Test fun prdShopRentExampleEndToEnd() = runTest {
        // 1–2. Expected, not received.
        var s = monthly(oct)
        assertEquals(0L, s.actualIncomePaise)
        assertEquals(1_500_000L, s.expectedIncomePaise)

        // 3. ₹10,000 arrives: actual ₹10,000, ₹5,000 outstanding.
        assertEquals(RecordResult.Recorded, recurring.recordPayment(octRent().id, 1_000_000, d("2026-10-06")))
        s = monthly(oct)
        assertEquals(1_000_000L, s.actualIncomePaise)
        assertEquals(500_000L, s.expectedIncomePaise)
        assertEquals(OccurrenceStatus.PARTIAL, octRent().status)
        assertEquals(500_000L, summary.outstanding(INCOME, oct.atEndOfMonth()).first().single().outstandingPaise)

        // 4. The remaining ₹5,000 arrives (next month) and closes the occurrence.
        assertEquals(RecordResult.Recorded, recurring.recordPayment(octRent().id, 500_000, d("2026-11-02")))
        assertEquals(OccurrenceStatus.COMPLETED, octRent().status)
        assertTrue(summary.outstanding(INCOME, oct.atEndOfMonth()).first().isEmpty())

        // 5. Monthly actual income counts only money received in that month.
        assertEquals(1_000_000L, monthly(oct).actualIncomePaise)
        assertEquals(0L, monthly(oct).expectedIncomePaise)
        assertEquals(500_000L, monthly(nov).actualIncomePaise)
        assertEquals(1_500_000L, monthly(nov).expectedIncomePaise) // November's own rent
    }

    @Test fun receiptIsIdempotentPerReceiptId() = runTest {
        val id = octRent().id
        assertEquals(RecordResult.Recorded, recurring.recordPayment(id, 1_000_000, d("2026-10-06"), receiptId = "r-1"))
        assertEquals(RecordResult.AlreadyRecorded, recurring.recordPayment(id, 1_000_000, d("2026-10-06"), receiptId = "r-1"))
        assertEquals(1, recurring.observeReceipts("rent").first().size)
        assertEquals(500_000L, summary.outstanding(INCOME, oct.atEndOfMonth()).first().single().outstandingPaise)
        // Reusing the id for a different occurrence is a bug, not a duplicate.
        val novId = recurring.occurrences("rent").single { it.dueDate == d("2026-11-05") }.id
        assertTrue(runCatching { recurring.recordPayment(novId, 1, d("2026-11-05"), receiptId = "r-1") }.isFailure)
    }

    @Test fun receiptAboveOutstandingIsRejectedAtomically() = runTest {
        val id = octRent().id
        recurring.recordPayment(id, 1_000_000, d("2026-10-06"))
        assertEquals(RecordResult.ExceedsOutstanding(500_000), recurring.recordPayment(id, 500_001, d("2026-10-07")))
        assertEquals(1, recurring.observeReceipts("rent").first().size)
        assertEquals(OccurrenceStatus.PARTIAL, octRent().status)
    }

    @Test fun outstandingIncludesOverdueExcludesFutureClosedAndOtherType() = runTest {
        val elec = rent.copy(id = "elec", name = "Electricity", type = EXPENSE, categoryId = DefaultCategories.MILK_ID, amountPaise = 200_000)
        recurring.create(elec)
        recurring.materialize(d("2026-10-01"), d("2026-12-31"))
        // As of end of November: Oct (overdue) + Nov rent; December is in the future.
        assertEquals(listOf(d("2026-10-05"), d("2026-11-05")), summary.outstanding(INCOME, nov.atEndOfMonth()).first().map { it.dueDate })
        recurring.skip("rent", d("2026-11-05"))
        recurring.recordPayment(octRent().id, 1_500_000, d("2026-10-05"))
        assertTrue(summary.outstanding(INCOME, nov.atEndOfMonth()).first().isEmpty())
        assertEquals(2, summary.outstanding(EXPENSE, nov.atEndOfMonth()).first().size)
        assertEquals("Electricity", summary.outstanding(EXPENSE, nov.atEndOfMonth()).first().first().scheduleName)
    }

    @Test fun editingAReceiptUpdatesOutstanding() = runTest {
        val tx = RoomTransactionRepository(db.transactionDao(), Clock.systemUTC())
        recurring.recordPayment(octRent().id, 1_000_000, d("2026-10-06"), receiptId = "r-1")
        tx.update(tx.get("r-1")!!.copy(amountPaise = 1_500_000))
        assertEquals(OccurrenceStatus.COMPLETED, octRent().status)
        assertTrue(summary.outstanding(INCOME, oct.atEndOfMonth()).first().isEmpty())
        tx.delete("r-1")
        assertEquals(1_500_000L, summary.outstanding(INCOME, oct.atEndOfMonth()).first().single().outstandingPaise)
    }
}
