package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceEngineTest {

    private fun source(id: String, name: String, opening: Long? = null) = DataSourceEntity(
        id = id,
        name = name,
        shortCode = id,
        type = DataSourceType.BANK,
        openingBalanceMinor = opening,
        openingBalanceMillis = opening?.let { 1L },
    )

    private fun tx(
        id: String,
        sourceId: String,
        direction: TransactionDirection,
        amountMinor: Long,
        duplicateOfId: String? = null,
    ) = TransactionEntity(
        id = id,
        timestampMillis = 1_000L,
        sourceId = sourceId,
        type = TransactionType.DEBIT_PURCHASE,
        direction = direction,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = "M",
        merchantNormalized = "m",
        categoryId = null,
        reference = null,
        externalId = id,
        rawMessage = null,
        duplicateOfId = duplicateOfId,
    )

    @Test
    fun `opening balance plus incoming minus outgoing`() {
        val s = source("HBL", "HBL", opening = 100_000)
        val txs = listOf(
            tx("a", "HBL", TransactionDirection.OUT, 25_000),
            tx("b", "HBL", TransactionDirection.IN, 10_000),
        )

        val balance = BalanceEngine.balanceFor(s, txs)

        assertEquals(85_000L, balance.balanceMinor)
        assertFalse(balance.isPartial)
    }

    @Test
    fun `balance is flagged partial when no opening balance is recorded`() {
        val s = source("HBL", "HBL", opening = null)
        val txs = listOf(tx("a", "HBL", TransactionDirection.OUT, 25_000))

        val balance = BalanceEngine.balanceFor(s, txs)

        // Without a starting point the figure is only the net movement.
        assertEquals(-25_000L, balance.balanceMinor)
        assertTrue(balance.isPartial)
    }

    @Test
    fun `only the source's own transactions are counted`() {
        val s = source("HBL", "HBL", opening = 100_000)
        val txs = listOf(
            tx("a", "HBL", TransactionDirection.OUT, 25_000),
            tx("b", "MEEZAN", TransactionDirection.OUT, 999_000),
        )

        assertEquals(75_000L, BalanceEngine.balanceFor(s, txs).balanceMinor)
    }

    @Test
    fun `duplicates do not move the balance twice`() {
        val s = source("HBL", "HBL", opening = 100_000)
        val txs = listOf(
            tx("real", "HBL", TransactionDirection.OUT, 25_000),
            tx("dupe", "HBL", TransactionDirection.OUT, 25_000, duplicateOfId = "real"),
        )

        assertEquals(75_000L, BalanceEngine.balanceFor(s, txs).balanceMinor)
    }

    @Test
    fun `internal transfers still move both account balances`() {
        // A transfer is excluded from spend/income totals but must affect balances.
        val hbl = source("HBL", "HBL", opening = 100_000)
        val meezan = source("MEEZAN", "Meezan", opening = 0)
        val txs = listOf(
            tx("out", "HBL", TransactionDirection.OUT, 310_000),
            tx("in", "MEEZAN", TransactionDirection.IN, 310_000),
        )

        assertEquals(-210_000L, BalanceEngine.balanceFor(hbl, txs).balanceMinor)
        assertEquals(310_000L, BalanceEngine.balanceFor(meezan, txs).balanceMinor)
    }

    @Test
    fun `total known balance ignores partial accounts`() {
        val balances = listOf(
            BalanceEngine.balanceFor(source("A", "A", opening = 50_000), emptyList()),
            BalanceEngine.balanceFor(source("B", "B", opening = null), emptyList()),
        )

        assertEquals(50_000L, BalanceEngine.totalKnownBalance(balances))
    }

    @Test
    fun `balances are ordered by absolute size`() {
        val sources = listOf(
            source("SMALL", "Small", opening = 1_000),
            source("BIG", "Big", opening = -900_000),
        )

        val balances = BalanceEngine.balances(sources, emptyList())

        assertEquals("BIG", balances[0].sourceId)
        assertEquals("SMALL", balances[1].sourceId)
    }
}
