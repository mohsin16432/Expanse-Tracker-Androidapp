package com.trae.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class TransactionDirection { OUT, IN }

@Serializable
enum class TransactionType {
    DEBIT_PURCHASE,
    CREDIT_RECEIVED,
    TRANSFER_OUT,
    TRANSFER_IN,
    CARD_CHARGE,
    CARD_PAYMENT,
    BILL_PAYMENT,
    ADJUSTMENT,
}

@Serializable
enum class DataSourceType { CASH, BANK, WALLET, CREDIT_CARD }

@Serializable
@Entity(tableName = "data_sources")
data class DataSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** SMS sender short code, e.g. 14250, 8756, 8558 */
    val shortCode: String,
    val type: DataSourceType,
    /** Optional for credit cards to differentiate multiple cards under same sender. */
    val cardLast4: String? = null,
    val enabled: Boolean = true,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Long,
    val sortOrder: Int,
)

@Serializable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val timestampMillis: Long,

    val sourceId: String,
    val type: TransactionType,
    val direction: TransactionDirection,

    /** Amount in minor units (e.g., PKR paisa = amount * 100). */
    val amountMinor: Long,
    val currency: String,

    val merchantRaw: String,
    val merchantNormalized: String,

    val categoryId: String?,
    val reference: String?,

    /** Dedup key; for SMS this is a stable hash of sender+body(+timestamp when available). */
    val externalId: String?,

    /** Keep raw message text for debugging; can be excluded from backups if desired. */
    val rawMessage: String?,
)

enum class PendingImportStatus { NEEDS_REVIEW, SAVED, DISCARDED }

@Entity(tableName = "pending_imports")
data class PendingImportEntity(
    @PrimaryKey val id: String,
    val createdAtMillis: Long,
    val kind: String, // "sms" or "receipt"
    val sender: String?,
    val rawText: String,
    val draftTransactionJson: String?,
    val status: PendingImportStatus = PendingImportStatus.NEEDS_REVIEW,
)
