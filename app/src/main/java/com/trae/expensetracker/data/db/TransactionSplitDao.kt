package com.trae.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trae.expensetracker.data.model.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionSplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TransactionSplitEntity>)

    @Query("SELECT * FROM transaction_splits")
    fun observeAll(): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits")
    suspend fun getAll(): List<TransactionSplitEntity>

    @Query("SELECT * FROM transaction_splits WHERE transactionId = :transactionId")
    suspend fun forTransaction(transactionId: String): List<TransactionSplitEntity>

    @Query("DELETE FROM transaction_splits WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: String)

    @Query("DELETE FROM transaction_splits")
    suspend fun clearAll()
}
