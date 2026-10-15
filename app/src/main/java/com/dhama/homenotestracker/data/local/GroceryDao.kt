package com.dhama.homenotestracker.data.local

import androidx.room.*
import com.dhama.homenotestracker.data.model.Grocery
import kotlinx.coroutines.flow.Flow

@Dao
interface GroceryDao {
    @Query("SELECT * FROM groceries")
    fun getAllGroceries(): Flow<List<Grocery>>

    @Query("SELECT * FROM groceries WHERE id = :id")
    suspend fun getGroceryById(id: Long): Grocery?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrocery(grocery: Grocery): Long

    @Update
    suspend fun updateGrocery(grocery: Grocery)

    @Delete
    suspend fun deleteGrocery(grocery: Grocery)
}
