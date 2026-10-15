package com.dhama.homefinance.ui.home

import androidx.lifecycle.SavedStateHandle
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.CategoryTotal
import com.dhama.homefinance.data.model.DailyTotal
import com.dhama.homefinance.data.model.ExpectedTotals
import com.dhama.homefinance.data.model.TransactionFilter
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.SummaryRepository
import com.dhama.homefinance.data.repository.TransactionListRepository
import com.dhama.homefinance.domain.ObserveMonthlySummaryUseCase
import com.dhama.homefinance.util.DateTicker
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun item(date: String, amount: Long, type: TransactionType, category: String = "milk") =
    TransactionListItem(
        id = "$date-$amount-$type", type = type, categoryId = category, categoryName = category,
        categoryIcon = "x", amountPaise = amount, quantityMilli = null, description = "", notes = "",
        date = LocalDate.parse(date)
    )

private class FakeSummary(
    private val months: Map<YearMonth, Triple<ActualTotals, List<CategoryTotal>, ExpectedTotals>>,
    private val today: Map<LocalDate, ActualTotals> = emptyMap()
) : SummaryRepository {
    override fun actualTotals(from: LocalDate, to: LocalDate): Flow<ActualTotals> {
        if (from == to) return flowOf(today[from] ?: ActualTotals(0, 0, 0))
        val m = YearMonth.from(from)
        return flowOf(months[m]?.first ?: ActualTotals(0, 0, 0))
    }
    override fun categoryTotals(from: LocalDate, to: LocalDate) =
        flowOf(months[YearMonth.from(from)]?.second.orEmpty())
    override fun expectedTotals(from: LocalDate, to: LocalDate) =
        flowOf(months[YearMonth.from(from)]?.third ?: ExpectedTotals(0, 0, 0))
}

