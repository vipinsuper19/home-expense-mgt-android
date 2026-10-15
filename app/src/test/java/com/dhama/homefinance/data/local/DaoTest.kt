package com.dhama.homefinance.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.DefaultCategories
import java.time.Instant
import java.time.LocalDate
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

/** Real Room + SQLite (in-memory) via Robolectric, with the production seeding callback. */
@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: AppDatabase
    private val categories get() = db.categoryDao()
    private val transactions get() = db.transactionDao()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .addCallback(SeedDefaultsCallback)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private fun tx(date: LocalDate, amount: Long, categoryId: String = DefaultCategories.MILK_ID, createdAt: Long = 0) =
        Transaction(
            type = EXPENSE, categoryId = categoryId, amountPaise = amount, date = date,
            createdAt = Instant.ofEpochMilli(createdAt)
        )

    @Test fun freshDatabaseAlreadyHasDefaultsOnFirstRead() = runTest {
        // First ever query on a brand-new DB: must not be empty.
        val all = categories.observeAll().first()
        assertEquals(DefaultCategories.all.size, all.size)
        assertEquals(DefaultCategories.all.toSet(), all.toSet())
        val income = categories.observeActiveByType(INCOME).first().map { it.name }
        assertTrue("Shop Rent" in income && "Salary" in income)
    }

    @Test fun reseedingDoesNotDuplicateOrReactivate() = runTest {
        categories.setActive(DefaultCategories.MILK_ID, false)
        categories.insertAllIgnoringExisting(DefaultCategories.all)
        assertEquals(DefaultCategories.all.size, categories.count())
        assertEquals(false, categories.getById(DefaultCategories.MILK_ID)!!.isActive)
        assertTrue(categories.observeActiveByType(EXPENSE).first().none { it.id == DefaultCategories.MILK_ID })
    }

    @Test(expected = SQLiteConstraintException::class)
    fun duplicateNamePerTypeRejected() = runTest {
        categories.insert(Category(name = "Milk", type = EXPENSE, icon = "x"))
    }

    @Test fun sameNameAllowedAcrossTypes() = runTest {
        // "Other" exists for both types in the defaults.
        assertEquals(2, categories.observeAll().first().count { it.name == "Other" })
    }

    @Test(expected = SQLiteConstraintException::class)
    fun transactionRequiresExistingCategory() = runTest {
        transactions.insert(tx(LocalDate.of(2026, 10, 1), 100, categoryId = "missing"))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun categoryWithTransactionsCannotBeDeleted() = runTest {
        transactions.insert(tx(LocalDate.of(2026, 10, 1), 100))
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM categories WHERE id = ?", arrayOf(DefaultCategories.MILK_ID)
        )
    }

    @Test fun betweenIsInclusiveAndOrderedNewestFirst() = runTest {
        val sep30 = tx(LocalDate.of(2026, 9, 30), 1)
        val oct1 = tx(LocalDate.of(2026, 10, 1), 2, createdAt = 1)
        val oct1Later = tx(LocalDate.of(2026, 10, 1), 3, createdAt = 2)
        val oct31 = tx(LocalDate.of(2026, 10, 31), 4)
        val nov1 = tx(LocalDate.of(2026, 11, 1), 5)
        listOf(sep30, oct1, oct1Later, oct31, nov1).forEach { transactions.insert(it) }

        val october = transactions.observeBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).first()
        assertEquals(listOf(oct31.id, oct1Later.id, oct1.id), october.map { it.id })
    }

    @Test fun roundTripsAllFieldsAndDeletes() = runTest {
        // scheduleId now has a foreign key; give it a parent schedule.
        db.recurringDao().insertSchedule(
            com.dhama.homefinance.data.model.RecurringSchedule(
                id = "s", name = "s", type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID, amountPaise = 1,
                frequency = com.dhama.homefinance.data.model.Frequency.DAILY, startDate = LocalDate.of(2024, 1, 1)
            )
        )
        // occurrenceId has a foreign key too.
        db.occurrenceDao().insertIgnoringExisting(
            com.dhama.homefinance.data.model.ScheduledOccurrence(
                id = "o", scheduleId = "s", type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID,
                dueDate = LocalDate.of(2024, 2, 29), expectedAmountPaise = 1
            )
        )
        val t = Transaction(
            type = INCOME, categoryId = DefaultCategories.SHOP_RENT_ID, scheduleId = "s", occurrenceId = "o",
            amountPaise = 1_234_567_89L, quantityMilli = 1500, unitRatePaise = 6000,
            description = "d", date = LocalDate.of(2024, 2, 29), notes = "n",
            createdAt = Instant.ofEpochMilli(1), updatedAt = Instant.ofEpochMilli(2)
        )
        transactions.insert(t)
        assertEquals(t, transactions.getById(t.id))
        assertEquals(1, transactions.countForCategory(DefaultCategories.SHOP_RENT_ID))
        transactions.deleteById(t.id)
        assertNull(transactions.getById(t.id))
    }
}
