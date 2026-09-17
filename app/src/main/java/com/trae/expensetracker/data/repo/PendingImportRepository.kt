package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.PendingImportDao
import com.trae.expensetracker.data.model.PendingImportEntity
import kotlinx.coroutines.flow.Flow

class PendingImportRepository(
    private val dao: PendingImportDao,
) {
    fun observeNeedsReview(): Flow<List<PendingImportEntity>> = dao.observeNeedsReview()
    fun observeNeedsReviewCount(): Flow<Int> = dao.observeNeedsReviewCount()
    suspend fun upsert(item: PendingImportEntity) = dao.upsert(item)
    suspend fun remove(id: String) = dao.deleteById(id)
    suspend fun clearAll() = dao.clearAll()
}

