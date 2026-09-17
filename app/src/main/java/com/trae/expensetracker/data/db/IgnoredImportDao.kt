package com.trae.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trae.expensetracker.data.model.IgnoredImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: IgnoredImportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(items: List<IgnoredImportEntity>)

    @Query("SELECT * FROM ignored_imports ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<IgnoredImportEntity>>

    @Query("SELECT * FROM ignored_imports ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<IgnoredImportEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM ignored_imports WHERE signature = :signature)")
    suspend fun isIgnored(signature: String): Boolean

    @Query("DELETE FROM ignored_imports WHERE signature = :signature")
    suspend fun restore(signature: String)

    @Query("DELETE FROM ignored_imports")
    suspend fun clearAll()
}
