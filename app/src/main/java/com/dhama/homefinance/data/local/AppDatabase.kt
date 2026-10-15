package com.dhama.homefinance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dhama.homefinance.data.repository.DefaultCategories
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.Transaction

/**
 * Still version 1: nothing has shipped, so the schema is edited in place
 * (the PR-A placeholder table is gone). Migrations start with the first release.
 * The exported schema lives in app/schemas/.
 */
@Database(
    entities = [Category::class, Transaction::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
}

/**
 * Seeds the default categories inside the database-creation transaction, so the very
 * first query on a fresh install already sees them (no empty first-launch list).
 */
object SeedDefaultsCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultCategories.insertInto(db)
    }
}
