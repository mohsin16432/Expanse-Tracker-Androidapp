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
enum class DataSourceType { CASH, BANK, WALLET, CREDIT_CARD, NOTIFICATION }

@Serializable
@Entity(tableName = "data_sources")
data class DataSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Sender identifier. For SMS this is a numeric short code (e.g. 14250, 8756, 8558). For app notifications this is usually the app package name. */
    val shortCode: String,
    val type: DataSourceType,
    /** Optional for credit cards to differentiate multiple cards under same sender. */
    val cardLast4: String? = null,
    val enabled: Boolean = true,

    /**
     * Balance of this account at [openingBalanceMillis], used to show a running balance.
     * Null means the user has not recorded a starting point for this source yet.
     */
    val openingBalanceMinor: Long? = null,
    val openingBalanceMillis: Long? = null,
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

    /**
     * Non-null when this transaction is one leg of an internal transfer between two of the
     * user's own accounts (e.g. HBL -> Meezan via Raast). Both legs share the same group id.
     * Internal transfers are excluded from spend/income totals to avoid double counting.
     */
    val transferGroupId: String? = null,

    /**
     * Non-null when this row is a likely duplicate of another transaction (for example the same
     * payment captured from both an SMS and a push notification).
     */
    val duplicateOfId: String? = null,

    /**
     * Non-null when this transaction is a refund/reversal of an earlier charge.
     * Refunds are not treated as income.
     */
    val refundOfId: String? = null,
)

/** How a merchant rule should be interpreted. */
@Serializable
enum class MerchantRuleType {
    /** Pattern matched against the normalized merchant name to assign a category. */
    MERCHANT_CATEGORY,

    /** Pattern matched against the normalized merchant name to rewrite the display name. */
    MERCHANT_ALIAS,

    /**
     * Marks a known non-duplicate: two sources that legitimately post the same amount around the
     * same time (for example a bank SMS plus its own wallet notification for a real transfer).
     * Stored as "sourceA|sourceB" in [MerchantRuleEntity.pattern].
     */
    DUPLICATE_SUPPRESSION,
}

/**
 * User-learned rule that survives app restarts and new imports.
 *
 * Rules are created automatically when the user corrects a transaction in Review (merchant
 * category / alias) or dismisses a duplicate (DUPLICATE_SUPPRESSION), so the same correction
 * never has to be repeated.
 */
@Serializable
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val id: String,
    val type: MerchantRuleType,
    /**
     * Match key. For merchant rules this is a lowercased substring of the normalized merchant
     * name; for DUPLICATE_SUPPRESSION it is "sourceA|sourceB".
     */
    val pattern: String,
    /** Target category id for MERCHANT_CATEGORY rules. */
    val categoryId: String? = null,
    /** Replacement display name for MERCHANT_ALIAS rules. */
    val alias: String? = null,
    val createdAtMillis: Long,
    val hitCount: Int = 0,
    val enabled: Boolean = true,
)

/**
 * A per-category spending limit for one budget cycle.
 *
 * [categoryId] is the category name used across the app (see CategoryRules). A budget with a
 * null/blank category id is a catch-all "total spending" budget.
 */
@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String?,
    /** Limit in minor units (paisa), for one full budget cycle. */
    val limitMinor: Long,
    val currency: String = "PKR",
    val createdAtMillis: Long,
    val enabled: Boolean = true,
)

/**
 * One part of a split transaction, e.g. a single grocery receipt split across Food and Household.
 *
 * A transaction with splits keeps its own total in [TransactionEntity.amountMinor]; the split rows
 * only redistribute that total across categories. Category totals use splits when present.
 */
@Serializable
@Entity(tableName = "transaction_splits", primaryKeys = ["transactionId", "categoryId"])
data class TransactionSplitEntity(
    val transactionId: String,
    /** Category this part belongs to. Blank is treated as uncategorised. */
    val categoryId: String,
    val amountMinor: Long,
)

enum class PendingImportStatus { NEEDS_REVIEW, SAVED, DISCARDED }

/**
 * Records that the user deliberately removed an imported message.
 *
 * SMS import is re-runnable: the message stays in the phone's inbox forever, so without a
 * record of the deletion the next "Refresh" would simply re-import it. Rows here are the
 * tombstone that stops that.
 */
@Serializable
@Entity(tableName = "ignored_imports")
data class IgnoredImportEntity(
    /** Hash of the message body. See [com.trae.expensetracker.ingest.ImportSignature]. */
    @PrimaryKey val signature: String,
    /** "deleted" or "discarded" — kept for display in Settings. */
    val reason: String,
    /** First line of the message, so the user can recognise it when reviewing the list. */
    val preview: String,
    val createdAtMillis: Long,
)

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
