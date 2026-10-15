package com.dhama.homefinance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Transaction

/**
 * Still version 1: nothing has shipped, so the schema is edited in place
 * (the PR-A placeholder table is gone). Migrations start with the first release.
 */
@Database(
    entities = [Category::class, Transaction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
}
