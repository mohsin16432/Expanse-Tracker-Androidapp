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

    fun observeInternalTransfers(): Flow<List<TransactionEntity>> = dao.observeInternalTransfers()

    fun observeFlaggedDuplicates(): Flow<List<TransactionEntity>> = dao.observeFlaggedDuplicates()

    fun observeFlaggedRefunds(): Flow<List<TransactionEntity>> = dao.observeFlaggedRefunds()

    suspend fun insertIgnore(tx: TransactionEntity): Boolean = dao.insertIgnore(tx) != -1L

    suspend fun findById(id: String): TransactionEntity? = dao.findById(id)

    suspend fun findCandidates(
        direction: TransactionDirection,
        amountMinor: Long,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
    ): List<TransactionEntity> = dao.findCandidates(
        direction = direction.name,
        amountMinor = amountMinor,
        currency = currency,
        fromMillis = fromMillis,
        toMillis = toMillis,
        anchorMillis = anchorMillis,
    )

    suspend fun findDuplicateCandidates(
        amountMinor: Long,
        direction: TransactionDirection,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
        excludeId: String,
    ): List<TransactionEntity> = dao.findDuplicateCandidates(
        amountMinor = amountMinor,
        direction = direction.name,
        currency = currency,
        fromMillis = fromMillis,
        toMillis = toMillis,
        anchorMillis = anchorMillis,
        excludeId = excludeId,
    )

    suspend fun findRefundCandidate(
        amountMinor: Long,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
        excludeId: String,
    ): TransactionEntity? = dao.findRefundCandidate(
        amountMinor = amountMinor,
        currency = currency,
        fromMillis = fromMillis,
        toMillis = toMillis,
        anchorMillis = anchorMillis,
        excludeId = excludeId,
    )

    suspend fun setRefundOf(id: String, originalId: String?) = dao.setRefundOf(id, originalId)

    suspend fun setTransferGroup(id: String, groupId: String?) = dao.setTransferGroup(id, groupId)

    suspend fun setDuplicateOf(id: String, originalId: String?) = dao.setDuplicateOf(id, originalId)

    /** Clears duplicate flags for one source pair. */
    suspend fun clearDuplicateFlagsForPair(originalId: String, duplicateSourceId: String) =
        dao.clearDuplicateFlagsForPair(originalId, duplicateSourceId)

    suspend fun setCategory(id: String, categoryId: String?) = dao.setCategory(id, categoryId)

    suspend fun setMerchant(id: String, merchantRaw: String, merchantNormalized: String) =
        dao.setMerchant(id, merchantRaw, merchantNormalized)

    suspend fun sumOutgoingMinor(fromMillis: Long, toMillis: Long): Long =
        dao.sumAmountMinorBetween(direction = TransactionDirection.OUT.name, fromMillis = fromMillis, toMillis = toMillis) ?: 0L

    suspend fun sumIncomingMinor(fromMillis: Long, toMillis: Long): Long =
        dao.sumAmountMinorBetween(direction = TransactionDirection.IN.name, fromMillis = fromMillis, toMillis = toMillis) ?: 0L

    suspend fun getAll(): List<TransactionEntity> = dao.getAll()
    suspend fun replaceAll(items: List<TransactionEntity>) = dao.insertAllReplace(items)
    suspend fun upsert(tx: TransactionEntity) = dao.upsert(tx)
    suspend fun deleteBySourceId(sourceId: String) = dao.deleteBySourceId(sourceId)
    suspend fun countAll(): Long = dao.countAll()
    /** @return true when a row was actually removed. */
    suspend fun deleteById(id: String): Boolean = dao.deleteById(id) > 0
    suspend fun deleteImportedTransactions() = dao.deleteImportedTransactions()
}
