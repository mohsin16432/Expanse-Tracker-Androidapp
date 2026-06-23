package com.trae.expensetracker.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trae.expensetracker.data.model.CategoryEntity
import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.PendingImportEntity
import com.trae.expensetracker.data.model.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        DataSourceEntity::class,
        PendingImportEntity::class,
    ],
    version = 3,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun dataSourceDao(): DataSourceDao
    abstract fun pendingImportDao(): PendingImportDao

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
    }
}
