package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.BudgetEntity
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetEngineTest {

    private fun tx(
        id: String,
        amountMinor: Long,
        categoryId: String?,
        merchant: String = "SHOP",
        direction: TransactionDirection = TransactionDirection.OUT,
        type: TransactionType = TransactionType.DEBIT_PURCHASE,
        transferGroupId: String? = null,
        duplicateOfId: String? = null,
        refundOfId: String? = null,
    ) = TransactionEntity(
        id = id,
        timestampMillis = 1_000L,
        sourceId = "HBL-BANK",
        type = type,
        direction = direction,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = merchant,
        merchantNormalized = merchant.lowercase(),
        categoryId = categoryId,
        reference = null,
        externalId = id,
        rawMessage = null,
        transferGroupId = transferGroupId,
        duplicateOfId = duplicateOfId,
        refundOfId = refundOfId,
    )

    private fun budget(id: String, categoryId: String?, limitMinor: Long, enabled: Boolean = true) = BudgetEntity(
        id = id,
        categoryId = categoryId,
        limitMinor = limitMinor,
        currency = "PKR",
        createdAtMillis = 1L,
        enabled = enabled,
    )

    private val categoryOf: (TransactionEntity) -> String = { it.categoryId ?: "Other" }

    @Test
    fun `category budget sums only that category`() {
        val spending = listOf(
            tx("a", 10_000, "Food"),
            tx("b", 5_000, "Food"),
            tx("c", 90_000, "Shopping"),
        )

        val progress = BudgetEngine.progress(listOf(budget("b1", "Food", 20_000)), spending, categoryOf)

        assertEquals(1, progress.size)
        assertEquals(15_000L, progress[0].spentMinor)
        assertEquals(5_000L, progress[0].remainingMinor)
        assertFalse(progress[0].isOverBudget)
        assertFalse(progress[0].isWarning)
    }

    @Test
    fun `overall budget sums all spending`() {
        val spending = listOf(
            tx("a", 10_000, "Food"),
            tx("b", 90_000, "Shopping"),
        )

        val progress = BudgetEngine.progress(listOf(budget("b1", null, 200_000)), spending, categoryOf)

        assertEquals(100_000L, progress[0].spentMinor)
        assertEquals("All spending", progress[0].categoryName)
    }

    @Test
    fun `crossing the warn threshold is flagged but not over budget`() {
        val spending = listOf(tx("a", 17_000, "Food"))

        val progress = BudgetEngine.progress(listOf(budget("b1", "Food", 20_000)), spending, categoryOf)

        assertTrue(progress[0].isWarning)
        assertFalse(progress[0].isOverBudget)
    }

    @Test
    fun `spending beyond the limit is over budget with no remainder`() {
        val spending = listOf(tx("a", 25_000, "Food"))

        val progress = BudgetEngine.progress(listOf(budget("b1", "Food", 20_000)), spending, categoryOf)

        assertTrue(progress[0].isOverBudget)
        assertEquals(0L, progress[0].remainingMinor)
    }

    @Test
    fun `disabled and zero-limit budgets are ignored`() {
        val spending = listOf(tx("a", 10_000, "Food"))

        val progress = BudgetEngine.progress(
            listOf(budget("b1", "Food", 20_000, enabled = false), budget("b2", "Food", 0)),
            spending,
            categoryOf,
        )

        assertTrue(progress.isEmpty())
    }

    @Test
    fun `progress is sorted by fraction descending`() {
        val spending = listOf(
            tx("a", 9_000, "Food"),
            tx("b", 10_000, "Shopping"),
        )

        val progress = BudgetEngine.progress(
            listOf(budget("b1", "Food", 10_000), budget("b2", "Shopping", 100_000)),
            spending,
            categoryOf,
        )

        assertEquals("Food", progress[0].categoryName)
    }

    @Test
    fun `pending alerts skip budgets already notified this cycle`() {
        val spending = listOf(tx("a", 25_000, "Food"))
        val budgets = listOf(budget("b1", "Food", 20_000))
        val progress = BudgetEngine.progress(budgets, spending, categoryOf)

        val cycleKey = "2026-08-26"
        val first = BudgetEngine.pendingAlerts(progress, cycleKey, alreadyNotified = emptySet())
        assertEquals(1, first.size)

        val key = BudgetEngine.alertKey("b1", cycleKey)
        val second = BudgetEngine.pendingAlerts(progress, cycleKey, alreadyNotified = setOf(key))
        assertEquals(0, second.size)

        // A new cycle must alert again even though the previous cycle was notified.
        val nextCycle = BudgetEngine.pendingAlerts(progress, "2026-09-26", alreadyNotified = setOf(key))
        assertEquals(1, nextCycle.size)
    }

    @Test
    fun `alert key is stable per budget and cycle`() {
        assertEquals("b1@2026-08-26", BudgetEngine.alertKey("b1", "2026-08-26"))
        assertEquals("b1@2026-09-26", BudgetEngine.alertKey("b1", "2026-09-26"))
    }
}
