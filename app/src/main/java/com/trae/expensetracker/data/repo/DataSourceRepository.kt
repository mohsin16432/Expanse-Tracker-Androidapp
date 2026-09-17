package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.DataSourceDao
import com.trae.expensetracker.data.model.DataSourceEntity
import kotlinx.coroutines.flow.Flow

class DataSourceRepository(
    private val dao: DataSourceDao,
) {
    fun observeEnabled(): Flow<List<DataSourceEntity>> = dao.observeEnabled()
    fun observeAll(): Flow<List<DataSourceEntity>> = dao.observeAll()
    suspend fun upsert(source: DataSourceEntity) = dao.upsert(source)
    suspend fun findByShortCode(shortCode: String): DataSourceEntity? = dao.findByShortCode(shortCode)
    suspend fun findById(id: String): DataSourceEntity? = dao.findById(id)
    suspend fun deleteById(id: String) = dao.deleteById(id)
    suspend fun getAll(): List<DataSourceEntity> = dao.getAll()

    /** Records the balance of an account at a point in time, enabling running balances. */
    suspend fun setOpeningBalance(id: String, balanceMinor: Long?, atMillis: Long?) {
        val existing = dao.findById(id) ?: return
        dao.upsert(existing.copy(openingBalanceMinor = balanceMinor, openingBalanceMillis = atMillis))
    }
    suspend fun replaceAll(items: List<DataSourceEntity>) = dao.insertAllReplace(items)
}
