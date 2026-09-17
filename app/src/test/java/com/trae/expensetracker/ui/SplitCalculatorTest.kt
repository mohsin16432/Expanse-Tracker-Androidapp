package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionSplitEntity
import com.trae.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SplitCalculatorTest {

    private fun tx(id: String, amountMinor: Long, categoryId: String? = null) = TransactionEntity(
        id = id,
        timestampMillis = 1_000L,
        sourceId = "HBL",
        type = TransactionType.DEBIT_PURCHASE,
        direction = TransactionDirection.OUT,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = "STORE",
        merchantNormalized = "store",
        categoryId = categoryId,
        reference = null,
        externalId = id,
        rawMessage = null,
    )

    private fun split(txId: String, category: String, amountMinor: Long) =
        TransactionSplitEntity(transactionId = txId, categoryId = category, amountMinor = amountMinor)

    private val categoryOf: (TransactionEntity) -> String = { it.categoryId ?: "Other" }

    @Test
    fun `transaction without splits is attributed to its own category`() {
        val transactions = listOf(tx("a", 10_000, "Food"))

        val totals = SplitCalculator.totalsByCategory(transactions, emptyMap(), categoryOf)

        assertEquals(10_000L, totals["Food"])
    }

    @Test
    fun `split transaction contributes to each of its categories`() {
        val transactions = listOf(tx("a", 10_000, null))
        val splits = mapOf(
            "a" to listOf(
                split("a", "Food", 6_000),
                split("a", "Household", 4_000),
            )
        )

        val totals = SplitCalculator.totalsByCategory(transactions, splits, categoryOf)

        assertEquals(6_000L, totals["Food"])
        assertEquals(4_000L, totals["Household"])
    }

    @Test
    fun `split totals never exceed the transaction amount`() {
        val transactions = listOf(tx("a", 10_000, null))
        val splits = mapOf(
            "a" to listOf(split("a", "Food", 6_000), split("a", "Household", 4_000))
        )

        val totals = SplitCalculator.totalsByCategory(transactions, splits, categoryOf)

        assertEquals(10_000L, totals.values.sum())
    }

    @Test
    fun `unsplit and split transactions combine correctly`() {
        val transactions = listOf(tx("a", 10_000, null), tx("b", 5_000, "Food"))
        val splits = mapOf(
            "a" to listOf(split("a", "Food", 4_000), split("a", "Bills", 6_000))
        )

        val totals = SplitCalculator.totalsByCategory(transactions, splits, categoryOf)

        assertEquals(9_000L, totals["Food"])
        assertEquals(6_000L, totals["Bills"])
    }

    @Test
    fun `amount for a specific category is returned`() {
        val transaction = tx("a", 10_000, null)
        val splits = listOf(split("a", "Food", 6_000), split("a", "Household", 4_000))

        assertEquals(6_000L, SplitCalculator.amountForCategory(transaction, splits, "Food"))
        assertEquals(0L, SplitCalculator.amountForCategory(transaction, splits, "Travel"))
    }

    @Test
    fun `valid splits pass validation`() {
        val splits = listOf(split("a", "Food", 6_000), split("a", "Household", 4_000))

        assertNull(SplitCalculator.validate(10_000, splits))
    }

    @Test
    fun `splits that do not sum to the total are rejected`() {
        val splits = listOf(split("a", "Food", 6_000), split("a", "Household", 3_000))

        assertNotNull(SplitCalculator.validate(10_000, splits))
    }

    @Test
    fun `zero or negative splits are rejected`() {
        val splits = listOf(split("a", "Food", 10_000), split("a", "Household", 0))

        assertNotNull(SplitCalculator.validate(10_000, splits))
    }

    @Test
    fun `no splits is always valid`() {
        assertNull(SplitCalculator.validate(10_000, emptyList()))
    }
}
