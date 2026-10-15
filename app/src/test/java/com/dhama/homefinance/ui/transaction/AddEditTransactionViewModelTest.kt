package com.dhama.homefinance.ui.transaction

import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.CategoryRepository
import com.dhama.homefinance.data.repository.TransactionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

private val milk = Category(id = "milk", name = "Milk", type = EXPENSE, icon = "x")
private val groceries = Category(id = "groc", name = "Groceries", type = EXPENSE, icon = "x")
private val oldPets = Category(id = "pets", name = "Pets", type = EXPENSE, icon = "x", isActive = false)
private val salary = Category(id = "salary", name = "Salary", type = INCOME, icon = "x")

private class FakeCategories : CategoryRepository {
    val all = listOf(milk, groceries, oldPets, salary)
    override fun observeAll() = flowOf(all)
    override fun observeActive(type: TransactionType) = flowOf(all.filter { it.type == type && it.isActive })
    override suspend fun get(id: String) = all.find { it.id == id }
    override suspend fun add(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun setActive(id: String, active: Boolean) = Unit
    override suspend fun seedDefaults() = Unit
}

private class FakeTransactions : TransactionRepository {
    val rows = MutableStateFlow<List<Transaction>>(emptyList())
    var adds = 0
    var gets = 0
    var failDelete = false
    var gate: CompletableDeferred<Unit>? = null
    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>> = rows
    override fun observeRecent(limit: Int) = rows
    override fun observe(id: String) = rows.map { l -> l.find { it.id == id } }
    override suspend fun get(id: String): Transaction? { gets++; return rows.value.find { it.id == id } }
    override suspend fun add(transaction: Transaction) {
        gate?.await(); adds++; rows.value += transaction
    }
    override suspend fun update(transaction: Transaction) {
        // Mirrors RoomTransactionRepository: createdAt preserved from the stored row.
        val existing = rows.value.first { it.id == transaction.id }
        rows.value = rows.value.map {
            if (it.id == transaction.id) transaction.copy(createdAt = existing.createdAt, updatedAt = Instant.EPOCH.plusSeconds(99)) else it
        }
    }
    override suspend fun delete(id: String) { if (failDelete) error("disk"); rows.value = rows.value.filterNot { it.id == id } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditTransactionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private lateinit var txRepo: FakeTransactions
    private lateinit var vm: AddEditTransactionViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        txRepo = FakeTransactions()
    }

    private fun newVm(id: String?, handle: SavedStateHandle = SavedStateHandle(mapOf(AddEditTransactionViewModel.ARG_TRANSACTION_ID to id))) =
        AddEditTransactionViewModel(handle, txRepo, FakeCategories(), clock).also { savedHandle = handle }

    private lateinit var savedHandle: SavedStateHandle

    @After fun tearDown() = Dispatchers.resetMain()

    /** Keeps uiState subscribed (it's WhileSubscribed) and returns the latest value. */
    private fun TestScope.collect() = backgroundScope.launch { vm.uiState.collect {} }
    private val ui get() = vm.uiState.value

    @Test fun addDefaultsToTodayExpenseAndShowsOnlyActiveCategoriesOfType() = runTest(dispatcher) {
        collect(); vm = newVm(null); advanceUntilIdle()
        assertEquals(today, ui.form.date)
        assertEquals(EXPENSE, ui.form.type)
        assertFalse(ui.isEdit)
        assertEquals(listOf("milk", "groc"), ui.categories.map { it.id }) // no archived Pets, no income
        vm.setType(INCOME); advanceUntilIdle()
        assertEquals(listOf("salary"), ui.categories.map { it.id })
    }

    @Test fun saveDisabledUntilValid() = runTest(dispatcher) {
        collect(); vm = newVm(null); advanceUntilIdle()
        assertFalse(ui.canSave)
        vm.setAmount("250"); advanceUntilIdle()
        assertFalse(ui.canSave) // no category
        vm.setCategory("milk"); advanceUntilIdle()
        assertTrue(ui.canSave)
    }

    @Test fun zeroAndInvalidAmountsShowErrorAndBlockSave() = runTest(dispatcher) {
        collect(); vm = newVm(null); vm.setCategory("milk")
        assertFalse(vm.uiState.value.form.amountError) // untouched: no error yet
        listOf("0", "0.00", "abc", "1.234", "-5").forEach {
            vm.setAmount(it); advanceUntilIdle()
            assertTrue(it, ui.form.amountError)
            assertFalse(it, ui.canSave)
        }
        vm.setAmount("0.01"); advanceUntilIdle()
        assertFalse(ui.form.amountError)
        assertTrue(ui.canSave)
    }

