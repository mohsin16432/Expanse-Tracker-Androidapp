package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import java.util.Locale

class CreditCardChargeParser : BankSmsParser {
    override fun parse(message: SmsMessage): TransactionDraft? {
        val body = message.body.trim()
        val sender = message.sender.trim()

        val charge = Regex(
            """Dear\s+Customer,\s+Your\s+Credit\s+Card\s+has\s+been\s+charged\s+for\s+((?:PKR|USD)\s*[0-9,]+(?:\.[0-9]{1,2})?)\s+at\s+(\d{1,2}:\d{2})\s+on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(.+?)\.\s*Call\s+111-000-787\s+for\s+details\.?$""",
            RegexOption.IGNORE_CASE
        ).find(body) ?: return null

        val amountStr = charge.groupValues[1].trim()
        val timeStr = charge.groupValues[2].trim()
        val dateStr = charge.groupValues[3].trim()
        val merchant = charge.groupValues[4]
            .replace(Regex("\\s+"), " ")
            .trim()

        val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
        val ts = ParseUtils.tryParseDateMillis(
            text = "$dateStr $timeStr",
            patterns = listOf("dd/MM/yy HH:mm", "dd/MM/yyyy HH:mm"),
            locale = Locale.US
        ) ?: message.receivedAtMillis

        val sourceShortCode = sender.filter { it.isDigit() }.ifBlank { null } ?: return null
        return TransactionDraft(
            sourceShortCode = sourceShortCode,
            sourceNameHint = "Credit Card $sourceShortCode",
            sourceTypeHint = DataSourceType.CREDIT_CARD,
            cardLast4Hint = null,
            timestampMillis = ts,
            type = TransactionType.CARD_CHARGE,
            direction = TransactionDirection.OUT,
            amountMinor = kotlin.math.abs(minor),
            currency = currency,
            merchantRaw = merchant,
            reference = null,
            rawMessage = body,
            externalId = ParseUtils.sha256Hex("$sender|$body"),
            confidence = 0.93,
        )
    }
}
