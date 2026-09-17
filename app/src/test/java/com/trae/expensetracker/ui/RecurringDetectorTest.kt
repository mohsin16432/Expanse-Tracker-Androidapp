package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringDetectorTest {

    private val day = 24L * 60 * 60 * 1000

    private fun tx(
        id: String,
        merchant: String,
        amountMinor: Long,
        daysAgo: Long,
        direction: TransactionDirection = TransactionDirection.OUT,
        transferGroupId: String? = null,
        duplicateOfId: String? = null,
    ) = TransactionEntity(
        id = id,
        timestampMillis = 1_000L * day - daysAgo * day,
        sourceId = "HBL-BANK",
        type = TransactionType.DEBIT_PURCHASE,
        direction = direction,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = merchant,
        merchantNormalized = merchant.lowercase(),
        categoryId = null,
        reference = null,
        externalId = id,
        rawMessage = null,
        transferGroupId = transferGroupId,
        duplicateOfId = duplicateOfId,
    )

    @Test
    fun `three monthly charges are detected as a subscription`() {
        val txs = listOf(
            tx("1", "NETFLIX", 150_000, daysAgo = 90),
            tx("2", "NETFLIX", 150_000, daysAgo = 60),
            tx("3", "NETFLIX", 150_000, daysAgo = 30),
        )

        val found = RecurringDetector.detect(txs)

        assertEquals(1, found.size)
        assertEquals("netflix", found[0].merchantNormalized)
        assertEquals(30, found[0].cadenceDays)
        assertEquals(3, found[0].occurrences)
        assertEquals(150_000L, found[0].averageAmountMinor)
    }

    @Test
    fun `two charges are not enough`() {
        val txs = listOf(
            tx("1", "NETFLIX", 150_000, daysAgo = 60),
            tx("2", "NETFLIX", 150_000, daysAgo = 30),
        )

        assertTrue(RecurringDetector.detect(txs).isEmpty())
    }

    @Test
    fun `irregular shopping is not a subscription`() {
        val txs = listOf(
            tx("1", "DARAZ", 150_000, daysAgo = 90),
            tx("2", "DARAZ", 150_000, daysAgo = 61),
            tx("3", "DARAZ", 150_000, daysAgo = 3),
        )

        assertTrue(RecurringDetector.detect(txs).isEmpty())
    }

    @Test
    fun `wildly varying amounts are not a subscription`() {
        val txs = listOf(
            tx("1", "GROCERY", 10_000, daysAgo = 90),
            tx("2", "GROCERY", 500_000, daysAgo = 60),
            tx("3", "GROCERY", 20_000, daysAgo = 30),
        )

        assertTrue(RecurringDetector.detect(txs).isEmpty())
    }

    @Test
    fun `transfers and duplicates are ignored`() {
        val txs = listOf(
            tx("1", "MY ACCOUNT", 150_000, daysAgo = 90, transferGroupId = "g1"),
            tx("2", "MY ACCOUNT", 150_000, daysAgo = 60, transferGroupId = "g1"),
            tx("3", "MY ACCOUNT", 150_000, daysAgo = 30, duplicateOfId = "x"),
        )

        assertTrue(RecurringDetector.detect(txs).isEmpty())
    }

    @Test
    fun `incoming recurring credits are not treated as subscriptions`() {
        val txs = listOf(
            tx("1", "SALARY", 5_000_000, daysAgo = 90, direction = TransactionDirection.IN),
            tx("2", "SALARY", 5_000_000, daysAgo = 60, direction = TransactionDirection.IN),
            tx("3", "SALARY", 5_000_000, daysAgo = 30, direction = TransactionDirection.IN),
        )

        assertTrue(RecurringDetector.detect(txs).isEmpty())
    }

    @Test
    fun `monthly equivalent normalises cadence`() {
        // A weekly 1,000.00 charge works out at about 4,285.71 per month.
        assertEquals(428_571L, RecurringDetector.monthlyEquivalent(100_000, 7))
        // A yearly 12,000.00 charge works out at about 986.30 per month.
        assertEquals(98_630L, RecurringDetector.monthlyEquivalent(1_200_000, 365))
        // Monthly amounts pass through unchanged.
        assertEquals(150_000L, RecurringDetector.monthlyEquivalent(150_000, 30))
    }

    @Test
    fun `cadence labels are human readable`() {
        assertEquals("Weekly", RecurringDetector.cadenceLabel(7))
        assertEquals("Every 2 weeks", RecurringDetector.cadenceLabel(14))
        assertEquals("Monthly", RecurringDetector.cadenceLabel(30))
        assertEquals("Yearly", RecurringDetector.cadenceLabel(365))
    }
}
