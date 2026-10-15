package com.dhama.homenotestracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groceries")
data class Grocery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val quantity: Double,
    val unit: String, // e.g., kg, liters, units
    val price: Double,
    val isRecurring: Boolean = false,
    val lastPurchasedDate: Long? = null,
    val notes: String = ""
)
