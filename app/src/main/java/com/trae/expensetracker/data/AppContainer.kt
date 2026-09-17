package com.trae.expensetracker.data

import android.content.Context
import androidx.room.Room
import com.trae.expensetracker.backup.BackupService
import com.trae.expensetracker.data.db.AppDatabase
import com.trae.expensetracker.data.repo.BudgetRepository
import com.trae.expensetracker.data.repo.CategoryRepository
import com.trae.expensetracker.data.repo.DataSourceRepository
import com.trae.expensetracker.data.repo.IgnoredImportRepository
import com.trae.expensetracker.data.repo.MerchantRuleRepository
import com.trae.expensetracker.data.repo.PendingImportRepository
import com.trae.expensetracker.data.repo.SettingsRepository
import com.trae.expensetracker.data.repo.TransactionRepository
import com.trae.expensetracker.data.repo.TransactionSplitRepository
import com.trae.expensetracker.ingest.SmsHistoryImporter
import com.trae.expensetracker.ingest.TransferPairer
import com.trae.expensetracker.ingest.SmsIngestor
import com.trae.expensetracker.llm.LlmClient
import com.trae.expensetracker.notify.BudgetAlertNotifier
import com.trae.expensetracker.security.AppLockManager
import com.trae.expensetracker.llm.PoeOpenAiClient
import com.trae.expensetracker.ocr.ReceiptOcrService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "expense-tracker.db")
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
        )
        .build()

    val transactionRepository = TransactionRepository(db.transactionDao())
    val categoryRepository = CategoryRepository(db.categoryDao())
    val dataSourceRepository = DataSourceRepository(db.dataSourceDao())
    val pendingImportRepository = PendingImportRepository(db.pendingImportDao())
    val merchantRuleRepository = MerchantRuleRepository(db.merchantRuleDao())
    val ignoredImportRepository = IgnoredImportRepository(db.ignoredImportDao())
    val budgetRepository = BudgetRepository(db.budgetDao())
    val transactionSplitRepository = TransactionSplitRepository(db.transactionSplitDao())
    val settingsRepository = SettingsRepository(appContext)

    val llmClient: LlmClient = PoeOpenAiClient(
        appContext = appContext,
        settingsRepository = settingsRepository,
    )

    val smsIngestor = SmsIngestor(
        appContext = appContext,
        transactionRepository = transactionRepository,
        pendingImportRepository = pendingImportRepository,
        dataSourceRepository = dataSourceRepository,
        categoryRepository = categoryRepository,
        settingsRepository = settingsRepository,
        merchantRuleRepository = merchantRuleRepository,
        ignoredImportRepository = ignoredImportRepository,
        llmClient = llmClient,
    )

    val transferPairer = TransferPairer(transactionRepository)

    val budgetAlertNotifier = BudgetAlertNotifier(
        appContext = appContext,
        budgetRepository = budgetRepository,
        settingsRepository = settingsRepository,
    )

    val smsHistoryImporter = SmsHistoryImporter(
        appContext = appContext,
        smsIngestor = smsIngestor,
        transactionRepository = transactionRepository,
        budgetAlertNotifier = budgetAlertNotifier,
    )

    val backupService = BackupService(
        appContext = appContext,
        db = db,
        settingsRepository = settingsRepository,
    )

    val receiptOcrService = ReceiptOcrService(appContext)

    val appLockManager = AppLockManager(appContext)

    init {
        // Seed a small default category set so category pickers are usable.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (categoryRepository.getAll().isEmpty()) {
                    val defaults = listOf(
                        com.trae.expensetracker.data.model.CategoryEntity("Food", "Food", 0xFFFF6A7C, 1),
                        com.trae.expensetracker.data.model.CategoryEntity("Shopping", "Shopping", 0xFF6B5DFF, 2),
                        com.trae.expensetracker.data.model.CategoryEntity("Bills", "Bills", 0xFF8D7BFF, 3),
                        com.trae.expensetracker.data.model.CategoryEntity("Health", "Health", 0xFF39D8AB, 4),
                        com.trae.expensetracker.data.model.CategoryEntity("Transfer", "Transfer", 0xFFF4C063, 5),
                        com.trae.expensetracker.data.model.CategoryEntity("Income", "Income", 0xFF39D8AB, 6),
                        com.trae.expensetracker.data.model.CategoryEntity("Other", "Other", 0xFF95A0BD, 7),
                    )
                    defaults.forEach { categoryRepository.upsert(it) }
                }
                if (dataSourceRepository.getAll().none { it.id == "cash" }) {
                    dataSourceRepository.upsert(
                        com.trae.expensetracker.data.model.DataSourceEntity(
                            id = "cash",
                            name = "Cash",
                            shortCode = "CASH",
                            type = com.trae.expensetracker.data.model.DataSourceType.CASH,
                            enabled = true,
                        )
                    )
                }
            }
        }
    }
}