    @Test fun invalidQuantityBlocksSave_blankIsFine() = runTest(dispatcher) {
        collect(); vm = newVm(null); vm.setAmount("60"); vm.setCategory("milk")
        vm.setQuantity("0"); advanceUntilIdle()
        assertTrue(ui.form.quantityError); assertFalse(ui.canSave)
        vm.setQuantity(""); advanceUntilIdle()
        assertTrue(ui.canSave)
    }

    @Test fun switchingTypeClearsCategory() = runTest(dispatcher) {
        collect(); vm = newVm(null); vm.setCategory("milk"); vm.setType(INCOME); advanceUntilIdle()
        assertNull(ui.form.categoryId)
    }

    @Test fun saveCreatesTransactionWithPaiseUtcDateAndFields() = runTest(dispatcher) {
        collect(); vm = newVm(null)
        vm.setAmount("1,250.50"); vm.setCategory("milk"); vm.setQuantity("2.5")
        vm.setDateFromPicker(Instant.parse("2026-10-14T00:00:00Z").toEpochMilli())
        vm.setDescription(" Morning milk "); vm.setNotes("n")
        vm.save(); advanceUntilIdle()
        assertTrue(ui.done)
        val t = txRepo.rows.value.single()
        assertEquals(125_050L, t.amountPaise)
        assertEquals(EXPENSE, t.type)
        assertEquals("milk", t.categoryId)
        assertEquals(2_500L, t.quantityMilli)
        assertEquals(LocalDate.of(2026, 10, 14), t.date)
        assertEquals(" Morning milk ", t.description) // repository trims
    }

    @Test fun rapidDoubleTapSavesOnce() = runTest(dispatcher) {
        collect(); vm = newVm(null); vm.setAmount("10"); vm.setCategory("milk")
        txRepo.gate = CompletableDeferred()
        vm.save(); advanceUntilIdle()
        assertTrue(ui.isSaving); assertFalse(ui.canSave)
        vm.save(); vm.save()
        txRepo.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, txRepo.adds)
    }

    @Test fun saveWithInvalidFormRevealsErrorsAndDoesNothing() = runTest(dispatcher) {
        collect(); vm = newVm(null); vm.save(); advanceUntilIdle()
        assertTrue(ui.form.amountError)
        assertFalse(ui.done)
        assertTrue(txRepo.rows.value.isEmpty())
    }

    private fun existing(category: String = "milk") = Transaction(
        id = "t1", type = EXPENSE, categoryId = category, occurrenceId = "occ", amountPaise = 6_050,
        quantityMilli = 1_000, unitRatePaise = 6_050, date = LocalDate.of(2026, 10, 1),
        description = "d", notes = "n", createdAt = Instant.parse("2026-10-01T03:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T03:00:00Z")
    )

    @Test fun editLoadsAndKeepsCreatedAtAndLinks() = runTest(dispatcher) {
        txRepo.rows.value = listOf(existing())
        collect(); vm = newVm("t1"); advanceUntilIdle()
        assertTrue(ui.isEdit)
        assertEquals("60.50", ui.form.amountText)
        assertEquals("1", ui.form.quantityText)
        assertEquals(LocalDate.of(2026, 10, 1), ui.form.date)
        vm.setAmount("70"); vm.save(); advanceUntilIdle()
        val t = txRepo.rows.value.single()
        assertEquals("t1", t.id)
        assertEquals(7_000L, t.amountPaise)
        assertEquals(Instant.parse("2026-10-01T03:00:00Z"), t.createdAt)
        assertEquals("occ", t.occurrenceId)
        assertEquals(7_000L, t.unitRatePaise) // recomputed: ₹70 / 1 L, not the stale ₹60.50
        assertTrue(ui.done)
    }

    @Test fun editKeepsArchivedCategoryVisible() = runTest(dispatcher) {
        txRepo.rows.value = listOf(existing(category = "pets"))
        collect(); vm = newVm("t1"); advanceUntilIdle()
        assertEquals(listOf("milk", "groc", "pets"), ui.categories.map { it.id })
        vm.setCategory("milk"); advanceUntilIdle()
        assertEquals(listOf("milk", "groc"), ui.categories.map { it.id })
    }

    @Test fun deleteRemovesAndCloses() = runTest(dispatcher) {
        txRepo.rows.value = listOf(existing())
        collect(); vm = newVm("t1"); advanceUntilIdle()
        vm.delete(); advanceUntilIdle()
        assertTrue(txRepo.rows.value.isEmpty())
        assertTrue(ui.done)
    }

