package com.dhama.homefinance.data.repository

import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeActive(type: TransactionType): Flow<List<Category>>
    suspend fun get(id: String): Category?
    suspend fun add(category: Category)
    suspend fun update(category: Category)
    /** Categories are archived, not deleted, so history keeps its labels. */
    suspend fun setActive(id: String, active: Boolean)
    /** Inserts any defaults that are missing. Safe to call on every start. */
    suspend fun seedDefaults()
}
