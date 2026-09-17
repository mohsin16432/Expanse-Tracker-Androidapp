package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import java.util.Locale

class WalletNotificationParser : BankSmsParser {
    override fun parse(message: SmsMessage): TransactionDraft? {
        val body = message.body.trim()
        val sender = message.sender.trim()
        if (!isSupportedNotification(sender, body)) return null

        val sourceCode = notificationSourceCode(sender, body)
        val sourceName = sourceName(sender, body)
        val sourceType = if (sourceCode.contains('.')) DataSourceType.NOTIFICATION else DataSourceType.WALLET

        val detailedSent = Regex(
            """amount\s+of\s+(Rs\.?\s*[0-9,]+(?:\.[0-9]{1,2})?).*?successfully\s+sent\s+to\s+(.+?)\s+in\s+.*?\s+on\s+(\d{4}-\d{2}-\d{2})\s+at\s+(\d{2}:\d{2}:\d{2}).*?(?:trx|transaction)\s*id:?\s*(\d+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(body)
        if (detailedSent != null) {
            val amountStr = detailedSent.groupValues[1]
            val merchant = normalizeParty(detailedSent.groupValues[2])
            val dateStr = detailedSent.groupValues[3]
            val timeStr = detailedSent.groupValues[4]
            val ref = detailedSent.groupValues[5]
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis("$dateStr $timeStr", listOf("yyyy-MM-dd HH:mm:ss"), Locale.US)
                ?: message.receivedAtMillis
            return TransactionDraft(
                sourceShortCode = sourceCode,
                sourceNameHint = sourceName,
                sourceTypeHint = sourceType,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_OUT,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = merchant,
                reference = ref,
                rawMessage = body,
                externalId = ParseUtils.sha256Hex("${sourceName.lowercase(Locale.US)}|$sourceCode|$ref|$amountStr|$dateStr $timeStr"),
                confidence = 0.93,
            )
        }

        val shortSent = Regex(
            """(Rs\.?\s*[0-9,]+(?:\.[0-9]{1,2})?)\s+(?:sent|paid)\s+(?:to|for)\s+(.+?)(?:\.|$)""",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (shortSent != null) {
            val amountStr = shortSent.groupValues[1]
            val merchant = normalizeParty(shortSent.groupValues[2])
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            return TransactionDraft(
                sourceShortCode = sourceCode,
                sourceNameHint = sourceName,
                sourceTypeHint = sourceType,
                timestampMillis = message.receivedAtMillis,
                type = TransactionType.TRANSFER_OUT,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = merchant,
                reference = null,
                rawMessage = body,
                externalId = ParseUtils.sha256Hex("${sourceName.lowercase(Locale.US)}|$sourceCode|$amountStr|$merchant|${message.receivedAtMillis}"),
                confidence = 0.89,
            )
        }

        val shortReceived = Regex(
            """(Rs\.?\s*[0-9,]+(?:\.[0-9]{1,2})?)\s+received\s+from\s+(.+?)(?:\.|$)""",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (shortReceived != null) {
            val amountStr = shortReceived.groupValues[1]
            val merchant = normalizeParty(shortReceived.groupValues[2])
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            return TransactionDraft(
                sourceShortCode = sourceCode,
                sourceNameHint = sourceName,
                sourceTypeHint = sourceType,
                timestampMillis = message.receivedAtMillis,
                type = TransactionType.TRANSFER_IN,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = merchant,
                reference = null,
                rawMessage = body,
                externalId = ParseUtils.sha256Hex("${sourceName.lowercase(Locale.US)}|$sourceCode|$amountStr|$merchant|${message.receivedAtMillis}"),
                confidence = 0.88,
            )
        }

        // Wallet load / top-up notifications (e.g., NayaPay):
        // "Rs. 1 loaded through Faysal Bank-1333 linked account. Just like that"
        val loaded = Regex(
            """(Rs\.?\s*[0-9,]+(?:\.[0-9]{1,2})?)\s+loaded\s+through\s+(.+?)(?:\.|$)""",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (loaded != null) {
            val amountStr = loaded.groupValues[1]
            val via = normalizeParty(loaded.groupValues[2])
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            return TransactionDraft(
                sourceShortCode = sourceCode,
                sourceNameHint = sourceName,
                sourceTypeHint = sourceType,
                timestampMillis = message.receivedAtMillis,
                type = TransactionType.TRANSFER_IN,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = via.ifBlank { "Top-up" },
                reference = null,
                rawMessage = body,
                externalId = ParseUtils.sha256Hex("${sourceName.lowercase(Locale.US)}|$sourceCode|loaded|$amountStr|$via|${message.receivedAtMillis}"),
                confidence = 0.9,
            )
        }

        return null
    }

    private fun isSupportedNotification(sender: String, body: String): Boolean {
        val s = "$sender $body".lowercase(Locale.US)
        val hasMoney = s.contains("rs") || s.contains("pkr") || Regex("""\b[0-9,]+(?:\.[0-9]{1,2})?\b""").containsMatchIn(s)
        val hasVerb = listOf("sent", "paid", "received", "credited", "debited", "transfer", "payment", "loaded", "top up", "top-up").any { s.contains(it) }
        return sender.contains('.') && hasMoney && hasVerb
    }

    private fun notificationSourceCode(sender: String, body: String): String {
        val s = sender.trim()
        return if (s.contains('.')) s else sourceName(sender, body)
    }

    private fun sourceName(sender: String, body: String): String {
        val s = "$sender $body".lowercase(Locale.US)
        return when {
            s.contains("nayapay") || s.contains("naya pay") -> "NayaPay"
            s.contains("easypaisa") || s.contains("easy paisa") -> "Easypaisa"
            s.contains("jazzcash") || s.contains("jazz cash") -> "JazzCash"
            else -> sender.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }

    private fun normalizeParty(raw: String): String {
        return raw
            .replace(Regex("""\s+Your\s+wallet.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+from your .*?$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+via .*?$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
