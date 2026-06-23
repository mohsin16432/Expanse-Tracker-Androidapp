package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import java.util.Locale

class FblParser : BankSmsParser {
    override fun parse(message: SmsMessage): TransactionDraft? {
        val body = message.body.trim()
        val sender = message.sender.trim()
        if (!isFblSender(sender)) return null

        // Debit card purchase at Merchant
        // "PKR 6531.00 Debit Card purchase at Daraz, Karachi, * from FBL A/C *1333 on 16/JUN/2026 at 07:17:23 PM"
        val purchase = Regex(
            "(PKR\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?Debit Card purchase at (.+?) from .*? on (.+?) at (.+?)$",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (purchase != null) {
            val amountStr = purchase.groupValues[1]
            val merchant = purchase.groupValues[2].trim().trimEnd(',')
            val dateStr = purchase.groupValues[3].trim()
            val timeStr = purchase.groupValues[4].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = "$dateStr $timeStr",
                patterns = listOf("dd/MMM/yyyy hh:mm:ss a", "dd-MMM-yy hh:mm a", "dd-MMM-yyyy hh:mm a"),
                locale = Locale.US
            ) ?: ParseUtils.tryParseDateMillis(dateStr, listOf("dd/MMM/yyyy", "dd-MMM-yy"), Locale.US) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "8756",
                sourceNameHint = "Faysal / FBL",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = ts,
                type = TransactionType.DEBIT_PURCHASE,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = merchant,
                reference = null,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.92,
            )
        }

        // Received transfer
        // "PKR 7,500.00 received from ... on 16-Jun-26 at 07:15 PM Ref # 221915241623"
        val received = Regex(
            "(PKR\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?received from (.+?) on (.+?) at (.+?)(?: Ref # (\\d+))?",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (received != null) {
            val amountStr = received.groupValues[1]
            val from = received.groupValues[2].trim()
            val dateStr = received.groupValues[3].trim()
            val timeStr = received.groupValues[4].trim()
            val ref = received.groupValues.getOrNull(5)?.trim()?.ifBlank { null }

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = "$dateStr $timeStr",
                patterns = listOf("dd-MMM-yy hh:mm a", "dd-MMM-yyyy hh:mm a", "dd/MMM/yyyy hh:mm:ss a"),
                locale = Locale.US
            ) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "8756",
                sourceNameHint = "Faysal / FBL",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_IN,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = from,
                reference = ref,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.9,
            )
        }

        // Sent transfer
        // "PKR 11,400.00 sent to ATIF ... on 09/JUN/2026 at 01:12 PM"
        val sent = Regex(
            "(PKR\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?sent to (.+?) .*? on (.+?) at (.+?)$",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (sent != null) {
            val amountStr = sent.groupValues[1]
            val to = sent.groupValues[2].trim()
            val dateStr = sent.groupValues[3].trim()
            val timeStr = sent.groupValues[4].trim()
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = "$dateStr $timeStr",
                patterns = listOf("dd/MMM/yyyy hh:mm a", "dd-MMM-yy hh:mm a", "dd-MMM-yyyy hh:mm a"),
                locale = Locale.US
            ) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "8756",
                sourceNameHint = "Faysal / FBL",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_OUT,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = to,
                reference = null,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.85,
            )
        }

        return null
    }

    private fun isFblSender(sender: String): Boolean {
        val s = sender.trim().uppercase(Locale.US)
        return s == "8756" || s.contains("8756") || s.contains("FBL") || s.contains("FAYSAL")
    }
}
