package com.dhama.homefinance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type AND isActive = 1 ORDER BY sortOrder, name")
    fun observeActiveByType(type: TransactionType): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeById(id: String): Flow<Category?>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: Category)

    /** Used for seeding: existing rows (same id or same type+name) are left alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringExisting(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Query("UPDATE categories SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)
}