    @Test fun deleteIsNoOpInAddMode() = runTest(dispatcher) {
        collect(); vm = newVm(null); vm.delete(); advanceUntilIdle()
        assertFalse(ui.done)
    }

    @Test fun missingTransactionClosesScreen() = runTest(dispatcher) {
        collect(); vm = newVm("nope"); advanceUntilIdle()
        assertTrue(ui.done)
    }

    @Test fun editRecomputesUnitRateFromAmountAndQuantity() = runTest(dispatcher) {
        txRepo.rows.value = listOf(existing())
        collect(); vm = newVm("t1"); advanceUntilIdle()
        vm.setAmount("100"); vm.setQuantity("3"); vm.save(); advanceUntilIdle()
        // 10000 paise / 3 = 3333.33 -> 3333 (half-up)
        assertEquals(3_333L, txRepo.rows.value.single().unitRatePaise)
    }

    @Test fun editClearingQuantityClearsUnitRate() = runTest(dispatcher) {
        txRepo.rows.value = listOf(existing())
        collect(); vm = newVm("t1"); advanceUntilIdle()
        vm.setQuantity(""); vm.save(); advanceUntilIdle()
        val t = txRepo.rows.value.single()
        assertNull(t.quantityMilli)
        assertNull(t.unitRatePaise)
    }

    @Test fun addWithQuantityStoresDerivedRate() = runTest(dispatcher) {
        collect(); vm = newVm(null)
        vm.setAmount("91"); vm.setCategory("milk"); vm.setQuantity("1.5"); vm.save(); advanceUntilIdle()
        assertEquals(6_067L, txRepo.rows.value.single().unitRatePaise) // 9100/1.5 = 6066.67 -> 6067
    }

    @Test fun failedDeleteShowsDeleteErrorNotSaveError() = runTest(dispatcher) {
        txRepo.rows.value = listOf(existing())
        txRepo.failDelete = true
        collect(); vm = newVm("t1"); advanceUntilIdle()
        vm.delete(); advanceUntilIdle()
        assertTrue(ui.deleteFailed)
        assertFalse(ui.saveFailed)
        assertFalse(ui.done)
        assertFalse(ui.isSaving)
        vm.setNotes("x"); advanceUntilIdle()
        assertFalse(ui.deleteFailed) // cleared on next edit
    }

    @Test fun restoresFormFromSavedStateWithoutReReadingRepository() = runTest(dispatcher) {
        txRepo.rows.value = listOf(existing())
        collect(); vm = newVm("t1"); advanceUntilIdle()
        assertEquals(1, txRepo.gets)
        vm.setType(INCOME); vm.setCategory("salary"); vm.setAmount("123.45")
        vm.setDateFromPicker(Instant.parse("2026-09-30T00:00:00Z").toEpochMilli())
        vm.setDescription("desc"); vm.setNotes("note"); vm.setQuantity("2"); advanceUntilIdle()

        // Simulate process death: only the SavedStateHandle's contents survive.
        val survived = SavedStateHandle(savedHandle.keys().associateWith { savedHandle.get<Any?>(it) })
        assertEquals("2026-09-30", survived.get<String>(AddEditTransactionViewModel.KEY_DATE))
        txRepo.gets = 0
        vm = newVm("ignored", survived)
        advanceUntilIdle()

        assertEquals(0, txRepo.gets) // no DB reload
        val f = ui.form
        assertEquals(INCOME, f.type)
        assertEquals("salary", f.categoryId)
        assertEquals("123.45", f.amountText)
        assertEquals(LocalDate.of(2026, 9, 30), f.date)
        assertEquals("desc", f.description)
        assertEquals("note", f.notes)
        assertEquals("2", f.quantityText)
        assertTrue(ui.isEdit)
        assertFalse(ui.isLoading)

        // Saving after restore still updates the same row and keeps createdAt.
        vm.save(); advanceUntilIdle()
        val t = txRepo.rows.value.single()
        assertEquals(12_345L, t.amountPaise)
        assertEquals(Instant.parse("2026-10-01T03:00:00Z"), t.createdAt)
    }

    @Test fun addModeRestoresWithoutTransactionId() = runTest(dispatcher) {
        collect(); vm = newVm(null)
        vm.setAmount("5"); advanceUntilIdle()
        val survived = SavedStateHandle(savedHandle.keys().associateWith { savedHandle.get<Any?>(it) })
        vm = newVm(null, survived); advanceUntilIdle()
        assertEquals("5", ui.form.amountText)
        assertFalse(ui.isEdit)
        assertEquals(0, txRepo.gets)
    }
}
