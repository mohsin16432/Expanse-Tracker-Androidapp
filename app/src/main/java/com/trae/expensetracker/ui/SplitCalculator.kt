package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionSplitEntity

/**
 * Distributes transaction amounts across categories, honouring splits.
 *
 * A whole-transaction entry is treated as a single split covering its full amount, so every
 * caller can use one code path whether or not the user split the transaction.
 */
object SplitCalculator {

    data class Weighted(
        val category: String,
        val amountMinor: Long,
        val transaction: TransactionEntity,
        val isSplit: Boolean,
    )

    /** Amount credited to [category] for one transaction. */
    fun amountForCategory(
        tx: TransactionEntity,
        splits: List<TransactionSplitEntity>,
        category: String,
    ): Long {
        if (splits.isEmpty()) {
            return if (CategoryRules.detect(tx).equals(category, ignoreCase = true)) tx.amountMinor else 0L
        }
        return splits
            .filter { it.categoryId.equals(category, ignoreCase = true) }
            .sumOf { it.amountMinor }
    }

    /**
     * Flattens transactions into category-weighted amounts.
     *
     * @param categoryOf used only for transactions that have no splits.
     */
    fun weighted(
        transactions: List<TransactionEntity>,
        splitsByTransaction: Map<String, List<TransactionSplitEntity>>,
        categoryOf: (TransactionEntity) -> String,
    ): List<Weighted> = transactions.flatMap { tx ->
        val splits = splitsByTransaction[tx.id].orEmpty()
        if (splits.isEmpty()) {
            listOf(
                Weighted(
                    category = categoryOf(tx),
                    amountMinor = tx.amountMinor,
                    transaction = tx,
                    isSplit = false,
                )
            )
        } else {
            splits.map { split ->
                Weighted(
                    category = split.categoryId,
                    amountMinor = split.amountMinor,
                    transaction = tx,
                    isSplit = true,
                )
            }
        }
    }

    /** Groups weighted amounts by category. */
    fun totalsByCategory(
        transactions: List<TransactionEntity>,
        splitsByTransaction: Map<String, List<TransactionSplitEntity>>,
        categoryOf: (TransactionEntity) -> String,
    ): Map<String, Long> = weighted(transactions, splitsByTransaction, categoryOf)
        .groupBy { it.category.trim().ifBlank { "Other" } }
        .mapValues { (_, items) -> items.sumOf { it.amountMinor } }

    /**
     * Validates a proposed split set.
     *
     * @return null when valid, otherwise a human-readable reason.
     */
    fun validate(totalMinor: Long, splits: List<TransactionSplitEntity>): String? {
        if (splits.isEmpty()) return null
        val sum = splits.sumOf { it.amountMinor }
        if (sum != totalMinor) {
            return "Split amounts must add up to the transaction total."
        }
        if (splits.any { it.amountMinor <= 0 }) {
            return "Each split must be greater than zero."
        }
        return null
    }
}
