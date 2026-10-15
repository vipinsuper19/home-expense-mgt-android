package com.dhama.homefinance.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.ExpectedTotals
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.ScheduledOccurrence
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.repository.RoomSummaryRepository
import com.dhama.homefinance.domain.ObserveMonthlySummaryUseCase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SummaryDaoTest {
    private lateinit var db: AppDatabase
    private val summary get() = db.summaryDao()

    private val milk = DefaultCategories.MILK_ID
    private val groceries = DefaultCategories.idFor("groceries")
    private val rent = DefaultCategories.SHOP_RENT_ID
    private val salary = DefaultCategories.idFor("salary")
    private val bills = DefaultCategories.idFor("bills")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .addCallback(SeedDefaultsCallback)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private suspend fun tx(
        date: String, amount: Long, type: TransactionType = EXPENSE,
        category: String = milk, occurrenceId: String? = null
    ) = Transaction(
        type = type, categoryId = category, amountPaise = amount,
        date = LocalDate.parse(date), occurrenceId = occurrenceId
    ).also { db.transactionDao().insert(it) }

    private suspend fun occ(
        date: String, amount: Long, type: TransactionType = EXPENSE,
        status: OccurrenceStatus = OccurrenceStatus.PENDING, category: String = bills,
        scheduleId: String = "s-${date}-${type}-${amount}"
    ) = ScheduledOccurrence(
        scheduleId = scheduleId, type = type, categoryId = category,
        dueDate = LocalDate.parse(date), expectedAmountPaise = amount, status = status
    ).also { db.occurrenceDao().insertIgnoringExisting(it) }

    private suspend fun actual(ym: String) =
        YearMonth.parse(ym).let { summary.observeActualTotals(it.atDay(1), it.atEndOfMonth()).first() }

    private suspend fun expected(ym: String) =
        YearMonth.parse(ym).let { summary.observeExpectedTotals(it.atDay(1), it.atEndOfMonth()).first() }

    @Test fun emptyMonthIsAllZeros() = runTest {
        assertEquals(ActualTotals(0, 0, 0), actual("2026-10"))
        assertEquals(ExpectedTotals(0, 0, 0), expected("2026-10"))
        assertTrue(summary.observeCategoryTotals(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).first().isEmpty())
    }

    @Test fun monthBoundariesAreInclusiveAndExclusiveOfNeighbours() = runTest {
        tx("2026-09-30", 1)      // previous month
        tx("2026-10-01", 10)     // first day
        tx("2026-10-31", 100)    // last day
        tx("2026-11-01", 1000)   // next month
        assertEquals(ActualTotals(0, 110, 2), actual("2026-10"))
        assertEquals(ActualTotals(0, 1, 1), actual("2026-09"))
        assertEquals(ActualTotals(0, 1000, 1), actual("2026-11"))
    }

    @Test fun mixedTypesSumSeparatelyWithExactPaise() = runTest {
        tx("2026-10-05", 5_000_000, INCOME, salary)
        tx("2026-10-07", 1_500_000, INCOME, rent)
        tx("2026-10-02", 6_050, EXPENSE, milk)
        tx("2026-10-03", 6_050, EXPENSE, milk)
        tx("2026-10-09", 123_45, EXPENSE, groceries)
        assertEquals(ActualTotals(6_500_000, 24_445, 5), actual("2026-10"))
    }

    @Test fun categoryBreakdownGroupsAndOrders() = runTest {
        tx("2026-10-02", 6_000, EXPENSE, milk)
        tx("2026-10-03", 6_000, EXPENSE, milk)
        tx("2026-10-04", 50_000, EXPENSE, groceries)
        tx("2026-10-05", 1_500_000, INCOME, rent)
        tx("2026-11-01", 999, EXPENSE, milk) // other month
        val rows = summary.observeCategoryTotals(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).first()
        assertEquals(
            listOf(Triple(groceries, 50_000L, 1), Triple(milk, 12_000L, 2), Triple(rent, 1_500_000L, 1)),
            rows.map { Triple(it.categoryId, it.totalPaise, it.transactionCount) }
        )
        assertEquals("Milk", rows[1].categoryName)
        assertEquals(INCOME, rows[2].type)
    }

    @Test fun expectedNeverCountsAsActual() = runTest {
        occ("2026-10-10", 1_500_000, INCOME, category = rent)    // pending rent
        occ("2026-10-15", 200_000, EXPENSE)                       // unpaid bill
        tx("2026-10-02", 6_000)                                   // one actual expense
        assertEquals(ActualTotals(0, 6_000, 1), actual("2026-10"))
        assertEquals(ExpectedTotals(1_500_000, 200_000, 2), expected("2026-10"))
    }

    @Test fun completedAndSkippedAreNotExpected_partialShowsOutstanding() = runTest {
        val rentOcc = occ("2026-10-10", 1_500_000, INCOME, OccurrenceStatus.PARTIAL, rent)
        tx("2026-10-12", 1_000_000, INCOME, rent, occurrenceId = rentOcc.id) // partial receipt
        val bill = occ("2026-10-15", 200_000, EXPENSE, OccurrenceStatus.COMPLETED)
        tx("2026-10-15", 200_000, EXPENSE, bills, occurrenceId = bill.id)
        occ("2026-10-20", 70_000, EXPENSE, OccurrenceStatus.SKIPPED)

        assertEquals(ActualTotals(1_000_000, 200_000, 2), actual("2026-10"))
        assertEquals(ExpectedTotals(500_000, 0, 1), expected("2026-10"))
    }

    @Test fun overpaidOccurrenceFloorsAtZero() = runTest {
        val o = occ("2026-10-10", 100_000, INCOME, OccurrenceStatus.PARTIAL, rent)
        tx("2026-10-10", 150_000, INCOME, rent, occurrenceId = o.id)
        assertEquals(ExpectedTotals(0, 0, 1), expected("2026-10"))
    }

    @Test fun occurrencesAreUniquePerScheduleAndDueDate() = runTest {
        occ("2026-10-10", 100, scheduleId = "rent")
        val dup = occ("2026-10-10", 999, scheduleId = "rent")
        assertEquals(-1L, db.occurrenceDao().insertIgnoringExisting(dup))
        assertEquals(ExpectedTotals(0, 100, 1), expected("2026-10"))
    }

    @Test fun leapYearFebruary() = runTest {
        tx("2028-02-28", 1)
        tx("2028-02-29", 10)
        tx("2028-03-01", 100)
        occ("2028-02-29", 500, scheduleId = "bill") // a 31st schedule clamped to 29 Feb
        assertEquals(ActualTotals(0, 11, 2), actual("2028-02"))
        assertEquals(ExpectedTotals(0, 500, 1), expected("2028-02"))
        // Non-leap February ends on the 28th.
        tx("2027-02-28", 7)
        tx("2027-03-01", 70)
        assertEquals(ActualTotals(0, 7, 1), actual("2027-02"))
    }

    @Test fun useCaseCombinesSeparately() = runTest {
        tx("2026-10-05", 5_000_000, INCOME, salary)
        tx("2026-10-06", 300_000, EXPENSE, groceries)
        occ("2026-10-10", 1_500_000, INCOME, category = rent)
        occ("2026-10-20", 200_000, EXPENSE)
        val s = ObserveMonthlySummaryUseCase(RoomSummaryRepository(summary))(YearMonth.of(2026, 10)).first()
        assertEquals(5_000_000L, s.actualIncomePaise)
        assertEquals(300_000L, s.actualExpensePaise)
        assertEquals(4_700_000L, s.netPaise)
        assertEquals(1_500_000L, s.expectedIncomePaise)
        assertEquals(200_000L, s.expectedExpensePaise)
        assertEquals(6_000_000L, s.projectedNetPaise)
        assertEquals(listOf(groceries), s.expenseByCategory.map { it.categoryId })
        assertEquals(listOf(salary), s.incomeByCategory.map { it.categoryId })
    }
}
