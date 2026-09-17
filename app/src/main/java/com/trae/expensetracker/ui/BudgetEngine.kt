package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.BudgetEntity
import com.trae.expensetracker.data.model.TransactionEntity

/**
 * Computes cycle-aware budget progress.
 *
 * Pure logic with no Android dependencies so it can be unit tested directly.
 */
object BudgetEngine {

    /** Overall budget key: a budget with no category applies to all spending. */
    const val OVERALL_KEY = ""

    /** Warn the user once they cross this fraction of a limit. */
    const val WARN_THRESHOLD = 0.8

    data class Progress(
        val budget: BudgetEntity,
        val categoryName: String,
        val spentMinor: Long,
        val limitMinor: Long,
        val remainingMinor: Long,
        val fraction: Double,
        val isOverBudget: Boolean,
        val isWarning: Boolean,
    )

    /**
     * Builds progress for every enabled budget.
     *
     * @param spending transactions already filtered to countable spending for the cycle
     *   (i.e. internal transfers, duplicates and refunds handled by [TransactionInsights]).
     */
    fun progress(
        budgets: List<BudgetEntity>,
        spending: List<TransactionEntity>,
        categoryOf: (TransactionEntity) -> String,
    ): List<Progress> {
        val totalsByCategory: Map<String, Long> = spending
            .groupBy { categoryOf(it).trim() }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

        val totalSpent = spending.sumOf { it.amountMinor }

        return budgets
            .filter { it.enabled && it.limitMinor > 0 }
            .map { budget ->
                val key = budget.categoryId?.trim().orEmpty()
                val spent = if (key == OVERALL_KEY) totalSpent else totalsByCategory[key] ?: 0L
                val fraction = if (budget.limitMinor <= 0) 0.0 else spent.toDouble() / budget.limitMinor.toDouble()
                Progress(
                    budget = budget,
                    categoryName = if (key == OVERALL_KEY) "All spending" else key,
                    spentMinor = spent,
                    limitMinor = budget.limitMinor,
                    remainingMinor = (budget.limitMinor - spent).coerceAtLeast(0L),
                    fraction = fraction,
                    isOverBudget = spent > budget.limitMinor,
                    isWarning = !isOver(spent, budget.limitMinor) && fraction >= WARN_THRESHOLD,
                )
            }
            .sortedWith(compareByDescending<Progress> { it.fraction }.thenBy { it.categoryName })
    }

    private fun isOver(spent: Long, limit: Long): Boolean = spent > limit

    /**
     * Budgets that just crossed a threshold and should raise an alert.
     *
     * @param cycleKey identifies the cycle, so an alert fires at most once per budget per cycle.
     * @param alreadyNotified keys previously recorded via [alertKey] — note these are alert keys
     *   (budget id combined with the cycle), not bare budget ids.
     */
    fun pendingAlerts(
        progress: List<Progress>,
        cycleKey: String,
        alreadyNotified: Set<String>,
    ): List<Progress> = progress.filter { p ->
        alertKey(p.budget.id, cycleKey) !in alreadyNotified && (p.isOverBudget || p.isWarning)
    }

    /**
     * Stable key so alert state can be remembered per budget per cycle.
     *
     * [cycleKey] must be the cycle *start date* (e.g. "2026-08-26"), not an epoch timestamp whose
     * value changes with time of day.
     */
    fun alertKey(budgetId: String, cycleKey: String): String = "$budgetId@$cycleKey"
}
