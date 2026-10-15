package com.dhama.homenotestracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val dueDate: Long,
    val isPaid: Boolean = false,
    val categoryId: Long,
    val recurringDay: Int? = null, // Day of month if recurring
    val notes: String = ""
)
