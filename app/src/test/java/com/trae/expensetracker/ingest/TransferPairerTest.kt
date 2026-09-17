package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.data.repo.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private object UnsupportedDao : com.trae.expensetracker.data.db.TransactionDao {
    override suspend fun insertIgnore(tx: TransactionEntity): Long = 1L
    override suspend fun insertAllReplace(items: List<TransactionEntity>) = Unit
    override suspend fun upsert(tx: TransactionEntity) = Unit
    override fun observeLatest(limit: Int): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList<TransactionEntity>())
    override fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList<TransactionEntity>())
    override fun searchBetween(query: String, fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList<TransactionEntity>())
    override suspend fun sumAmountMinorBetween(direction: String, fromMillis: Long, toMillis: Long): Long? = 0L
    override suspend fun countAll(): Long = 0L
    override suspend fun getAll(): List<TransactionEntity> = emptyList()
    override suspend fun clearAll() = Unit
    override suspend fun deleteBySourceId(sourceId: String) = Unit
    override suspend fun deleteById(id: String): Int = 0
    override suspend fun deleteImportedTransactions() = Unit
    override suspend fun findCandidates(
        direction: String,
        amountMinor: Long,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
    ): List<TransactionEntity> = emptyList()
    override suspend fun findById(id: String): TransactionEntity? = null
    override fun observeByTransferGroup(groupId: String): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList<TransactionEntity>())
    override fun observeInternalTransfers(): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList<TransactionEntity>())
    override fun observeFlaggedDuplicates(): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList<TransactionEntity>())
    override fun observeFlaggedRefunds(): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList<TransactionEntity>())
    override suspend fun setTransferGroup(id: String, groupId: String?) = Unit
    override suspend fun setDuplicateOf(id: String, originalId: String?) = Unit
    override suspend fun clearDuplicateFlagsForPair(originalId: String, duplicateSourceId: String) = Unit
    override suspend fun setCategory(id: String, categoryId: String?) = Unit
    override suspend fun setMerchant(id: String, merchantRaw: String, merchantNormalized: String) = Unit
    override suspend fun findDuplicateCandidates(
        amountMinor: Long,
        direction: String,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
        excludeId: String,
    ): List<TransactionEntity> = emptyList()
    override suspend fun findRefundCandidate(
        amountMinor: Long,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
        excludeId: String,
    ): TransactionEntity? = null
    override suspend fun setRefundOf(id: String, originalId: String?) = Unit
}

class TransferPairerTest {

    private fun tx(
        id: String,
        direction: TransactionDirection,
        amountMinor: Long,
        sourceId: String,
        type: TransactionType,
        timestampMillis: Long,
        transferGroupId: String? = null,
    ) = TransactionEntity(
        id = id,
        timestampMillis = timestampMillis,
        sourceId = sourceId,
        type = type,
        direction = direction,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = "PARTY",
        merchantNormalized = "party",
        categoryId = null,
        reference = null,
        externalId = id,
        rawMessage = null,
        transferGroupId = transferGroupId,
    )

    @Test
    fun `raast legs on different sources within window are paired`() = runBlocking {
        val dao = RecordingDao()
        val pairer = TransferPairer(TransactionRepository(dao))

        val out = tx(
            id = "hbl-out",
            direction = TransactionDirection.OUT,
            amountMinor = 310_000,
            sourceId = "HBL-BANK",
            type = TransactionType.TRANSFER_OUT,
            timestampMillis = 1_000_000L,
        )
        val incoming = tx(
            id = "meezan-in",
            direction = TransactionDirection.IN,
            amountMinor = 310_000,
            sourceId = "MEEZAN-BANK",
            type = TransactionType.TRANSFER_IN,
            timestampMillis = 1_000_030L,
        )

        dao.items += out
        dao.items += incoming

        val groupId = pairer.tryPair(out)
        assertNotNull(groupId)
        assertEquals(groupId, dao.groups["hbl-out"])
        assertEquals(groupId, dao.groups["meezan-in"])
    }

    @Test
    fun `same source is never paired with itself`() = runBlocking {
        val dao = RecordingDao()
        val pairer = TransferPairer(TransactionRepository(dao))

        val out = tx("a", TransactionDirection.OUT, 50_000, "HBL-BANK", TransactionType.TRANSFER_OUT, 1_000L)
        val incoming = tx("b", TransactionDirection.IN, 50_000, "HBL-BANK", TransactionType.TRANSFER_IN, 1_100L)
        dao.items += out
        dao.items += incoming

        assertNull(pairer.tryPair(out))
    }

    @Test
    fun `legs outside the time window are not paired`() = runBlocking {
        val dao = RecordingDao()
        val pairer = TransferPairer(TransactionRepository(dao))

        val out = tx("a", TransactionDirection.OUT, 50_000, "HBL-BANK", TransactionType.TRANSFER_OUT, 1_000L)
        val incoming = tx("b", TransactionDirection.IN, 50_000, "MEEZAN-BANK", TransactionType.TRANSFER_IN, 1_000L + TransferPairer.MAX_SKEW_MILLIS + 1)
        dao.items += out
        dao.items += incoming

        assertNull(pairer.tryPair(out))
    }

    @Test
    fun `a purchase is never paired with a matching transfer`() = runBlocking {
        val dao = RecordingDao()
        val pairer = TransferPairer(TransactionRepository(dao))

        val purchase = tx("a", TransactionDirection.OUT, 50_000, "HBL-BANK", TransactionType.DEBIT_PURCHASE, 1_000L)
        val incoming = tx("b", TransactionDirection.IN, 50_000, "MEEZAN-BANK", TransactionType.TRANSFER_IN, 1_050L)
        dao.items += purchase
        dao.items += incoming

        assertNull(pairer.tryPair(purchase))
    }

    @Test
    fun `already paired legs are not paired again`() = runBlocking {
        val dao = RecordingDao()
        val pairer = TransferPairer(TransactionRepository(dao))

        val out = tx("a", TransactionDirection.OUT, 50_000, "HBL-BANK", TransactionType.TRANSFER_OUT, 1_000L, transferGroupId = "existing")
        val incoming = tx("b", TransactionDirection.IN, 50_000, "MEEZAN-BANK", TransactionType.TRANSFER_IN, 1_050L)
        dao.items += out
        dao.items += incoming

        assertNull(pairer.tryPair(out))
    }
}

/**
 * Minimal in-memory DAO implementing the subset TransferPairer touches.
 */
private class RecordingDao : com.trae.expensetracker.data.db.TransactionDao by UnsupportedDao {
    val items = mutableListOf<TransactionEntity>()
    val groups = mutableMapOf<String, String?>()

    override suspend fun findCandidates(
        direction: String,
        amountMinor: Long,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
    ): List<TransactionEntity> = items
        .filter {
            it.direction.name == direction &&
                it.amountMinor == amountMinor &&
                it.currency == currency &&
                it.timestampMillis in fromMillis..toMillis
        }
        .sortedBy { kotlin.math.abs(it.timestampMillis - anchorMillis) }

    override suspend fun setTransferGroup(id: String, groupId: String?) {
        groups[id] = groupId
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = items[idx].copy(transferGroupId = groupId)
    }
}
