package com.dhama.homenotestracker.data.repository

import com.dhama.homenotestracker.data.local.GroceryDao
import com.dhama.homenotestracker.data.model.Grocery
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroceryRepository @Inject constructor(
    private val groceryDao: GroceryDao
) {
    fun getAllGroceries(): Flow<List<Grocery>> = groceryDao.getAllGroceries()

    suspend fun getGroceryById(id: Long): Grocery? = groceryDao.getGroceryById(id)

    suspend fun insertGrocery(grocery: Grocery): Long = groceryDao.insertGrocery(grocery)

    suspend fun updateGrocery(grocery: Grocery) = groceryDao.updateGrocery(grocery)

    suspend fun deleteGrocery(grocery: Grocery) = groceryDao.deleteGrocery(grocery)
}
