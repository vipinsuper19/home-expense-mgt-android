package com.dhama.homefinance.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.model.ScheduledOccurrence
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.repository.RecurringRepository
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

@RunWith(RobolectricTestRunner::class)
class RecurringRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecurringRepository
    private val dao get() = db.recurringDao()
    private fun d(s: String) = LocalDate.parse(s)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .addCallback(SeedDefaultsCallback).allowMainThreadQueries().build()
        repo = RecurringRepository(db, Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC))
    }

    @After fun tearDown() = db.close()

    private val rent = RecurringSchedule(
        id = "rent", name = " Shop rent ", type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID,
        amountPaise = 1_500_000, frequency = Frequency.MONTHLY, startDate = d("2026-01-31"), dayOfMonth = 31
    )
    private val milk = RecurringSchedule(
        id = "milk", name = "Milk", type = EXPENSE, categoryId = DefaultCategories.MILK_ID,
        amountPaise = 6_000, frequency = Frequency.DAILY, startDate = d("2026-10-01")
    )

    private suspend fun due(id: String) = repo.occurrences(id).map { it.dueDate.toString() }

    @Test fun createTrimsNameAndMaterializeSnapshotsTypeCategoryAmount() = runTest {
        repo.create(rent)
        assertEquals("Shop rent", repo.get("rent")!!.name)
        assertEquals(4, repo.materialize(d("2026-01-01"), d("2026-04-30")))
        val occ = repo.occurrences("rent")
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), occ.map { it.dueDate.toString() })
        assertTrue(occ.all { it.type == INCOME && it.categoryId == DefaultCategories.SHOP_RENT_ID && it.expectedAmountPaise == 1_500_000L })
        assertTrue(occ.all { it.status == OccurrenceStatus.PENDING })
    }

    @Test fun materializeIsIdempotentAndOverlappingWindowsDoNotDuplicate() = runTest {
        repo.create(milk)
        assertEquals(10, repo.materialize(d("2026-10-01"), d("2026-10-10")))
        assertEquals(0, repo.materialize(d("2026-10-01"), d("2026-10-10")))
        assertEquals(5, repo.materialize(d("2026-10-06"), d("2026-10-15")))
        assertEquals(15, repo.occurrences("milk").size)
        assertEquals(15, due("milk").toSet().size)
    }

    @Test fun materializeNeverOverwritesExistingStatus() = runTest {
        repo.create(milk)
        repo.materialize(d("2026-10-01"), d("2026-10-03"))
        val first = repo.occurrences("milk").first()
        dao.setOccurrenceStatus(first.id, OccurrenceStatus.COMPLETED)
        repo.materialize(d("2026-10-01"), d("2026-10-03"))
        assertEquals(OccurrenceStatus.COMPLETED, repo.occurrences("milk").first().status)
    }

    @Test fun amountChangeRepricesOnlyUntouchedFutureOccurrences() = runTest {
        repo.create(rent)
        repo.materialize(d("2026-01-01"), d("2026-06-30"))
        val occ = repo.occurrences("rent")
        // Jan completed; Apr partially received (linked receipt).
        dao.setOccurrenceStatus(occ[0].id, OccurrenceStatus.COMPLETED)
        db.transactionDao().insert(
            Transaction(type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID, amountPaise = 500_000,
                date = d("2026-04-30"), occurrenceId = occ[3].id)
        )
        dao.setOccurrenceStatus(occ[3].id, OccurrenceStatus.PARTIAL)

        repo.changeAmount("rent", d("2026-03-01"), 1_600_000)
        assertEquals(
            listOf(1_500_000L, 1_500_000L, 1_600_000L, 1_500_000L, 1_600_000L, 1_600_000L),
            repo.occurrences("rent").map { it.expectedAmountPaise }
        )
        // Newly materialized dates use the amount in force on their date.
        repo.materialize(d("2026-07-01"), d("2026-07-31"))
        assertEquals(1_600_000L, repo.occurrences("rent").last().expectedAmountPaise)
    }

    @Test fun pauseRemovesUntouchedFutureAndResumeSkipsTheGapForever() = runTest {
        repo.create(milk)
        repo.materialize(d("2026-10-01"), d("2026-10-10"))
        repo.pause("milk", d("2026-10-04"))
        assertEquals(ScheduleStatus.PAUSED, repo.get("milk")!!.status)
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03"), due("milk"))
        assertNull(repo.nextDueDate("milk", d("2026-10-04")))
        // While paused, materialize adds nothing.
        assertEquals(0, repo.materialize(d("2026-10-01"), d("2026-10-31")))

        repo.resume("milk", d("2026-10-08"))
        assertEquals(ScheduleStatus.ACTIVE, repo.get("milk")!!.status)
        repo.materialize(d("2026-10-01"), d("2026-10-10"))
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-08", "2026-10-09", "2026-10-10"), due("milk"))
        assertEquals(d("2026-10-11"), repo.nextDueDate("milk", d("2026-10-11")))
    }

    @Test fun pauseKeepsOccurrencesThatHaveReceipts() = runTest {
        repo.create(milk)
        repo.materialize(d("2026-10-01"), d("2026-10-05"))
        val oct5 = repo.occurrences("milk").last()
        db.transactionDao().insert(
            Transaction(type = EXPENSE, categoryId = DefaultCategories.MILK_ID, amountPaise = 3_000, date = d("2026-10-05"), occurrenceId = oct5.id)
        )
        repo.pause("milk", d("2026-10-03"))
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-05"), due("milk"))
    }

    @Test fun skipMarksSkippedCreatesIfMissingAndSurvivesMaterialize() = runTest {
        repo.create(rent)
        repo.skip("rent", d("2026-02-28"))           // not materialized yet
        repo.materialize(d("2026-01-01"), d("2026-03-31"))
        val byDate = repo.occurrences("rent").associate { it.dueDate.toString() to it.status }
        assertEquals(OccurrenceStatus.SKIPPED, byDate["2026-02-28"])
        assertEquals(OccurrenceStatus.PENDING, byDate["2026-03-31"])
        assertEquals(3, byDate.size)
        // Not a due date -> rejected; already skipped -> rejected.
        assertTrue(runCatching { repo.skip("rent", d("2026-03-15")) }.isFailure)
        assertTrue(runCatching { repo.skip("rent", d("2026-02-28")) }.isFailure)
    }

    @Test fun stopIsPermanentAndTrimsUntouchedFuture() = runTest {
        repo.create(milk)
        repo.materialize(d("2026-10-01"), d("2026-10-10"))
        repo.stop("milk", d("2026-10-05"))
        val s = repo.get("milk")!!
        assertEquals(ScheduleStatus.STOPPED, s.status)
        assertEquals(d("2026-10-05"), s.endDate)
        assertEquals(5, repo.occurrences("milk").size)
        assertEquals(0, repo.materialize(d("2026-10-01"), d("2026-12-31")))
        assertNull(repo.nextDueDate("milk", d("2026-10-06")))
        assertTrue(runCatching { repo.resume("milk", d("2026-10-08")) }.isFailure)
    }

    @Test fun stopWhilePausedClosesThePause() = runTest {
        repo.create(milk)
        repo.pause("milk", d("2026-10-05"))
        repo.stop("milk", d("2026-10-07"))
        assertNull(dao.openPause("milk"))
        assertEquals(ScheduleStatus.STOPPED, repo.get("milk")!!.status)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun occurrenceRequiresExistingSchedule() = runTest {
        dao.insertOccurrencesIgnoringExisting(listOf(
            ScheduledOccurrence(scheduleId = "missing", type = EXPENSE, categoryId = DefaultCategories.MILK_ID,
                dueDate = d("2026-10-01"), expectedAmountPaise = 1)
        ))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun scheduleWithHistoryCannotBeDeleted() = runTest {
        repo.create(milk)
        repo.materialize(d("2026-10-01"), d("2026-10-01"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_schedules WHERE id = 'milk'")
    }

    @Test fun observeSchedulesAndExpectedTotalsSeeMaterializedRows() = runTest {
        repo.create(rent); repo.create(milk)
        repo.materialize(d("2026-10-01"), d("2026-10-31"))
        assertEquals(2, repo.observeSchedules().first().size)
        val expected = db.summaryDao().observeExpectedTotals(d("2026-10-01"), d("2026-10-31")).first()
        assertEquals(1_500_000L, expected.incomePaise)       // one rent on 31 Oct
        assertEquals(31 * 6_000L, expected.expensePaise)      // 31 milk days
        // Actual totals are untouched by expected rows.
        assertEquals(0, db.summaryDao().observeActualTotals(d("2026-10-01"), d("2026-10-31")).first().transactionCount)
    }
}
