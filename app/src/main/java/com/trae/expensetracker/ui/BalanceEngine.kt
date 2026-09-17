package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity

/**
 * Computes running balances per account from a recorded opening balance plus transactions.
 *
 * Pure logic, no Android dependencies, so it can be unit tested directly.
 */
object BalanceEngine {

    data class AccountBalance(
        val sourceId: String,
        val sourceName: String,
        val balanceMinor: Long,
        val currency: String,
        /** True when the user has not recorded an opening balance, so the figure is partial. */
        val isPartial: Boolean,
        val transactionCount: Int,
    )

    /**
     * Balance for one account: opening balance plus incoming, minus outgoing.
     *
     * Internal transfer legs are included — money genuinely left one account and arrived in the
     * other, even though the pair is excluded from spend/income totals.
     */
    fun balanceFor(
        source: DataSourceEntity,
        transactions: List<TransactionEntity>,
    ): AccountBalance {
        val opening = source.openingBalanceMinor
        val relevant = transactions.filter { it.sourceId == source.id && it.duplicateOfId == null }

        var delta = 0L
        relevant.forEach { tx ->
            delta += when (tx.direction) {
                TransactionDirection.IN -> tx.amountMinor
                TransactionDirection.OUT -> -tx.amountMinor
            }
        }

        return AccountBalance(
            sourceId = source.id,
            sourceName = source.name,
            balanceMinor = (opening ?: 0L) + delta,
            currency = relevant.firstOrNull()?.currency ?: "PKR",
            isPartial = opening == null,
            transactionCount = relevant.size,
        )
    }

    /** Balances for every source, largest absolute balance first. */
    fun balances(
        sources: List<DataSourceEntity>,
        transactions: List<TransactionEntity>,
    ): List<AccountBalance> = sources
        .map { balanceFor(it, transactions) }
        .sortedByDescending { kotlin.math.abs(it.balanceMinor) }

    /** Combined balance across accounts that have a known opening balance. */
    fun totalKnownBalance(balances: List<AccountBalance>): Long =
        balances.filter { !it.isPartial }.sumOf { it.balanceMinor }
}
