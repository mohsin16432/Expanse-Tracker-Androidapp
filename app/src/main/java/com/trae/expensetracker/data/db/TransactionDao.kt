package com.trae.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trae.expensetracker.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tx: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int = 50): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestampMillis BETWEEN :fromMillis AND :toMillis
        ORDER BY timestampMillis DESC
        """
    )
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestampMillis BETWEEN :fromMillis AND :toMillis
        AND (
          merchantNormalized LIKE '%' || :query || '%'
          OR merchantRaw LIKE '%' || :query || '%'
        )
        ORDER BY timestampMillis DESC
        """
    )
    fun searchBetween(query: String, fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT SUM(amountMinor) FROM transactions
        WHERE timestampMillis BETWEEN :fromMillis AND :toMillis
        AND direction = :direction
        """
    )
    suspend fun sumAmountMinorBetween(direction: String, fromMillis: Long, toMillis: Long): Long?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Long

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    @Query("DELETE FROM transactions WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    /** @return number of rows removed, so callers can report accurately. */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM transactions WHERE externalId IS NOT NULL")
    suspend fun deleteImportedTransactions()

    // ---- Internal transfer pairing / duplicate detection support ----

    @Query(
        """
        SELECT * FROM transactions
        WHERE direction = :direction
        AND amountMinor = :amountMinor
        AND currency = :currency
        AND timestampMillis BETWEEN :fromMillis AND :toMillis
        ORDER BY ABS(timestampMillis - :anchorMillis) ASC
        """
    )
    suspend fun findCandidates(
        direction: String,
        amountMinor: Long,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    fun observeByTransferGroup(groupId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transferGroupId IS NOT NULL ORDER BY timestampMillis DESC")
    fun observeInternalTransfers(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE duplicateOfId IS NOT NULL ORDER BY timestampMillis DESC")
    fun observeFlaggedDuplicates(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE refundOfId IS NOT NULL ORDER BY timestampMillis DESC")
    fun observeFlaggedRefunds(): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET transferGroupId = :groupId WHERE id = :id")
    suspend fun setTransferGroup(id: String, groupId: String?)

    @Query("UPDATE transactions SET duplicateOfId = :originalId WHERE id = :id")
    suspend fun setDuplicateOf(id: String, originalId: String?)

    /**
     * Clears duplicate flags only for one source pair, so dismissing a duplicate between two
     * specific sources cannot silently unflag a genuine duplicate from a different source.
     */
    @Query("UPDATE transactions SET duplicateOfId = NULL WHERE duplicateOfId = :originalId AND sourceId = :duplicateSourceId")
    suspend fun clearDuplicateFlagsForPair(originalId: String, duplicateSourceId: String)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: String, categoryId: String?)

    @Query("UPDATE transactions SET merchantRaw = :merchantRaw, merchantNormalized = :merchantNormalized WHERE id = :id")
    suspend fun setMerchant(id: String, merchantRaw: String, merchantNormalized: String)

    /**
     * Duplicate candidates across all sources. The same payment can be captured twice from
     * different senders (bank SMS + wallet push notification), so source must not be a filter.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amountMinor = :amountMinor
        AND direction = :direction
        AND currency = :currency
        AND timestampMillis BETWEEN :fromMillis AND :toMillis
        AND id != :excludeId
        AND duplicateOfId IS NULL
        ORDER BY ABS(timestampMillis - :anchorMillis) ASC
        """
    )
    suspend fun findDuplicateCandidates(
        amountMinor: Long,
        direction: String,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
        excludeId: String,
    ): List<TransactionEntity>

    /**
     * Looks for an earlier charge that an incoming refund/reversal could be reversing.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amountMinor = :amountMinor
        AND currency = :currency
        AND direction = 'OUT'
        AND timestampMillis BETWEEN :fromMillis AND :toMillis
        AND id != :excludeId
        ORDER BY ABS(timestampMillis - :anchorMillis) ASC
        LIMIT 1
        """
    )
    suspend fun findRefundCandidate(
        amountMinor: Long,
        currency: String,
        fromMillis: Long,
        toMillis: Long,
        anchorMillis: Long,
        excludeId: String,
    ): TransactionEntity?

    @Query("UPDATE transactions SET refundOfId = :originalId WHERE id = :id")
    suspend fun setRefundOf(id: String, originalId: String?)
}
