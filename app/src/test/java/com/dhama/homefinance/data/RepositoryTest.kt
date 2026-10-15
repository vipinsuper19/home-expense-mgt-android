package com.dhama.homefinance.data

import com.dhama.homefinance.data.local.CategoryDao
import com.dhama.homefinance.data.local.TransactionDao
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.repository.RoomCategoryRepository
import com.dhama.homefinance.data.repository.RoomTransactionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In-memory DAO fakes mimicking the Room conflict strategies we rely on. */
private class FakeCategoryDao : CategoryDao {
    val rows = MutableStateFlow<List<Category>>(emptyList())
    override fun observeAll() = rows
    override fun observeActiveByType(type: TransactionType) =
        rows.map { l -> l.filter { it.type == type && it.isActive } }
    override suspend fun getById(id: String) = rows.value.find { it.id == id }
    override fun observeById(id: String) = rows.map { l -> l.find { it.id == id } }
    override suspend fun count() = rows.value.size
    private fun clashes(c: Category) =
        rows.value.any { it.id == c.id || (it.type == c.type && it.name == c.name) }
    override suspend fun insert(category: Category) {
        check(!clashes(category)) { "constraint" }
        rows.value += category
    }
    override suspend fun insertAllIgnoringExisting(categories: List<Category>) {
        categories.forEach { if (!clashes(it)) rows.value += it }
    }
    override suspend fun update(category: Category) {
        rows.value = rows.value.map { if (it.id == category.id) category else it }
    }
    override suspend fun setActive(id: String, active: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(isActive = active) else it }
    }
}

private class FakeTransactionDao : TransactionDao {
    val rows = MutableStateFlow<List<Transaction>>(emptyList())
    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>> =
        rows.map { l -> l.filter { it.date in from..to }.sortedByDescending { it.date } }
    override fun observeRecent(limit: Int) = rows.map { it.take(limit) }
    override suspend fun getById(id: String) = rows.value.find { it.id == id }
    override fun observeById(id: String) = rows.map { l -> l.find { it.id == id } }
    override suspend fun countForCategory(categoryId: String) = rows.value.count { it.categoryId == categoryId }
    override suspend fun insert(transaction: Transaction) { rows.value += transaction }
    override suspend fun update(transaction: Transaction) {
        rows.value = rows.value.map { if (it.id == transaction.id) transaction else it }
    }
    override suspend fun deleteById(id: String) { rows.value = rows.value.filterNot { it.id == id } }
}

class RepositoryTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    @Test fun seedingIsIdempotentAndKeepsUserChanges() = runTest {
        val dao = FakeCategoryDao()
        val repo = RoomCategoryRepository(dao)
        repo.seedDefaults()
        assertEquals(DefaultCategories.all.size, dao.count())
        repo.setActive(DefaultCategories.MILK_ID, false)
        repo.seedDefaults()
        assertEquals(DefaultCategories.all.size, dao.count())
        assertEquals(false, dao.getById(DefaultCategories.MILK_ID)!!.isActive)
    }

    @Test fun categoryNameIsTrimmedAndRequired() = runTest {
        val repo = RoomCategoryRepository(FakeCategoryDao())
        val c = Category(name = "  Pets ", type = TransactionType.EXPENSE, icon = "Pets")
        repo.add(c)
        assertEquals("Pets", repo.get(c.id)!!.name)
        val result = runCatching { repo.add(Category(name = "  ", type = TransactionType.EXPENSE, icon = "x")) }
        assertEquals(true, result.isFailure)
    }

    @Test fun updatePreservesCreatedAtAndBumpsUpdatedAt() = runTest {
        val t0 = Instant.parse("2026-10-15T10:00:00Z")
        val clock = MutableClock(t0)
        val repo = RoomTransactionRepository(FakeTransactionDao(), clock)
        val tx = Transaction(
            type = TransactionType.INCOME, categoryId = DefaultCategories.SHOP_RENT_ID,
            amountPaise = 1_500_000, date = LocalDate.of(2026, 10, 1), description = " Oct rent "
        )
        repo.add(tx)
        val saved = repo.get(tx.id)!!
        assertEquals(t0, saved.createdAt)
        assertEquals("Oct rent", saved.description)

        clock.now = t0.plusSeconds(60)
        repo.update(saved.copy(amountPaise = 1_200_000, createdAt = Instant.EPOCH))
        val updated = repo.get(tx.id)!!
        assertEquals(t0, updated.createdAt)
        assertEquals(t0.plusSeconds(60), updated.updatedAt)
        assertEquals(1_200_000L, updated.amountPaise)

        repo.delete(tx.id)
        assertNull(repo.get(tx.id))
    }
}