private class FakeList(private val rows: List<TransactionListItem>) : TransactionListRepository {
    override fun items(month: YearMonth, filter: TransactionFilter) =
        flowOf(rows.filter { YearMonth.from(it.date) == month })
    override fun dailyTotals(month: YearMonth, filter: TransactionFilter) = flowOf(emptyList<DailyTotal>())
    override fun recent(month: YearMonth, limit: Int) =
        flowOf(rows.filter { YearMonth.from(it.date) == month }.take(limit))
    override fun filterCategories(month: YearMonth, type: TransactionType?) = flowOf(emptyList<Category>())
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    /** Settable clock so tests can cross midnight. */
    private class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone() = zone
        override fun withZone(zone: ZoneId) = MutableClock(now, zone)
        override fun instant() = now
    }
    private val clock = MutableClock(Instant.parse("2026-10-15T06:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private val ticker = DateTicker(clock)
    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)

    private val octActual = ActualTotals(5_000_000, 300_000, 4)
    private val octExpected = ExpectedTotals(1_500_000, 200_000, 2)
    private val octCategories = listOf(
        CategoryTotal("groc", "Groceries", "x", EXPENSE, 200_000, 2),
        CategoryTotal("milk", "Milk", "x", EXPENSE, 100_000, 2)
    )
    private val todayTotals = ActualTotals(0, 6_050, 1)

    private lateinit var summary: FakeSummary
    private lateinit var list: FakeList
    private lateinit var handle: SavedStateHandle
    private lateinit var vm: HomeViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        summary = FakeSummary(
            months = mapOf(
                oct to Triple(octActual, octCategories, octExpected),
                sep to Triple(ActualTotals(0, 0, 0), emptyList(), ExpectedTotals(0, 0, 0))
            ),
            today = mapOf(
                LocalDate.of(2026, 10, 15) to todayTotals,
                LocalDate.of(2026, 10, 16) to ActualTotals(1_000, 0, 1)
            )
        )
        list = FakeList(listOf(
            item("2026-10-15", 6_050, EXPENSE),
            item("2026-10-14", 50_000, EXPENSE, "groc"),
            item("2026-10-05", 5_000_000, INCOME, "salary"),
            item("2026-10-02", 100_000, EXPENSE),
            item("2026-10-01", 50_000, EXPENSE, "groc"),
            item("2026-07-31", 1, EXPENSE) // another month, never shown for Oct/Sep
        ))
        handle = SavedStateHandle()
        vm = HomeViewModel(handle, ObserveMonthlySummaryUseCase(summary), summary, list, ticker)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val ui get() = vm.uiState.value

    @Test fun defaultsToCurrentMonthWithActualExpectedAndTodaySeparated() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(oct, ui.month)
        assertTrue(ui.isCurrentMonth)
        assertEquals(LocalDate.of(2026, 10, 15), ui.today)
        val s = ui.summary!!
        assertEquals(5_000_000L, s.actualIncomePaise)
        assertEquals(300_000L, s.actualExpensePaise)
        assertEquals(4_700_000L, s.netPaise)
        // Expected is separate and never mixed into actual.
        assertEquals(1_500_000L, s.expectedIncomePaise)
        assertEquals(200_000L, s.expectedExpensePaise)
        assertEquals(2, s.openExpectedCount)
        assertEquals(6_000_000L, s.projectedNetPaise) // net + expected income - expected expense
        assertTrue(ui.hasExpected)
        // Today's totals are independent of the month card.
        assertEquals(todayTotals, ui.todayTotals)
        assertEquals(listOf("Groceries", "Milk"), s.expenseByCategory.map { it.categoryName })
        assertEquals(5, ui.recent.size)
        assertFalse(ui.isEmpty)
    }

    @Test fun emptyMonthHasNoActualOrExpected() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.previousMonth(); runCurrent()
        assertEquals(sep, ui.month)
        assertFalse(ui.isCurrentMonth)
        assertTrue(ui.isEmpty)
        assertFalse(ui.hasExpected)
        assertEquals(0L, ui.summary!!.actualIncomePaise)
        assertEquals(0L, ui.summary!!.expectedIncomePaise)
        assertTrue(ui.recent.isEmpty())
        // Today's totals stay on today even when browsing another month.
        assertEquals(todayTotals, ui.todayTotals)
    }

    @Test fun monthNavigationAndRestore() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.previousMonth(); vm.previousMonth(); runCurrent()
        assertEquals(YearMonth.of(2026, 8), ui.month)
        vm.goToCurrentMonth(); runCurrent()
        assertEquals(oct, ui.month)

        val restored = HomeViewModel(
            SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }),
            ObserveMonthlySummaryUseCase(summary), summary, list, ticker
        )
        backgroundScope.launch { restored.uiState.collect {} }
        runCurrent()
        assertEquals(oct, restored.uiState.value.month)
        assertEquals(5_000_000L, restored.uiState.value.summary!!.actualIncomePaise)
    }

    @Test fun projectedIsNotAddedIntoActualNet() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        val s = ui.summary!!
        // Actual net ignores expected entirely.
        assertEquals(s.actualIncomePaise - s.actualExpensePaise, s.netPaise)
        assertEquals(s.netPaise + s.expectedIncomePaise - s.expectedExpensePaise, s.projectedNetPaise)
        assertTrue(s.projectedNetPaise != s.netPaise)
    }

    @Test fun todayCardRollsOverAtMidnightViaTicker() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 15), ui.today)
        assertEquals(todayTotals, ui.todayTotals)

        // 23:59 IST -> 00:00:30 IST next day. Nothing changes until the next minute check.
        clock.now = Instant.parse("2026-10-15T18:30:30Z")
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 15), ui.today)
        advanceTimeBy(DateTicker.CHECK_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 16), ui.today)
        assertEquals(ActualTotals(1_000, 0, 1), ui.todayTotals)
        assertTrue(ui.isCurrentMonth)
    }

    @Test fun resumeTriggersImmediateRecheck() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        clock.now = Instant.parse("2026-10-15T18:31:00Z") // 00:01 IST on the 16th
        ticker.resumeObserver.onResume(object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle: androidx.lifecycle.Lifecycle = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
        })
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 16), ui.today)
    }

    @Test fun goToCurrentMonthUsesClockAtTapTime() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.previousMonth(); runCurrent()
        clock.now = Instant.parse("2026-10-31T19:00:00Z") // 1 Nov 00:30 IST
        vm.goToCurrentMonth(); runCurrent()
        assertEquals(YearMonth.of(2026, 11), ui.month)
    }
}
