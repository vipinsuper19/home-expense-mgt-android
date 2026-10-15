package com.dhama.homefinance.data.model

/** Direction of money. Amounts are always positive; never use negative amounts for expenses. */
enum class TransactionType {
    INCOME, EXPENSE;

    companion object {
        /** Shared parser for saved-state / nav-argument strings; null or unknown -> null. */
        fun parseOrNull(value: String?): TransactionType? =
            value?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
