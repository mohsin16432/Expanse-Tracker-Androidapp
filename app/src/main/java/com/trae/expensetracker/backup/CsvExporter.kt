package com.trae.expensetracker.backup

import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.repo.DataSourceRepository
import com.trae.expensetracker.ui.CategoryRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds a spreadsheet-friendly CSV of transactions.
 *
 * Written for real spreadsheets: the header is stable, dates are ISO-8601, amounts are plain
 * decimal numbers (not formatted currency), and every field is quoted so commas in merchant
 * names cannot break the columns.
 */
object CsvExporter {

    val HEADER = listOf(
        "Date",
        "Time",
        "Type",
        "Direction",
        "Amount",
        "Currency",
        "Category",
        "Merchant",
        "Source",
        "Reference",
        "Excluded from totals",
    )

    fun build(
        transactions: List<TransactionEntity>,
        sourceNames: Map<String, String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)

        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",") { escape(it) }).append('\n')

        transactions.sortedByDescending { it.timestampMillis }.forEach { tx ->
            val instant = Instant.ofEpochMilli(tx.timestampMillis)
            sb.append(
                listOf(
                    dateFmt.format(instant),
                    timeFmt.format(instant),
                    tx.type.name,
                    tx.direction.name,
                    formatAmount(tx.amountMinor),
                    tx.currency,
                    CategoryRules.detect(tx),
                    tx.merchantRaw,
                    sourceNames[tx.sourceId] ?: tx.sourceId,
                    tx.reference.orEmpty(),
                    exclusionReason(tx),
                ).joinToString(",") { escape(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    /** Amounts are exported as decimal major units so spreadsheets treat them as numbers. */
    fun formatAmount(amountMinor: Long): String {
        val negative = amountMinor < 0
        val abs = kotlin.math.abs(amountMinor)
        val major = abs / 100
        val cents = abs % 100
        val text = "$major.${cents.toString().padStart(2, '0')}"
        return if (negative) "-$text" else text
    }

    /** Documents why a row is missing from spend/income totals. */
    fun exclusionReason(tx: TransactionEntity): String = when {
        tx.transferGroupId != null -> "internal transfer"
        tx.duplicateOfId != null -> "duplicate"
        tx.refundOfId != null -> "refund"
        else -> ""
    }

    /** RFC 4180 escaping: wrap in quotes and double any embedded quotes. */
    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val cleaned = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$cleaned\"" else cleaned
    }
}
