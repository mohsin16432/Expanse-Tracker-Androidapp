package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.TransactionDao
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val dao: TransactionDao,
) {
    fun observeLatest(limit: Int = 50): Flow<List<TransactionEntity>> = dao.observeLatest(limit)

    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>> =
        dao.observeBetween(fromMillis, toMillis)

    fun searchBetween(query: String, fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>> =
        dao.searchBetween(query = query.trim().lowercase(), fromMillis = fromMillis, toMillis = toMillis)

    suspend fun insertIgnore(tx: TransactionEntity): Boolean = dao.insertIgnore(tx) != -1L

    suspend fun sumOutgoingMinor(fromMillis: Long, toMillis: Long): Long =
        dao.sumAmountMinorBetween(direction = TransactionDirection.OUT.name, fromMillis = fromMillis, toMillis = toMillis) ?: 0L

    suspend fun sumIncomingMinor(fromMillis: Long, toMillis: Long): Long =
        dao.sumAmountMinorBetween(direction = TransactionDirection.IN.name, fromMillis = fromMillis, toMillis = toMillis) ?: 0L

    suspend fun getAll(): List<TransactionEntity> = dao.getAll()
    suspend fun replaceAll(items: List<TransactionEntity>) = dao.insertAllReplace(items)
    suspend fun upsert(tx: TransactionEntity) = dao.upsert(tx)
    suspend fun deleteBySourceId(sourceId: String) = dao.deleteBySourceId(sourceId)
    suspend fun countAll(): Long = dao.countAll()
    suspend fun deleteById(id: String) = dao.deleteById(id)
    suspend fun deleteImportedTransactions() = dao.deleteImportedTransactions()
}
