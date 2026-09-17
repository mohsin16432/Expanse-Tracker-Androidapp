package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportAnalyticsTest {

    private val zone = ZoneId.of("UTC")

    private fun tx(
        id: String,
        amountMinor: Long,
        category: String,
        merchant: String,
        direction: TransactionDirection = TransactionDirection.OUT,
        date: LocalDate = LocalDate.of(2026, 9, 10),
    ) = TransactionEntity(
        id = id,
        timestampMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        sourceId = "HBL-BANK",
        type = TransactionType.DEBIT_PURCHASE,
        direction = direction,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = merchant,
        merchantNormalized = merchant.lowercase(),
        categoryId = category,
        reference = null,
        externalId = id,
        rawMessage = null,
    )

    private val categoryOf: (TransactionEntity) -> String = { it.categoryId ?: "Other" }

    @Test
    fun `category breakdown totals and fractions are correct`() {
        val spending = listOf(
            tx("a", 75_000, "Food", "CAFE"),
            tx("b", 25_000, "Food", "BAKERY"),
            tx("c", 100_000, "Shopping", "DARAZ"),
        )

        val slices = ReportAnalytics.categoryBreakdown(spending, categoryOf)

        // Total is 200,000: Shopping 50%, Food 50%. Shopping sorts first on the total tie-break.
        assertEquals(2, slices.size)
        assertEquals(100_000L, slices[0].totalMinor)
        assertEquals(0.5, slices[0].fraction, 0.001)
        assertEquals(0.5, slices[1].fraction, 0.001)
        val byName = slices.associateBy { it.category }
        assertEquals(2, byName.getValue("Food").count)
        assertEquals(1, byName.getValue("Shopping").count)
    }

    @Test
    fun `empty spending produces no slices`() {
        assertTrue(ReportAnalytics.categoryBreakdown(emptyList(), categoryOf).isEmpty())
    }

    @Test
    fun `top merchants are ranked by total spend`() {
        val spending = listOf(
            tx("a", 10_000, "Food", "CAFE"),
            tx("b", 90_000, "Shopping", "DARAZ"),
            tx("c", 5_000, "Shopping", "DARAZ"),
        )

        val merchants = ReportAnalytics.topMerchants(spending, limit = 2)

        assertEquals(2, merchants.size)
        assertEquals("DARAZ", merchants[0].merchant)
        assertEquals(95_000L, merchants[0].totalMinor)
        assertEquals(2, merchants[0].count)
        assertEquals("CAFE", merchants[1].merchant)
    }

    @Test
    fun `monthly trend fills empty months with zero`() {
        val txs = listOf(
            tx("a", 50_000, "Food", "CAFE", date = LocalDate.of(2026, 9, 5)),
            tx("b", 20_000, "Food", "CAFE", direction = TransactionDirection.IN, date = LocalDate.of(2026, 9, 6)),
            tx("c", 30_000, "Food", "CAFE", date = LocalDate.of(2026, 7, 5)),
        )

        val trend = ReportAnalytics.monthlyTrend(
            transactions = txs,
            months = 3,
            today = LocalDate.of(2026, 9, 15),
            zone = zone,
        )

        assertEquals(3, trend.size)
        // Oldest first: July, August, September.
        assertEquals(30_000L, trend[0].spentMinor)
        assertEquals(0L, trend[1].spentMinor)
        assertEquals(50_000L, trend[2].spentMinor)
        assertEquals(20_000L, trend[2].incomeMinor)
    }

    @Test
    fun `average daily spend handles zero elapsed days`() {
        assertEquals(0L, ReportAnalytics.averageDailySpend(100_000, 0))
        assertEquals(10_000L, ReportAnalytics.averageDailySpend(100_000, 10))
    }

    @Test
    fun `projection extrapolates the current pace`() {
        // Halfway through the cycle having spent half the money => lands at 200,000.
        val projected = ReportAnalytics.projectedCycleSpend(
            totalSpentMinor = 100_000,
            daysElapsed = 15,
            daysInCycle = 30,
        )
        assertEquals(200_000L, projected)
    }

    @Test
    fun `projection is safe with no elapsed days`() {
        assertEquals(0L, ReportAnalytics.projectedCycleSpend(0, 0, 30))
    }
}
