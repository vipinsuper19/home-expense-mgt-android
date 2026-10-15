package com.dhama.homefinance.data.repository

import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.TransactionType
import com.dhama.homefinance.data.model.TransactionType.EXPENSE
import com.dhama.homefinance.data.model.TransactionType.INCOME
import java.util.UUID

/**
 * Built-in categories. IDs are name-based UUIDs (v3) so they are stable across
 * installs, which keeps seeding idempotent and backups portable.
 */
object DefaultCategories {
    private data class Spec(val type: TransactionType, val key: String, val name: String, val icon: String)

    private val specs = listOf(
        Spec(EXPENSE, "groceries", "Groceries", "ShoppingCart"),
        Spec(EXPENSE, "milk", "Milk", "LocalDrink"),
        Spec(EXPENSE, "bills", "Bills & Utilities", "Receipt"),
        Spec(EXPENSE, "rent_paid", "Rent", "Home"),
        Spec(EXPENSE, "fuel", "Fuel & Transport", "LocalGasStation"),
        Spec(EXPENSE, "medical", "Medical", "LocalHospital"),
        Spec(EXPENSE, "education", "Education", "School"),
        Spec(EXPENSE, "household", "Household", "Chair"),
        Spec(EXPENSE, "eating_out", "Eating Out", "Restaurant"),
        Spec(EXPENSE, "other_expense", "Other", "MoreHoriz"),
        Spec(INCOME, "salary", "Salary", "Work"),
        Spec(INCOME, "shop_rent", "Shop Rent", "Storefront"),
        Spec(INCOME, "business", "Business", "BusinessCenter"),
        Spec(INCOME, "interest", "Interest", "Savings"),
        Spec(INCOME, "other_income", "Other", "MoreHoriz"),
    )

    fun idFor(key: String): String =
        UUID.nameUUIDFromBytes("com.dhama.homefinance:category:$key".toByteArray()).toString()

    val all: List<Category> = specs.mapIndexed { index, s ->
        Category(
            id = idFor(s.key),
            name = s.name,
            type = s.type,
            icon = s.icon,
            isDefault = true,
            isActive = true,
            sortOrder = index
        )
    }

    val MILK_ID: String = idFor("milk")
    val SHOP_RENT_ID: String = idFor("shop_rent")
}
