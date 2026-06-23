package com.trae.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trae.expensetracker.data.model.PendingImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingImportEntity)

    @Query("SELECT * FROM pending_imports WHERE status = 'NEEDS_REVIEW' ORDER BY createdAtMillis DESC")
    fun observeNeedsReview(): Flow<List<PendingImportEntity>>

    @Query("SELECT COUNT(*) FROM pending_imports WHERE status = 'NEEDS_REVIEW'")
    fun observeNeedsReviewCount(): Flow<Int>
}

