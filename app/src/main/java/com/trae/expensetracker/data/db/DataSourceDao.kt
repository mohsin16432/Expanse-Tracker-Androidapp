package com.trae.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trae.expensetracker.data.model.DataSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: DataSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(items: List<DataSourceEntity>)

    @Query("SELECT * FROM data_sources WHERE enabled = 1 ORDER BY name ASC")
    fun observeEnabled(): Flow<List<DataSourceEntity>>

    @Query("SELECT * FROM data_sources ORDER BY name ASC")
    fun observeAll(): Flow<List<DataSourceEntity>>

    @Query("SELECT * FROM data_sources WHERE shortCode = :shortCode LIMIT 1")
    suspend fun findByShortCode(shortCode: String): DataSourceEntity?

    @Query("SELECT * FROM data_sources WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DataSourceEntity?

    @Query("DELETE FROM data_sources WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM data_sources ORDER BY name ASC")
    suspend fun getAll(): List<DataSourceEntity>

    @Query("DELETE FROM data_sources")
    suspend fun clearAll()
}
