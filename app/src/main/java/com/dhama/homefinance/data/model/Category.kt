package com.dhama.homefinance.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "categories",
    indices = [Index(value = ["type", "name"], unique = true)]
)
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TransactionType,
    /** Material icon name, resolved in the UI layer. */
    val icon: String,
    val isDefault: Boolean = false,
    /** Inactive categories are hidden from pickers but kept for history. */
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)
