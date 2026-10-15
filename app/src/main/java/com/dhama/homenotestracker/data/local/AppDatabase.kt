package com.dhama.homenotestracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dhama.homenotestracker.data.model.Bill
import com.dhama.homenotestracker.data.model.Category
import com.dhama.homenotestracker.data.model.Grocery
import com.dhama.homenotestracker.data.model.Task

@Database(
    entities = [Task::class, Category::class, Grocery::class, Bill::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun groceryDao(): GroceryDao
    abstract fun billDao(): BillDao
}
