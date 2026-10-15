package com.dhama.homefinance.data

import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.repository.DefaultCategories
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesTest {
    @Test fun idsAreStableUuids() {
        assertEquals(DefaultCategories.idFor("milk"), DefaultCategories.MILK_ID)
        assertEquals(DefaultCategories.all, DefaultCategories.all) // deterministic
        DefaultCategories.all.forEach { UUID.fromString(it.id) }
        assertEquals(DefaultCategories.all.size, DefaultCategories.all.map { it.id }.toSet().size)
    }

    @Test fun namesUniquePerType() {
        val keys = DefaultCategories.all.map { it.type to it.name }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun coversPrdExamplesForBothTypes() {
        val byType = DefaultCategories.all.groupBy({ it.type }, { it.name })
        assertTrue(byType.getValue(TransactionType.EXPENSE).containsAll(listOf("Groceries", "Milk")))
        assertTrue(byType.getValue(TransactionType.INCOME).containsAll(listOf("Salary", "Shop Rent")))
        assertTrue(DefaultCategories.all.all { it.isDefault && it.isActive })
    }
}
