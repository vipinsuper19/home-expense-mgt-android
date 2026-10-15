package com.dhama.homefinance.data.local

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Placeholder so the database has at least one table until the
 * Category/Transaction entities land (next PR), which will replace it.
 */
@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey val key: String,
    val value: String
)

@Database(
    entities = [AppMeta::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase()
