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
            type = TransactionType.TRANSFER_IN,
            direction = TransactionDirection.IN,
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

