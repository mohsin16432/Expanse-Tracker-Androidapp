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

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions WHERE externalId IS NOT NULL")
    suspend fun deleteImportedTransactions()
}
