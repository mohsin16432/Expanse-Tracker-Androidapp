package com.trae.expensetracker.backup

import android.content.Context
import android.net.Uri
import com.trae.expensetracker.data.db.AppDatabase
import com.trae.expensetracker.data.model.CategoryEntity
import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Functional local backup/restore.
 * Storage target: app external files dir.
 *
 * This makes the buttons actually work now. Google Drive sync can be layered on top later.
 */
class BackupService(
    private val appContext: Context,
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun backupNowToGoogleDrive(): String = withContext(Dispatchers.IO) {
        val snapshot = BackupSnapshot(
            transactions = db.transactionDao().getAll(),
            categories = db.categoryDao().getAll(),
            sources = db.dataSourceDao().getAll(),
            settings = settingsRepository.snapshot(),
        )
        val file = backupFile()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(BackupSnapshot.serializer(), snapshot))
        file.absolutePath
    }

    suspend fun restoreFromGoogleDriveReplaceAll(): Boolean = withContext(Dispatchers.IO) {
        val file = backupFile()
        if (!file.exists()) return@withContext false

        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), file.readText())
        restoreSnapshot(snapshot)
        true
    }

    suspend fun exportBackupToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val snapshot = BackupSnapshot(
            transactions = db.transactionDao().getAll(),
            categories = db.categoryDao().getAll(),
            sources = db.dataSourceDao().getAll(),
            settings = settingsRepository.snapshot(),
        )
        appContext.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(BackupSnapshot.serializer(), snapshot).toByteArray())
            out.flush()
        } ?: return@withContext false
        true
    }

    suspend fun restoreFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val content = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@withContext false
        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), content)
        restoreSnapshot(snapshot)
        true
    }

    private suspend fun restoreSnapshot(snapshot: BackupSnapshot) {
        db.runInTransaction {
            db.clearAllTables()
        }
        db.dataSourceDao().insertAllReplace(snapshot.sources)
        db.categoryDao().insertAllReplace(snapshot.categories)
        db.transactionDao().insertAllReplace(snapshot.transactions)
        settingsRepository.restore(snapshot.settings)
    }

    fun backupFilePath(): String = backupFile().absolutePath

    private fun backupFile(): File {
        val dir = appContext.getExternalFilesDir("backups") ?: appContext.filesDir
        return File(dir, "expense_tracker_backup.json")
    }
}

@Serializable
data class BackupSnapshot(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val sources: List<DataSourceEntity>,
    val settings: com.trae.expensetracker.data.repo.SettingsSnapshot,
)
