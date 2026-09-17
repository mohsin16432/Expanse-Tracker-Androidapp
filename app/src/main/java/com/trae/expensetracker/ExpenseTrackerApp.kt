package com.trae.expensetracker

import android.app.Application
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.work.BackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ExpenseTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Honour the user's automatic-backup preference as early as possible.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (container.settingsRepository.autoBackupEnabled().first()) {
                    BackupScheduler.schedule(this@ExpenseTrackerApp)
                } else {
                    BackupScheduler.cancel(this@ExpenseTrackerApp)
                }
            }
        }
    }
}

