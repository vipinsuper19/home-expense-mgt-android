package com.dhama.homefinance.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionFilter
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.repository.RoomTransactionListRepository
import java.time.Instant
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
class TransactionListDaoTest {
    private lateinit var db: AppDatabase
    private val repo = lazy { RoomTransactionListRepository(db.transactionListDao()) }
    private val oct = YearMonth.of(2026, 10)

    private val milk = DefaultCategories.MILK_ID
    private val groceries = DefaultCategories.idFor("groceries")
    private val salary = DefaultCategories.idFor("salary")
    private val rent = DefaultCategories.SHOP_RENT_ID

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .addCallback(SeedDefaultsCallback).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private suspend fun tx(
        date: String, amount: Long, type: TransactionType = EXPENSE, category: String = milk,
        description: String = "", notes: String = "", createdAt: Long = 0
    ) = db.transactionDao().insert(
        Transaction(
            type = type, categoryId = category, amountPaise = amount, date = LocalDate.parse(date),
            description = description, notes = notes, createdAt = Instant.ofEpochMilli(createdAt)
        )
    )

    private suspend fun ids(month: YearMonth = oct, filter: TransactionFilter = TransactionFilter()) =
        repo.value.items(month, filter).first().map { it.date.toString() to it.amountPaise }

    @Test fun monthBoundsAndNewestFirstOrder() = runTest {
        tx("2026-09-30", 1)
        tx("2026-10-01", 10, createdAt = 1)
        tx("2026-10-01", 11, createdAt = 2)
        tx("2026-10-31", 100)
        tx("2026-11-01", 1000)
        assertEquals(listOf("2026-10-31" to 100L, "2026-10-01" to 11L, "2026-10-01" to 10L), ids())
        assertEquals(listOf("2026-09-30" to 1L), ids(YearMonth.of(2026, 9)))
    }

    @Test fun typeAndCategoryFilters() = runTest {
        tx("2026-10-02", 6_000, EXPENSE, milk)
        tx("2026-10-03", 50_000, EXPENSE, groceries)
        tx("2026-10-05", 5_000_000, INCOME, salary)
        assertEquals(listOf(5_000_000L), ids(filter = TransactionFilter(type = INCOME)).map { it.second })
        assertEquals(listOf(50_000L, 6_000L), ids(filter = TransactionFilter(type = EXPENSE)).map { it.second })
        assertEquals(listOf(6_000L), ids(filter = TransactionFilter(categoryId = milk)).map { it.second })
        assertTrue(ids(filter = TransactionFilter(type = INCOME, categoryId = milk)).isEmpty())
    }

    @Test fun searchMatchesDescriptionAndNotes_isCaseInsensitiveAndSafe() = runTest {
        tx("2026-10-02", 1, description = "Morning milk")
        tx("2026-10-03", 2, notes = "100% discount")
        tx("2026-10-04", 3, description = "groceries")
        tx("2026-10-05", 4, description = "a_b")
        assertEquals(listOf(1L), ids(filter = TransactionFilter(search = "MILK")).map { it.second })
        assertEquals(listOf(2L), ids(filter = TransactionFilter(search = "100%")).map { it.second })
        assertEquals(listOf(4L), ids(filter = TransactionFilter(search = "a_b")).map { it.second })
        // Wildcards and quotes are literal, not SQL.
        assertEquals(listOf(2L), ids(filter = TransactionFilter(search = "%")).map { it.second }) // literal %, not "match all"
        assertTrue(ids(filter = TransactionFilter(search = "m%k")).isEmpty())
        assertTrue(ids(filter = TransactionFilter(search = "m_lk")).isEmpty())
        assertTrue(ids(filter = TransactionFilter(search = "\" OR 1=1 --")).isEmpty())
        assertTrue(ids(filter = TransactionFilter(search = "   ")).size == 4)
    }

    @Test fun dailyAndMonthTotalsFollowFilters() = runTest {
        tx("2026-10-01", 100, INCOME, salary)
        tx("2026-10-01", 30, EXPENSE, milk)
        tx("2026-10-02", 7, EXPENSE, milk, description = "extra")
        val daily = repo.value.dailyTotals(oct, TransactionFilter()).first()
        assertEquals("2026-10-02", daily[0].date.toString())
        assertEquals(0L, daily[0].incomePaise); assertEquals(7L, daily[0].expensePaise)
        assertEquals(100L, daily[1].incomePaise); assertEquals(30L, daily[1].expensePaise)

        val filtered = repo.value.dailyTotals(oct, TransactionFilter(search = "extra")).first()
        assertEquals(1, filtered.size); assertEquals(7L, filtered[0].expensePaise)

        val totals = repo.value.totals(oct, TransactionFilter(type = EXPENSE)).first()
        assertEquals(0L, totals.incomePaise); assertEquals(37L, totals.expensePaise); assertEquals(2, totals.transactionCount)
    }

    @Test fun emptyMonth() = runTest {
        assertTrue(ids().isEmpty())
        assertTrue(repo.value.dailyTotals(oct, TransactionFilter()).first().isEmpty())
        assertEquals(0, repo.value.totals(oct, TransactionFilter()).first().transactionCount)
    }

    @Test fun filterCategoriesAreActivePlusUsedInMonth() = runTest {
        val archived = Category(id = "pets", name = "Pets", type = EXPENSE, icon = "x", isActive = false, sortOrder = 99)
        db.categoryDao().insert(archived)
        tx("2026-10-02", 100, EXPENSE, "pets")
        val cats = repo.value.filterCategories(oct, EXPENSE).first()
        assertTrue(cats.any { it.id == "pets" })                       // used, though archived
        assertTrue(cats.any { it.id == milk })                         // active
        assertTrue(cats.none { it.type == INCOME })                    // type filter
        assertTrue(repo.value.filterCategories(YearMonth.of(2026, 9), EXPENSE).first().none { it.id == "pets" })
    }

    @Test fun leapYearFebruaryBounds() = runTest {
        tx("2028-02-29", 29)
        tx("2028-03-01", 1)
        assertEquals(listOf("2028-02-29" to 29L), ids(YearMonth.of(2028, 2)))
    }

    @Test fun likePatternEscapes() {
        assertEquals(null, TransactionFilter.likePattern("  "))
        assertEquals("%milk%", TransactionFilter.likePattern(" milk "))
        assertEquals("%100\\%%", TransactionFilter.likePattern("100%"))
        assertEquals("%a\\_b%", TransactionFilter.likePattern("a_b"))
        assertEquals("%a\\\\b%", TransactionFilter.likePattern("a\\b"))
    }
}
