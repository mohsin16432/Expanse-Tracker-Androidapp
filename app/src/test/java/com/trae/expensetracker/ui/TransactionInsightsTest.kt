package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionInsightsTest {

    private fun tx(
        id: String,
        direction: TransactionDirection,
        amountMinor: Long,
        sourceId: String = "HBL-BANK",
        type: TransactionType = TransactionType.DEBIT_PURCHASE,
        transferGroupId: String? = null,
        duplicateOfId: String? = null,
        refundOfId: String? = null,
        timestampMillis: Long = 1_000_000L,
    ) = TransactionEntity(
        id = id,
        timestampMillis = timestampMillis,
        sourceId = sourceId,
        type = type,
        direction = direction,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = "TEST",
        merchantNormalized = "test",
        categoryId = null,
        reference = null,
        externalId = id,
        rawMessage = null,
        transferGroupId = transferGroupId,
        duplicateOfId = duplicateOfId,
        refundOfId = refundOfId,
    )

    @Test
    fun `normal purchases count towards outgoing`() {
        val items = listOf(
            tx("a", TransactionDirection.OUT, 5_000),
            tx("b", TransactionDirection.OUT, 2_500),
            tx("c", TransactionDirection.IN, 90_000, type = TransactionType.TRANSFER_IN),
        )

        assertEquals(7_500L, TransactionInsights.outgoingTotalMinor(items))
        assertEquals(90_000L, TransactionInsights.incomingTotalMinor(items))
    }

    @Test
    fun `both legs of an internal transfer are excluded from totals`() {
        val items = listOf(
            tx(
                "out-leg",
                TransactionDirection.OUT,
                310_000,
                sourceId = "HBL-BANK",
                type = TransactionType.TRANSFER_OUT,
                transferGroupId = "grp-1",
            ),
            tx(
                "in-leg",
                TransactionDirection.IN,
                310_000,
                sourceId = "MEEZAN-BANK",
                type = TransactionType.TRANSFER_IN,
                transferGroupId = "grp-1",
            ),
        )

        assertEquals(0L, TransactionInsights.outgoingTotalMinor(items))
        assertEquals(0L, TransactionInsights.incomingTotalMinor(items))
    }

    @Test
    fun `flagged duplicates do not inflate totals`() {
        val items = listOf(
            tx("original", TransactionDirection.OUT, 12_000),
            tx("dupe", TransactionDirection.OUT, 12_000, duplicateOfId = "original"),
        )

        assertEquals(12_000L, TransactionInsights.outgoingTotalMinor(items))
        assertFalse(TransactionInsights.isExcludedFromTotals(items[0]))
        assertTrue(TransactionInsights.isExcludedFromTotals(items[1]))
    }

    @Test
    fun `internal transfer groups collapse both legs into one entry`() {
        val items = listOf(
            tx(
                "out-leg",
                TransactionDirection.OUT,
                310_000,
                sourceId = "HBL-BANK",
                type = TransactionType.TRANSFER_OUT,
                transferGroupId = "grp-1",
                timestampMillis = 2_000L,
            ),
            tx(
                "in-leg",
                TransactionDirection.IN,
                310_000,
                sourceId = "MEEZAN-BANK",
                type = TransactionType.TRANSFER_IN,
                transferGroupId = "grp-1",
                timestampMillis = 2_500L,
            ),
        )

        val groups = TransactionInsights.internalTransferGroups(items)
        assertEquals(1, groups.size)
        assertEquals(310_000L, groups[0].amountMinor)
        assertEquals("HBL-BANK", groups[0].fromSourceId)
        assertEquals("MEEZAN-BANK", groups[0].toSourceId)
        assertEquals(2_000L, groups[0].timestampMillis)
    }

    @Test
    fun `a refund reduces spending and is not counted as income`() {
        val items = listOf(
            tx("charge", TransactionDirection.OUT, 8_000, type = TransactionType.CARD_CHARGE),
            tx(
                "refund",
                TransactionDirection.IN,
                8_000,
                type = TransactionType.ADJUSTMENT,
                refundOfId = "charge",
            ),
        )

        // The refund reverses the charge rather than adding income.
        assertEquals(0L, TransactionInsights.outgoingTotalMinor(items))
        assertEquals(0L, TransactionInsights.incomingTotalMinor(items))
        assertEquals(8_000L, TransactionInsights.refundTotalMinor(items))
    }

    @Test
    fun `a partial refund only reduces spending by the refunded amount`() {
        val items = listOf(
            tx("charge", TransactionDirection.OUT, 10_000, type = TransactionType.CARD_CHARGE),
            tx(
                "refund",
                TransactionDirection.IN,
                4_000,
                type = TransactionType.ADJUSTMENT,
                refundOfId = "charge",
            ),
        )

        assertEquals(6_000L, TransactionInsights.outgoingTotalMinor(items))
        assertEquals(0L, TransactionInsights.incomingTotalMinor(items))
    }

    @Test
    fun `transfer between own accounts does not count as spending`() {
        // Mirrors the Raast scenario: 3,100 PKR moved HBL -> Meezan.
        val items = listOf(
            tx(
                "hbl-out",
                TransactionDirection.OUT,
                310_000,
                sourceId = "HBL-BANK",
                type = TransactionType.TRANSFER_OUT,
                transferGroupId = "grp-raast",
            ),
            tx(
                "meezan-in",
                TransactionDirection.IN,
                310_000,
                sourceId = "MEEZAN-BANK",
                type = TransactionType.TRANSFER_IN,
                transferGroupId = "grp-raast",
            ),
            tx("groceries", TransactionDirection.OUT, 4_200, type = TransactionType.DEBIT_PURCHASE),
        )

        assertEquals(4_200L, TransactionInsights.outgoingTotalMinor(items))
        assertEquals(0L, TransactionInsights.incomingTotalMinor(items))
    }
}
