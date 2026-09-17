package com.trae.expensetracker.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trae.expensetracker.data.model.BudgetEntity
import com.trae.expensetracker.data.model.CategoryEntity
import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.IgnoredImportEntity
import com.trae.expensetracker.data.model.MerchantRuleEntity
import com.trae.expensetracker.data.model.PendingImportEntity
import com.trae.expensetracker.data.model.TransactionSplitEntity
import com.trae.expensetracker.data.model.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        DataSourceEntity::class,
        PendingImportEntity::class,
        MerchantRuleEntity::class,
        BudgetEntity::class,
        TransactionSplitEntity::class,
        IgnoredImportEntity::class,
    ],
    version = 7,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun dataSourceDao(): DataSourceDao
    abstract fun pendingImportDao(): PendingImportDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun transactionSplitDao(): TransactionSplitDao
    abstract fun ignoredImportDao(): IgnoredImportDao

    companion object {
        /**
         * Historical safety migration. Version 1 is treated as the original production schema.
         * If a user still has a v1 DB, move forward without wiping data.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create pending_imports if it did not exist in early builds.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_imports` (
                        `id` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `sender` TEXT,
                        `rawText` TEXT NOT NULL,
                        `draftTransactionJson` TEXT,
                        `status` TEXT NOT NULL DEFAULT 'NEEDS_REVIEW',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Add credit-card discriminator column without wiping existing sources.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `data_sources` ADD COLUMN `cardLast4` TEXT")
            }
        }

        /**
         * Tier 1 features: internal transfer pairing, duplicate/refund flags, learned merchant rules.
         * Additive only; existing transactions keep working with NULL for the new columns.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transferGroupId` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `duplicateOfId` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `refundOfId` TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_rules` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `categoryId` TEXT,
                        `alias` TEXT,
                        `createdAtMillis` INTEGER NOT NULL,
                        `hitCount` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** Tier 2: per-category budget limits. Additive, existing data untouched. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `id` TEXT NOT NULL,
                        `categoryId` TEXT,
                        `limitMinor` INTEGER NOT NULL,
                        `currency` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Tier 3: split transactions and account opening balances.
         * Additive; existing rows keep working with NULL/empty defaults.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `data_sources` ADD COLUMN `openingBalanceMinor` INTEGER")
                db.execSQL("ALTER TABLE `data_sources` ADD COLUMN `openingBalanceMillis` INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_splits` (
                        `transactionId` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        PRIMARY KEY(`transactionId`, `categoryId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Tier 3 fix: remember deleted messages so a re-run of SMS import does not resurrect them.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ignored_imports` (
                        `signature` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `preview` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`signature`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
