package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.CategoryDao
import com.trae.expensetracker.data.model.CategoryEntity
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val dao: CategoryDao,
) {
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    suspend fun upsert(category: CategoryEntity) = dao.upsert(category)
    suspend fun getAll(): List<CategoryEntity> = dao.getAll()
    suspend fun replaceAll(items: List<CategoryEntity>) = dao.insertAllReplace(items)
    suspend fun delete(id: String) = dao.deleteById(id)

    /** Next free sort order, so newly created categories land at the end of the list. */
    suspend fun nextSortOrder(): Int = (dao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
}
