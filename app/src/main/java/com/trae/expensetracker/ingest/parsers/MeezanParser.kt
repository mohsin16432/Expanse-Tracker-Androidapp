package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import java.util.Locale

/**
 * Meezan Bank cheque inward clearing SMS.
 *
 * Example:
 * Meezan:(8079) Your cheque#28101501 of PKR 154125.00 drawn on a/c xxxxxxxxxx9618
 * is received in inward clearing on 22-06-26 at Meezan Bank.
 */
class MeezanParser : BankSmsParser {
    override fun parse(message: SmsMessage): TransactionDraft? {
        val body = message.body.trim()
        val sender = message.sender.trim()
        if (!isMeezanSender(sender, body)) return null

        // Example:
        // "PKR 155,000.00 received from MOHSIN HBL-xxx2703 to A/C xxx9618 of F-8 MARKAZ BR ISD on 25-May-2026 at 12:21"
        val receivedTransfer = Regex(
            "(PKR\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?received\\s+from\\s+(.+?)\\s+to\\s+A/C\\s+([xX*0-9]+).*?\\s+on\\s+(\\d{1,2}-[A-Za-z]{3}-\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2})",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (receivedTransfer != null) {
            val amountStr = receivedTransfer.groupValues[1].trim()
            val from = receivedTransfer.groupValues[2].trim()
            val accountMasked = receivedTransfer.groupValues[3].trim()
            val dateStr = receivedTransfer.groupValues[4].trim()
            val timeStr = receivedTransfer.groupValues[5].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = "$dateStr $timeStr",
                patterns = listOf("dd-MMM-yyyy HH:mm", "dd-MMM-yy HH:mm", "d-MMM-yyyy HH:mm"),
                locale = Locale.US
            ) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("8079|$amountStr|$dateStr $timeStr|$from|$accountMasked")
            return TransactionDraft(
                sourceShortCode = "8079",
                sourceNameHint = "Meezan Bank",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_IN,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = from,
                reference = null,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.88,
            )
        }

        val m = Regex(
            "cheque#(\\d+).*?of\\s+PKR\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?inward clearing on\\s+(\\d{1,2}-\\d{1,2}-\\d{2})",
            RegexOption.IGNORE_CASE
        ).find(body) ?: return null

        val chequeNo = m.groupValues[1].trim()
        val amountStr = "PKR " + m.groupValues[2].trim()
        val dateStr = m.groupValues[3].trim()

        val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
        val ts = ParseUtils.tryParseDateMillis(dateStr, listOf("dd-MM-yy"), Locale.US) ?: message.receivedAtMillis
        val externalId = ParseUtils.sha256Hex("8079|$chequeNo|$amountStr|$ts")

        return TransactionDraft(
            sourceShortCode = "8079",
            sourceNameHint = "Meezan Bank",
            sourceTypeHint = DataSourceType.BANK,
            timestampMillis = ts,
            // "cheque ... drawn on a/c ... received in inward clearing" indicates an issued cheque
            // being presented for clearing (outflow from your account).
            type = TransactionType.TRANSFER_OUT,
            direction = TransactionDirection.OUT,
            amountMinor = kotlin.math.abs(minor),
            currency = currency,
            merchantRaw = "Cheque $chequeNo (Inward clearing)",
            reference = chequeNo,
            rawMessage = body,
            externalId = externalId,
            confidence = 0.86,
        )
    }

    private fun isMeezanSender(sender: String, body: String): Boolean {
        val s = sender.trim().uppercase(Locale.US)
        if (s == "8079" || s.contains("8079") || s.contains("MEEZAN")) return true
        val b = body.uppercase(Locale.US)
        return b.contains("MEEZAN") && b.contains("(8079)")
    }
}
