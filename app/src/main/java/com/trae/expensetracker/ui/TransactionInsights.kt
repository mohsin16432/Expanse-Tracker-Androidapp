package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity

/**
 * Shared rules for deciding which transactions count towards money-in / money-out.
 *
 * Two classes of rows are stored but must not distort totals:
 *  - internal transfers between the user's own accounts (both legs would inflate spend AND income)
 *  - rows flagged as duplicates of an earlier capture
 */
object TransactionInsights {

    /**
     * Rows that must not be counted as either spending or income:
     *  - internal transfers (both legs would double count)
     *  - duplicates of an earlier capture
     *  - refunds (they reverse a charge, they are not earnings)
     */
    fun isExcludedFromTotals(tx: TransactionEntity): Boolean =
        tx.transferGroupId != null || tx.duplicateOfId != null || tx.refundOfId != null

    /** Transactions that genuinely move money out of the user's control. */
    fun countableOutgoing(items: List<TransactionEntity>): List<TransactionEntity> =
        items.filter { it.direction == TransactionDirection.OUT && !isExcludedFromTotals(it) }

    /** Transactions that genuinely bring money in. */
    fun countableIncoming(items: List<TransactionEntity>): List<TransactionEntity> =
        items.filter { it.direction == TransactionDirection.IN && !isExcludedFromTotals(it) }

    /** Refunds reduce what was actually spent instead of counting as income. */
    fun refundTotalMinor(items: List<TransactionEntity>): Long =
        items.filter { it.refundOfId != null }.sumOf { it.amountMinor }

    fun outgoingTotalMinor(items: List<TransactionEntity>): Long =
        (countableOutgoing(items).sumOf { it.amountMinor } - refundTotalMinor(items)).coerceAtLeast(0L)

    fun incomingTotalMinor(items: List<TransactionEntity>): Long =
        countableIncoming(items).sumOf { it.amountMinor }

    /** Internal transfer legs, deduplicated to one row per transfer group. */
    fun internalTransferGroups(items: List<TransactionEntity>): List<InternalTransfer> =
        items.filter { it.transferGroupId != null }
            .groupBy { it.transferGroupId!! }
            .mapNotNull { (groupId, legs) ->
                val out = legs.firstOrNull { it.direction == TransactionDirection.OUT }
                val incoming = legs.firstOrNull { it.direction == TransactionDirection.IN }
                val anchor = out ?: incoming ?: return@mapNotNull null
                InternalTransfer(
                    groupId = groupId,
                    amountMinor = anchor.amountMinor,
                    currency = anchor.currency,
                    timestampMillis = legs.minOf { it.timestampMillis },
                    fromSourceId = out?.sourceId,
                    toSourceId = incoming?.sourceId,
                    legs = legs,
                )
            }
            .sortedByDescending { it.timestampMillis }

    /** Rows that were flagged as duplicates of an earlier capture. */
    fun flaggedDuplicates(items: List<TransactionEntity>): List<TransactionEntity> =
        items.filter { it.duplicateOfId != null }

    /** Rows that were recognised as refunds of an earlier charge. */
    fun refunds(items: List<TransactionEntity>): List<TransactionEntity> =
        items.filter { it.refundOfId != null }

    data class InternalTransfer(
        val groupId: String,
        val amountMinor: Long,
        val currency: String,
        val timestampMillis: Long,
        val fromSourceId: String?,
        val toSourceId: String?,
        val legs: List<TransactionEntity>,
    )
}
