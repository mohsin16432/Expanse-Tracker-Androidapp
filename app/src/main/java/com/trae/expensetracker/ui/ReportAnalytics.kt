package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Aggregations for the Reports screen.
 *
 * Pure logic, no Android or Compose dependencies, so the numbers can be unit tested directly.
 * All input lists are expected to be already filtered by [TransactionInsights] where relevant.
 */
object ReportAnalytics {

    data class CategorySlice(
        val category: String,
        val totalMinor: Long,
        /** Share of total spending, 0.0..1.0 */
        val fraction: Double,
        val count: Int,
    )

    data class MerchantTotal(
        val merchant: String,
        val totalMinor: Long,
        val count: Int,
    )

    data class MonthPoint(
        val month: YearMonth,
        val spentMinor: Long,
        val incomeMinor: Long,
    )

    /** Category breakdown for a set of spending transactions, largest first. */
    fun categoryBreakdown(
        spending: List<TransactionEntity>,
        categoryOf: (TransactionEntity) -> String,
    ): List<CategorySlice> {
        if (spending.isEmpty()) return emptyList()
        val total = spending.sumOf { it.amountMinor }
        if (total <= 0L) return emptyList()

        return spending
            .groupBy { categoryOf(it).trim().ifBlank { "Other" } }
            .map { (category, items) ->
                val sum = items.sumOf { it.amountMinor }
                CategorySlice(
                    category = category,
                    totalMinor = sum,
                    fraction = sum.toDouble() / total.toDouble(),
                    count = items.size,
                )
            }
            .sortedByDescending { it.totalMinor }
    }

    /** Biggest merchants by spend. */
    fun topMerchants(
        spending: List<TransactionEntity>,
        limit: Int = 5,
    ): List<MerchantTotal> = spending
        .groupBy { it.merchantNormalized.trim().ifBlank { "unknown" } }
        .map { (merchant, items) ->
            MerchantTotal(
                merchant = items.first().merchantRaw.ifBlank { merchant },
                totalMinor = items.sumOf { it.amountMinor },
                count = items.size,
            )
        }
        .sortedByDescending { it.totalMinor }
        .take(limit)

    /**
     * Month-by-month spend/income for the last [months] calendar months, oldest first.
     * Months with no activity are included with zeroes so the chart keeps an even axis.
     */
    fun monthlyTrend(
        transactions: List<TransactionEntity>,
        months: Int = 6,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthPoint> {
        if (months <= 0) return emptyList()

        val current = YearMonth.from(today)
        val window = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }

        val grouped = transactions.groupBy { tx ->
            YearMonth.from(java.time.Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).toLocalDate())
        }

        return window.map { month ->
            val items = grouped[month].orEmpty()
            MonthPoint(
                month = month,
                spentMinor = TransactionInsights.outgoingTotalMinor(items),
                incomeMinor = TransactionInsights.incomingTotalMinor(items),
            )
        }
    }

    /** Average daily spend across the elapsed days of a cycle. */
    fun averageDailySpend(totalSpentMinor: Long, daysElapsed: Int): Long {
        if (daysElapsed <= 0) return 0L
        return totalSpentMinor / daysElapsed
    }

    /**
     * Projects where spending will land by the end of the cycle, based on the pace so far.
     * Useful for an early warning before a budget is actually breached.
     */
    fun projectedCycleSpend(totalSpentMinor: Long, daysElapsed: Int, daysInCycle: Int): Long {
        if (daysElapsed <= 0 || daysInCycle <= 0) return totalSpentMinor
        val pace = totalSpentMinor.toDouble() / daysElapsed.toDouble()
        return (pace * daysInCycle).toLong()
    }
}
