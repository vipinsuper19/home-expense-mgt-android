package com.dhama.homefinance.ui.recurring

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhama.homefinance.data.local.AppDatabase
import com.dhama.homefinance.data.local.SeedDefaultsCallback
import com.dhama.homefinance.data.model.Frequency
import com.dhama.homefinance.data.model.OccurrenceStatus
import com.dhama.homefinance.data.model.RecurringSchedule
import com.dhama.homefinance.data.model.ScheduleStatus
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.repository.RecurringRepository
import com.dhama.homefinance.data.repository.RoomCategoryRepository
import com.dhama.homefinance.util.DateTicker
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ViewModels over a real in-memory Room DB; Room queries run on the test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecurringViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repo: RecurringRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneOffset.UTC)
    private val ticker = DateTicker(clock)
    private fun d(s: String) = LocalDate.parse(s)

    private val rent = RecurringSchedule(
        id = "rent", name = "Shop rent", type = TransactionType.INCOME, categoryId = DefaultCategories.SHOP_RENT_ID,
        amountPaise = 1_500_000, frequency = Frequency.MONTHLY, startDate = d("2026-09-10"), dayOfMonth = 10
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .addCallback(SeedDefaultsCallback).allowMainThreadQueries()
            .setQueryCoroutineContext(dispatcher).build()
        repo = RecurringRepository(db, clock)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun TestScope.collect(flow: kotlinx.coroutines.flow.Flow<*>) = backgroundScope.launch { flow.collect {} }

    // --- list ---
    @Test fun listMaterializesWindowAndShowsNextDueAndCurrentAmount() = runTest(dispatcher) {
        repo.create(rent)
        repo.changeAmount("rent", d("2026-10-01"), 1_600_000)
        val vm = RecurringListViewModel(repo, ticker)
        collect(vm.uiState); runCurrent()
        val row = vm.uiState.value.rows.single()
        assertEquals(1_600_000L, row.currentAmountPaise)
        assertEquals(d("2026-11-10"), row.nextDueDate)
        // Window = start of this month .. +3 months: Oct, Nov, Dec, Jan.
        assertEquals(listOf("2026-10-10", "2026-11-10", "2026-12-10", "2027-01-10"), repo.occurrences("rent").map { it.dueDate.toString() })
    }

    @Test fun listEmptyStateAndPausedHasNoNextDue() = runTest(dispatcher) {
        val vm = RecurringListViewModel(repo, ticker)
        collect(vm.uiState); runCurrent()
        assertFalse(vm.uiState.value.loading)
        assertTrue(vm.uiState.value.rows.isEmpty())
        repo.create(rent); repo.pause("rent", d("2026-10-15")); runCurrent()
        assertNull(vm.uiState.value.rows.single().nextDueDate)
    }

    // --- detail ---
    private fun detail() = ScheduleDetailViewModel(SavedStateHandle(mapOf(ScheduleDetailViewModel.ARG_SCHEDULE_ID to "rent")), repo, ticker)

    @Test fun detailHistoryDerivesOverdueAndActionsWork() = runTest(dispatcher) {
        repo.create(rent)
        repo.materialize(d("2026-09-01"), d("2026-09-30")) // Sep 10: overdue
        val vm = detail(); collect(vm.uiState); runCurrent()
        val sep = vm.uiState.value.history.single { it.item.occurrence.dueDate == d("2026-09-10") }
        assertTrue(sep.isOverdue)
        assertFalse(vm.uiState.value.history.single { it.item.occurrence.dueDate == d("2026-11-10") }.isOverdue)

        assertFalse(vm.recordPayment(sep.item.occurrence.id, "0", d("2026-10-15"))) // invalid amount
        assertTrue(vm.recordPayment(sep.item.occurrence.id, "1,500", d("2026-10-15"))); runCurrent()
        val after = vm.uiState.value.history.single { it.item.occurrence.dueDate == d("2026-09-10") }
        assertEquals(OccurrenceStatus.PARTIAL, after.item.occurrence.status)
        assertEquals(1_350_000L, after.item.remainingPaise)

        vm.skip(d("2026-11-10")); runCurrent()
        assertEquals(OccurrenceStatus.SKIPPED, vm.uiState.value.history.single { it.item.occurrence.dueDate == d("2026-11-10") }.item.occurrence.status)
        vm.skip(d("2026-11-10")); runCurrent()
        assertTrue(vm.uiState.value.actionFailed) // already skipped
        vm.dismissError(); runCurrent()
        assertFalse(vm.uiState.value.actionFailed)
    }

    @Test fun detailPauseResumeStop() = runTest(dispatcher) {
        repo.create(rent)
        val vm = detail(); collect(vm.uiState); runCurrent()
        assertEquals(d("2026-11-10"), vm.uiState.value.nextDueDate)
        vm.pause(); runCurrent()
        assertEquals(ScheduleStatus.PAUSED, vm.uiState.value.schedule!!.status)
        assertNull(vm.uiState.value.nextDueDate)
        vm.resume(); runCurrent()
        assertEquals(ScheduleStatus.ACTIVE, vm.uiState.value.schedule!!.status)
        vm.stop(); runCurrent()
        assertEquals(ScheduleStatus.STOPPED, vm.uiState.value.schedule!!.status)
        assertEquals(d("2026-10-15"), vm.uiState.value.schedule!!.endDate)
        assertTrue(vm.uiState.value.history.none { it.item.occurrence.dueDate > d("2026-10-15") })
    }

    // --- form ---
    private fun form(id: String? = null, handle: SavedStateHandle = SavedStateHandle(mapOf(ScheduleFormViewModel.ARG_SCHEDULE_ID to id))) =
        ScheduleFormViewModel(handle, repo, RoomCategoryRepository(db.categoryDao()), ticker)

    @Test fun formValidationAndCreateMonthly() = runTest(dispatcher) {
        val vm = form(); collect(vm.uiState); runCurrent()
        assertEquals(d("2026-10-15"), vm.uiState.value.form.startDate)
        assertEquals("15", vm.uiState.value.form.dayOfMonthText)
        assertFalse(vm.uiState.value.canSave)
        vm.setName("Electricity")
        vm.setCategory(DefaultCategories.MILK_ID)
        vm.setAmount("0"); runCurrent()
        assertTrue(vm.uiState.value.form.amountError); assertFalse(vm.uiState.value.canSave)
        vm.setAmount("1,850.50")
        vm.setDayOfMonth("32"); runCurrent()
        assertTrue(vm.uiState.value.form.dayOfMonthError); assertFalse(vm.uiState.value.canSave)
        vm.setDayOfMonth("31")
        vm.setInterval(""); runCurrent()
        assertFalse(vm.uiState.value.canSave)
        vm.setInterval("1")
        vm.setEndDate(d("2026-10-01")); runCurrent()
        assertTrue(vm.uiState.value.form.endDateError)
        vm.setEndDate(null); runCurrent()
        assertTrue(vm.uiState.value.canSave)
        vm.save(); runCurrent()
        assertTrue(vm.uiState.value.done)
        val s = repo.observeSchedules().first().single()
        assertEquals(185_050L, s.amountPaise)
        assertEquals(31, s.dayOfMonth); assertNull(s.dayOfWeek); assertNull(s.monthOfYear)
        assertEquals(TransactionType.EXPENSE, s.type)
    }

    @Test fun formCreatesWeeklyAndYearlyWithOnlyTheirFields() = runTest(dispatcher) {
        val vm = form(); collect(vm.uiState)
        vm.setName("Paper"); vm.setCategory(DefaultCategories.MILK_ID); vm.setAmount("70")
        vm.setFrequency(Frequency.WEEKLY); vm.setDayOfWeek(7); vm.save(); runCurrent()
        val weekly = repo.observeSchedules().first().single()
        assertEquals(7, weekly.dayOfWeek); assertNull(weekly.dayOfMonth)

        val y = form(); collect(y.uiState)
        y.setType(TransactionType.INCOME); y.setName("Bonus"); y.setCategory(DefaultCategories.SHOP_RENT_ID); y.setAmount("5000")
        y.setFrequency(Frequency.YEARLY); y.setMonthOfYear(2); y.setDayOfMonth("29"); y.save(); runCurrent()
        val yearly = repo.observeSchedules().first().single { it.name == "Bonus" }
        assertEquals(2, yearly.monthOfYear); assertEquals(29, yearly.dayOfMonth); assertNull(yearly.dayOfWeek)
    }

    @Test fun typeChangeClearsCategoryAndStartDateReseedsDays() = runTest(dispatcher) {
        val vm = form(); collect(vm.uiState)
        vm.setCategory(DefaultCategories.MILK_ID)
        vm.setType(TransactionType.INCOME); runCurrent()
        assertNull(vm.uiState.value.form.categoryId)
        assertTrue(vm.uiState.value.categories.all { it.type == TransactionType.INCOME })
        vm.setStartDate(d("2027-02-28")); runCurrent()
        assertEquals("28", vm.uiState.value.form.dayOfMonthText)
        assertEquals(2, vm.uiState.value.form.monthOfYear)
    }

    @Test fun editRenamesAndChangesAmountFromEffectiveDateOnly() = runTest(dispatcher) {
        repo.create(rent)
        repo.materialize(d("2026-09-01"), d("2026-12-31"))
        val vm = form("rent"); collect(vm.uiState); runCurrent()
        val f = vm.uiState.value.form
        assertTrue(vm.uiState.value.isEdit)
        assertEquals("Shop rent", f.name); assertEquals("15000", f.amountText.substringBefore('.'))
        assertEquals(d("2026-10-15"), f.amountEffectiveFrom)
        // Rule and type are locked.
        vm.setFrequency(Frequency.DAILY); vm.setType(TransactionType.EXPENSE); runCurrent()
        assertEquals(Frequency.MONTHLY, vm.uiState.value.form.frequency)
        assertEquals(TransactionType.INCOME, vm.uiState.value.form.type)

        vm.setName("Shop 2 rent"); vm.setAmount("16000"); vm.setAmountEffectiveFrom(d("2026-11-01"))
        vm.save(); runCurrent()
        assertTrue(vm.uiState.value.done)
        assertEquals("Shop 2 rent", repo.get("rent")!!.name)
        assertEquals(
            listOf(1_500_000L, 1_500_000L, 1_600_000L, 1_600_000L),
            repo.occurrences("rent").map { it.expectedAmountPaise }
        )
    }

    @Test fun editWithoutAmountChangeAddsNoChangeRow() = runTest(dispatcher) {
        repo.create(rent)
        val vm = form("rent"); collect(vm.uiState); runCurrent()
        vm.setName("Rent"); vm.save(); runCurrent()
        assertTrue(repo.observeAllAmountChanges().first().isEmpty())
    }

    @Test fun formSurvivesProcessDeath() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = form(handle = handle); collect(vm.uiState)
        vm.setName("Water"); vm.setAmount("300"); vm.setFrequency(Frequency.YEARLY); vm.setMonthOfYear(4); runCurrent()
        val restored = form(handle = handle); collect(restored.uiState); runCurrent()
        val f = restored.uiState.value.form
        assertEquals("Water", f.name); assertEquals("300", f.amountText)
        assertEquals(Frequency.YEARLY, f.frequency); assertEquals(4, f.monthOfYear)
    }
}
