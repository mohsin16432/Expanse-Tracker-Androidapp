package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.TransactionSplitDao
import com.trae.expensetracker.data.model.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow

class TransactionSplitRepository(
    private val dao: TransactionSplitDao,
) {
    fun observeAll(): Flow<List<TransactionSplitEntity>> = dao.observeAll()

    suspend fun getAll(): List<TransactionSplitEntity> = dao.getAll()

    suspend fun forTransaction(transactionId: String): List<TransactionSplitEntity> =
        dao.forTransaction(transactionId)

    /** Replaces the split set for one transaction. An empty list clears the split. */
    suspend fun replace(transactionId: String, splits: List<TransactionSplitEntity>) {
        dao.deleteForTransaction(transactionId)
        if (splits.isNotEmpty()) dao.insertAll(splits)
    }

    suspend fun deleteForTransaction(transactionId: String) = dao.deleteForTransaction(transactionId)

    suspend fun replaceAll(items: List<TransactionSplitEntity>) = dao.insertAll(items)

    suspend fun clearAll() = dao.clearAll()
}
