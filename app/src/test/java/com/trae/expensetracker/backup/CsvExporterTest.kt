package com.trae.expensetracker.backup

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvExporterTest {

    private val zone = ZoneId.of("UTC")

    private fun tx(
        id: String,
        amountMinor: Long,
        merchant: String,
        sourceId: String = "HBL-BANK",
        reference: String? = "REF1",
        transferGroupId: String? = null,
        duplicateOfId: String? = null,
        refundOfId: String? = null,
    ) = TransactionEntity(
        id = id,
        timestampMillis = 1_756_000_000_000L,
        sourceId = sourceId,
        type = TransactionType.DEBIT_PURCHASE,
        direction = TransactionDirection.OUT,
        amountMinor = amountMinor,
        currency = "PKR",
        merchantRaw = merchant,
        merchantNormalized = merchant.lowercase(),
        categoryId = "Food",
        reference = reference,
        externalId = id,
        rawMessage = null,
        transferGroupId = transferGroupId,
        duplicateOfId = duplicateOfId,
        refundOfId = refundOfId,
    )

    @Test
    fun `header is stable`() {
        val csv = CsvExporter.build(emptyList(), emptyMap(), zone)

        assertEquals(CsvExporter.HEADER.joinToString(","), csv.trim())
    }

    @Test
    fun `amounts are exported as plain decimals`() {
        assertEquals("3100.00", CsvExporter.formatAmount(310_000))
        assertEquals("0.05", CsvExporter.formatAmount(5))
        assertEquals("-12.50", CsvExporter.formatAmount(-1_250))
    }

    @Test
    fun `merchant names containing commas are quoted`() {
        val csv = CsvExporter.build(
            listOf(tx("a", 10_000, "SHOP, LAHORE")),
            mapOf("HBL-BANK" to "HBL"),
            zone,
        )

        assertTrue(csv.contains("\"SHOP, LAHORE\""))
    }

    @Test
    fun `embedded quotes are escaped by doubling`() {
        val csv = CsvExporter.build(
            listOf(tx("a", 10_000, "JOE\"S CAFE")),
            mapOf("HBL-BANK" to "HBL"),
            zone,
        )

        assertTrue(csv.contains("\"JOE\"\"S CAFE\""))
    }

    @Test
    fun `one row per transaction plus header`() {
        val csv = CsvExporter.build(
            listOf(tx("a", 10_000, "A"), tx("b", 20_000, "B")),
            mapOf("HBL-BANK" to "HBL"),
            zone,
        )

        assertEquals(3, csv.trim().lines().size)
    }

    @Test
    fun `source names are resolved from the map`() {
        val csv = CsvExporter.build(
            listOf(tx("a", 10_000, "A")),
            mapOf("HBL-BANK" to "HBL Bank"),
            zone,
        )

        assertTrue(csv.contains("HBL Bank"))
    }

    @Test
    fun `excluded rows are labelled with the reason`() {
        val csv = CsvExporter.build(
            listOf(
                tx("transfer", 10_000, "A", transferGroupId = "g1"),
                tx("dupe", 10_000, "B", duplicateOfId = "orig"),
                tx("refund", 10_000, "C", refundOfId = "orig"),
                tx("normal", 10_000, "D"),
            ),
            mapOf("HBL-BANK" to "HBL"),
            zone,
        )

        assertTrue(csv.contains("internal transfer"))
        assertTrue(csv.contains("duplicate"))
        assertTrue(csv.contains("refund"))
    }

    @Test
    fun `exclusion reason is empty for a normal transaction`() {
        assertEquals("", CsvExporter.exclusionReason(tx("a", 10_000, "A")))
    }
}
