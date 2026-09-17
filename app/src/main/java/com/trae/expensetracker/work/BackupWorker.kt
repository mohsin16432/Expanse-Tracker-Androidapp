package com.trae.expensetracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trae.expensetracker.ExpenseTrackerApp

/**
 * Writes a local backup on a schedule so data survives a lost or wiped phone even if the user
 * never taps Export.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ExpenseTrackerApp ?: return Result.failure()
        return runCatching {
            app.container.backupService.backupNowToGoogleDrive()
            app.container.settingsRepository.setLastBackupAt(System.currentTimeMillis())
            Result.success()
        }.getOrElse {
            // Retry later rather than giving up permanently.
            Result.retry()
        }
    }
}
