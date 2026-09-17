package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.BudgetDao
import com.trae.expensetracker.data.model.BudgetEntity
import com.trae.expensetracker.ingest.parsers.ParseUtils
import kotlinx.coroutines.flow.Flow

class BudgetRepository(
    private val dao: BudgetDao,
) {
    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()

    suspend fun getAll(): List<BudgetEntity> = dao.getAll()

    /** Sets (or replaces) the limit for a category. A blank category sets the overall budget. */
    suspend fun setLimit(categoryId: String?, limitMinor: Long, currency: String = "PKR") {
        val key = normalizedKey(categoryId)
        val existing = dao.findByCategory(key)
        dao.upsert(
            BudgetEntity(
                id = existing?.id ?: idFor(key),
                categoryId = key,
                limitMinor = limitMinor.coerceAtLeast(0L),
                currency = currency,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                enabled = true,
            )
        )
    }

    suspend fun setEnabled(budget: BudgetEntity, enabled: Boolean) = dao.upsert(budget.copy(enabled = enabled))

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun replaceAll(items: List<BudgetEntity>) = dao.insertAllReplace(items)

    suspend fun clearAll() = dao.clearAll()

    /** Blank category is stored as an empty string so the unique lookup stays simple. */
    private fun normalizedKey(categoryId: String?): String = categoryId?.trim().orEmpty()

    private fun idFor(key: String): String =
        ParseUtils.sha256Hex("budget|$key").take(24)
}
