package com.dhama.homefinance.data.repository

import com.dhama.homefinance.data.local.CategoryDao
import com.dhama.homefinance.data.model.Category
import com.dhama.homefinance.data.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val dao: CategoryDao
) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> = dao.observeAll()
    override fun observeActive(type: TransactionType) = dao.observeActiveByType(type)
    override suspend fun get(id: String) = dao.getById(id)
    override suspend fun add(category: Category) = dao.insert(category.normalized())
    override suspend fun update(category: Category) = dao.update(category.normalized())
    override suspend fun setActive(id: String, active: Boolean) = dao.setActive(id, active)
    override suspend fun seedDefaults() = dao.insertAllIgnoringExisting(DefaultCategories.all)

    private fun Category.normalized(): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name is required" }
        return copy(name = trimmed)
    }
}
