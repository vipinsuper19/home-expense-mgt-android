package com.dhama.homefinance.ui.transactions

import androidx.lifecycle.SavedStateHandle
import com.dhama.homefinance.data.model.ActualTotals
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.DailyTotal
import com.dhama.homefinance.data.model.TransactionFilter
import com.dhama.homefinance.data.model.TransactionListItem
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.CategoryRepository
import com.dhama.homefinance.data.repository.TransactionListRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun item(date: String, amount: Long, type: TransactionType, category: String, description: String) =
    TransactionListItem(
        id = "$date-$amount", type = type, categoryId = category, categoryName = category, categoryIcon = "x",
        amountPaise = amount, quantityMilli = null, description = description, notes = "", date = LocalDate.parse(date)
    )

/** In-memory stand-in for the SQL; the SQL itself is covered by TransactionListDaoTest. */
private class FakeListRepository(private val rows: List<TransactionListItem>) : TransactionListRepository {
    val requested = mutableListOf<Pair<YearMonth, TransactionFilter>>()
    private fun match(m: YearMonth, f: TransactionFilter) = rows.filter {
        YearMonth.from(it.date) == m &&
            (f.type == null || it.type == f.type) &&
            (f.categoryId == null || it.categoryId == f.categoryId) &&
            (f.search.isBlank() || it.description.contains(f.search.trim(), ignoreCase = true))
    }
    override fun items(month: YearMonth, filter: TransactionFilter): Flow<List<TransactionListItem>> {
        requested += month to filter
        return flowOf(match(month, filter))
    }
    override fun dailyTotals(month: YearMonth, filter: TransactionFilter) = flowOf(
        match(month, filter).groupBy { it.date }.map { (d, l) ->
            DailyTotal(d, l.filter { it.type == INCOME }.sumOf { it.amountPaise }, l.filter { it.type == EXPENSE }.sumOf { it.amountPaise })
        }.sortedByDescending { it.date }
    )
    override fun totals(month: YearMonth, filter: TransactionFilter) = flowOf(
        match(month, filter).let { l ->
            ActualTotals(l.filter { it.type == INCOME }.sumOf { it.amountPaise }, l.filter { it.type == EXPENSE }.sumOf { it.amountPaise }, l.size)
        }
    )
    override fun filterCategories(month: YearMonth, type: TransactionType?) = flowOf(
        listOf(Category("milk", "Milk", EXPENSE, "x"), Category("salary", "Salary", INCOME, "x"))
            .filter { type == null || it.type == type }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private lateinit var repo: FakeListRepository
    private lateinit var handle: SavedStateHandle
    private lateinit var vm: TransactionsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeListRepository(listOf(
            item("2026-10-02", 6_000, EXPENSE, "milk", "morning"),
            item("2026-10-02", 50_000, EXPENSE, "groceries", "kirana"),
            item("2026-10-05", 5_000_000, INCOME, "salary", "salary")
        ))
        handle = SavedStateHandle()
        categories = object : CategoryRepository {
            private val all = listOf(
                Category("milk", "Milk", EXPENSE, "x"),
                Category("salary", "Salary", INCOME, "x"),
                Category("pets", "Pets", EXPENSE, "x", isActive = false)
            )
            override fun observeAll() = flowOf(all)
            override fun observeActive(type: TransactionType) = flowOf(all.filter { it.type == type && it.isActive })
            override suspend fun get(id: String) = all.find { it.id == id }
            override suspend fun add(category: Category) = Unit
            override suspend fun update(category: Category) = Unit
            override suspend fun setActive(id: String, active: Boolean) = Unit
            override suspend fun seedDefaults() = Unit
        }
        vm = TransactionsViewModel(handle, repo, categories, clock)
    }

    private lateinit var categories: CategoryRepository

    @After fun tearDown() = Dispatchers.resetMain()

    private val ui get() = vm.uiState.value

