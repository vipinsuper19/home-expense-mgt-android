package com.dhama.homenotestracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RecurringType {
    NONE, DAILY, WEEKLY, MONTHLY
}

enum class Priority {
    LOW, MEDIUM, HIGH
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val categoryId: Long,
    val priority: Priority,
    val recurringType: RecurringType,
    val reminderTime: Long?, // Timestamp for the next reminder
    val dueDate: Long?, // Timestamp for the due date
    val notes: String,
    val isCompleted: Boolean = false,
    val notificationEnabled: Boolean = true,
    val color: Int,
    val icon: String,
    val createdAt: Long = System.currentTimeMillis()
)
