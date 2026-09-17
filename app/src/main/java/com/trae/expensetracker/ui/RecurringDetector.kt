package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity

/**
 * Detects recurring subscriptions from transaction history.
 *
 * Works purely from what the app already stores: if the same merchant charges a similar amount
 * at a roughly monthly (or weekly/yearly) cadence, it is reported as a subscription. No bank
 * integration or manual setup required.
 */
object RecurringDetector {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** How much the amount may vary between charges and still count as the same subscription. */
    const val AMOUNT_TOLERANCE = 0.15

    /** Minimum number of charges before something is considered recurring. */
    const val MIN_OCCURRENCES = 3

    data class Recurring(
        val merchantNormalized: String,
        val displayName: String,
        val averageAmountMinor: Long,
        val currency: String,
        val cadenceDays: Int,
        val occurrences: Int,
        val lastChargedMillis: Long,
        val nextExpectedMillis: Long,
        val monthlyCostMinor: Long,
    )

    private data class Cadence(val label: String, val days: Int, val tolerance: Int)

    private val cadences = listOf(
        Cadence("weekly", 7, 2),
        Cadence("biweekly", 14, 3),
        Cadence("monthly", 30, 6),
        Cadence("quarterly", 91, 12),
        Cadence("yearly", 365, 30),
    )

    fun detect(
        transactions: List<TransactionEntity>,
        minOccurrences: Int = MIN_OCCURRENCES,
    ): List<Recurring> {
        val candidates = transactions.filter {
            it.direction == TransactionDirection.OUT &&
                it.transferGroupId == null &&
                it.duplicateOfId == null &&
                it.refundOfId == null &&
                it.merchantNormalized.isNotBlank()
        }

        return candidates
            .groupBy { it.merchantNormalized }
            .mapNotNull { (key, items) -> analyse(key, items, minOccurrences) }
            .sortedByDescending { it.monthlyCostMinor }
    }

    private fun analyse(
        key: String,
        items: List<TransactionEntity>,
        minOccurrences: Int,
    ): Recurring? {
        if (items.size < minOccurrences) return null

        val sorted = items.sortedBy { it.timestampMillis }
        // Amounts must be stable, otherwise it is ordinary repeat shopping, not a subscription.
        val amounts = sorted.map { it.amountMinor }
        val average = amounts.average()
        if (average <= 0.0) return null
        val withinTolerance = amounts.all { kotlin.math.abs(it - average) / average <= AMOUNT_TOLERANCE }
        if (!withinTolerance) return null

        val gaps = sorted.zipWithNext { a, b -> ((b.timestampMillis - a.timestampMillis) / DAY_MILLIS).toInt() }
            .filter { it > 0 }
        if (gaps.isEmpty()) return null

        val medianGap = gaps.sorted()[gaps.size / 2]
        val cadence = cadences.firstOrNull { kotlin.math.abs(medianGap - it.days) <= it.tolerance } ?: return null

        // Most gaps must agree with the cadence; a single coincidental repeat should not qualify.
        val agreeing = gaps.count { kotlin.math.abs(it - cadence.days) <= cadence.tolerance }
        if (agreeing < gaps.size * 0.6) return null

        val last = sorted.last()
        val nextExpected = last.timestampMillis + cadence.days.toLong() * DAY_MILLIS
        val monthlyCost = monthlyEquivalent(average.toLong(), cadence.days)

        return Recurring(
            merchantNormalized = key,
            displayName = last.merchantRaw.ifBlank { key },
            averageAmountMinor = average.toLong(),
            currency = last.currency,
            cadenceDays = cadence.days,
            occurrences = sorted.size,
            lastChargedMillis = last.timestampMillis,
            nextExpectedMillis = nextExpected,
            monthlyCostMinor = monthlyCost,
        )
    }

    /** Normalises any cadence to a comparable monthly cost. */
    fun monthlyEquivalent(amountMinor: Long, cadenceDays: Int): Long {
        if (cadenceDays <= 0) return amountMinor
        val perMonth = 30.0 / cadenceDays.toDouble()
        return (amountMinor * perMonth).toLong()
    }

    fun cadenceLabel(cadenceDays: Int): String = when {
        cadenceDays <= 8 -> "Weekly"
        cadenceDays <= 16 -> "Every 2 weeks"
        cadenceDays <= 45 -> "Monthly"
        cadenceDays <= 120 -> "Quarterly"
        else -> "Yearly"
    }
}