    @Test fun defaultsToCurrentMonthGroupedNewestFirstWithTotals() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), ui.month)
        assertNull(ui.type); assertNull(ui.categoryId); assertEquals("", ui.search)
        assertEquals(5_000_000L, ui.totals.incomePaise)
        assertEquals(56_000L, ui.totals.expensePaise)
        assertEquals(4_944_000L, ui.netPaise)
        assertEquals(listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 2)), ui.days.map { it.date })
        assertEquals(2, ui.days[1].items.size)
        assertEquals(56_000L, ui.days[1].total.expensePaise)
        assertFalse(ui.isEmpty)
    }

    @Test fun monthNavigationAndEmptyState() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.previousMonth(); vm.previousMonth(); advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 8), ui.month)
        assertTrue(ui.isEmpty)
        assertFalse(ui.isFiltered)
        vm.nextMonth(); advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 9), ui.month)
        vm.goToCurrentMonth(); advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), ui.month)
        assertFalse(ui.isEmpty)
    }

    @Test fun filtersApplyAndClear() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setType(EXPENSE); vm.setCategory("milk"); advanceUntilIdle()
        assertEquals(listOf(6_000L), ui.days.flatMap { it.items }.map { it.amountPaise })
        assertTrue(ui.isFiltered)
        vm.setSearch("zzz"); advanceUntilIdle()
        assertTrue(ui.isEmpty)
        vm.clearFilters(); advanceUntilIdle()
        assertFalse(ui.isFiltered)
        assertEquals(3, ui.totals.transactionCount)
    }

    @Test fun searchIsDebouncedAndTrimmed() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val before = repo.requested.size
        "kiran".forEach { vm.setSearch(vm.uiState.value.search + it) }
        vm.setSearch("kirana  ")
        advanceUntilIdle()
        assertEquals(before + 1, repo.requested.size) // one query, not one per keystroke
        assertEquals("kirana", repo.requested.last().second.search)
        assertEquals(listOf(50_000L), ui.days.flatMap { it.items }.map { it.amountPaise })
        assertEquals("kirana  ", ui.search) // the field keeps what was typed
    }

    @Test fun filterStateSurvivesRecreationFromSavedStateHandle() = runTest(dispatcher) {
        vm.previousMonth(); vm.setType(INCOME); vm.setCategory("salary"); vm.setSearch("sal")
        val restored = TransactionsViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), repo, categories, clock)
        backgroundScope.launch { restored.uiState.collect {} }
        advanceUntilIdle()
        val s = restored.uiState.value
        assertEquals(YearMonth.of(2026, 9), s.month)
        assertEquals(INCOME, s.type)
        assertEquals("salary", s.categoryId)
        assertEquals("sal", s.search)
    }

    @Test fun switchingTypeDropsMismatchedCategory() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setCategory("milk"); advanceUntilIdle()
        vm.setType(INCOME); advanceUntilIdle()
        assertNull(ui.categoryId)
    }

    @Test fun categoryOptionsFollowType() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(2, ui.categories.size)
        vm.setType(INCOME); advanceUntilIdle()
        assertEquals(listOf("salary"), ui.categories.map { it.id })
    }

    @Test fun setTypeClearsCategoryViaRepositoryEvenWhenUiEmpty() = runTest(dispatcher) {
        // After recreation, uiState.categories can be empty until data loads; setType must
        // still clear a mismatched category by querying the repository.
        handle[TransactionsViewModel.KEY_CATEGORY] = "milk"
        handle[TransactionsViewModel.KEY_TYPE] = null
        val fresh = TransactionsViewModel(handle, repo, categories, clock)
        // Don't collect: categories stay empty in the initial state.
        assertTrue(fresh.uiState.value.categories.isEmpty())
        fresh.setType(INCOME)
        advanceUntilIdle()
        assertNull(handle.get<String>(TransactionsViewModel.KEY_CATEGORY))
        assertEquals(INCOME.name, handle.get(TransactionsViewModel.KEY_TYPE))
    }

    @Test fun selectedCategoryStaysListedWhenMissingFromNewMonth() = runTest(dispatcher) {
        // Selecting an archived category (kept via #6 filter options when used) then
        // navigating to an empty month must still show it while selected.
        val emptyRepo = FakeListRepository(emptyList())
        // Override filterCategories to never include "pets".
        val cats = categories
        val repoWithPetsSelected = object : TransactionListRepository by emptyRepo {
            override fun filterCategories(month: YearMonth, type: TransactionType?) =
                emptyRepo.filterCategories(month, type) // no pets
        }
        backgroundScope.launch { vm.uiState.collect {} }
        // Seed selection of pets on the original VM via handle, rebuild with empty-month repo.
        handle[TransactionsViewModel.KEY_CATEGORY] = "pets"
        val rebuilt = TransactionsViewModel(handle, repoWithPetsSelected, cats, clock)
        backgroundScope.launch { rebuilt.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("milk", "salary", "pets"), rebuilt.uiState.value.categories.map { it.id })
        assertEquals("pets", rebuilt.uiState.value.categoryId)
    }
}
