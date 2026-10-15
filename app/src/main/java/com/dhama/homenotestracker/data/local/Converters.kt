package com.dhama.homenotestracker.data.local

import androidx.room.TypeConverter
import com.dhama.homenotestracker.data.model.Priority
import com.dhama.homenotestracker.data.model.RecurringType

class Converters {
    @TypeConverter
    fun fromPriority(priority: Priority): String {
        return priority.name
    }

    @TypeConverter
    fun toPriority(priority: String): Priority {
        return Priority.valueOf(priority)
    }

    @TypeConverter
    fun fromRecurringType(recurringType: RecurringType): String {
        return recurringType.name
    }

    @TypeConverter
    fun toRecurringType(recurringType: String): RecurringType {
        return RecurringType.valueOf(recurringType)
    }
}
