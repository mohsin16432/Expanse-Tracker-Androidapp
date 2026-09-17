package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.IgnoredImportDao
import com.trae.expensetracker.data.model.IgnoredImportEntity
import com.trae.expensetracker.ingest.ImportSignature
import kotlinx.coroutines.flow.Flow

/**
 * Remembers messages the user deleted, so a later import does not resurrect them.
 */
class IgnoredImportRepository(
    private val dao: IgnoredImportDao,
) {
    fun observeAll(): Flow<List<IgnoredImportEntity>> = dao.observeAll()

    suspend fun getAll(): List<IgnoredImportEntity> = dao.getAll()

    suspend fun isIgnored(body: String): Boolean =
        runCatching { dao.isIgnored(ImportSignature.of(body)) }.getOrDefault(false)

    suspend fun ignore(body: String, reason: String) {
        if (body.isBlank()) return
        dao.upsert(
            IgnoredImportEntity(
                signature = ImportSignature.of(body),
                reason = reason,
                preview = ImportSignature.previewOf(body),
                createdAtMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun restore(signature: String) = dao.restore(signature)

    suspend fun replaceAll(items: List<IgnoredImportEntity>) = dao.insertAllReplace(items)

    suspend fun clearAll() = dao.clearAll()
}
