package com.trae.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trae.expensetracker.data.model.MerchantRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(items: List<MerchantRuleEntity>)

    @Query("SELECT * FROM merchant_rules WHERE enabled = 1 ORDER BY hitCount DESC, createdAtMillis DESC")
    suspend fun getEnabled(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules ORDER BY hitCount DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules ORDER BY hitCount DESC, createdAtMillis DESC")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE type = :type AND pattern = :pattern LIMIT 1")
    suspend fun findByPattern(type: String, pattern: String): MerchantRuleEntity?

    @Query("UPDATE merchant_rules SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun incrementHit(id: String)

    @Query("DELETE FROM merchant_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM merchant_rules")
    suspend fun clearAll()
}
