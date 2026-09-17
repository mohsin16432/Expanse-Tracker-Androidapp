package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import java.util.Locale

class HblParser : BankSmsParser {
    override fun parse(message: SmsMessage): TransactionDraft? {
        val body = message.body.trim()
        val sender = message.sender.trim()
        if (!isHblSender(sender)) return null

        // Exclude withdrawals early (but keep a safety check here too).
        if (body.lowercase().contains("cash withdrawal")) return null

        // --- HBL Credit Card charge ---
        // Example: "Dear Customer, Your HBL CreditCard (ending with 9529) has been charged at FOOD PANDA for PKR-1,405.99 on 11/Jun/2026."
        val ccCharge = Regex(
            "CreditCard \\(ending with (\\d{4})\\).*has been charged at (.+?) for (.+?) on (.+?)[\\.|$]",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (ccCharge != null) {
            val last4 = ccCharge.groupValues[1].trim()
            val merchant = ccCharge.groupValues[2].trim()
            val amountStr = ccCharge.groupValues[3].trim()
            val dateStr = ccCharge.groupValues[4].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = dateStr,
                patterns = listOf("dd/MMM/yyyy", "dd/MM/yyyy", "dd-MMM-yy", "dd-MMM-yyyy", "dd/MMM/yy"),
                locale = Locale.US
            ) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "14250",
                sourceNameHint = "HBL Credit Card $last4",
                sourceTypeHint = DataSourceType.CREDIT_CARD,
                cardLast4Hint = last4,
                timestampMillis = ts,
                type = TransactionType.CARD_CHARGE,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor), // store positive minor for OUT flows
                currency = currency,
                merchantRaw = merchant,
                reference = null,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.95,
            )
        }

        // --- HBL account credited ---
        // Example: "Your HBL account no ... has been credited with PKR 847.75 on 6/1/2026 from OGDC ..."
        val credit = Regex(
            "has been credited with (.+?) on (.+?)(?: from (.+?)(?:\\s|$)|\\.)",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (credit != null) {
            val amountStr = credit.groupValues[1].trim()
            val dateStr = credit.groupValues[2].trim()
            val from = credit.groupValues.getOrNull(3)?.trim().orEmpty().ifBlank { "CREDIT" }
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = dateStr,
                patterns = listOf("d/M/yyyy", "dd/MM/yyyy", "d/M/yy", "dd/MM/yy"),
                locale = Locale.US
            ) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "14250",
                sourceNameHint = "HBL",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = ts,
                type = TransactionType.CREDIT_RECEIVED,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = from,
                reference = null,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.9,
            )
        }

        // --- Dividend credited (often mentions Meezan Bank dividend but credited in HBL account) ---
        // Example:
        // "Payment of Interim cash dividend (D-45) of Meezan Bank of PKR 370 is credited in your account PK98... with HABIB BANK LIMITED."
        val dividend = Regex(
            "Payment of\\s+(.+?)\\s+cash dividend\\s*\\((D-\\d+)\\)\\s+of\\s+(.+?)\\s+of\\s+(PKR\\s*[0-9,]+(?:\\.[0-9]{1,2})?)\\s+is credited in your account\\s+([A-Za-z0-9Xx*]+)\\s+with\\s+HABIB BANK LIMITED",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (dividend != null) {
            val kind = dividend.groupValues[1].trim()
            val code = dividend.groupValues[2].trim()
            val company = dividend.groupValues[3].trim()
            val amountStr = dividend.groupValues[4].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "14250",
                sourceNameHint = "HBL",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = message.receivedAtMillis,
                type = TransactionType.CREDIT_RECEIVED,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = "$company dividend $code ($kind)",
                reference = code,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.88,
            )
        }

        // --- HBL account debited / funds transfer ---
        // Example: "Your HBL A/C 2290***12703 has been debited with PKR 100.00 on 17/06/2026 for ATM Funds Transfer."
        val debited = Regex(
            "has been debited with (.+?) on (.+?) for (.+?)[\\.|$]",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (debited != null) {
            val amountStr = debited.groupValues[1].trim()
            val dateStr = debited.groupValues[2].trim()
            val purpose = debited.groupValues[3].trim()
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = dateStr,
                patterns = listOf("dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "d/M/yy"),
                locale = Locale.US
            ) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "14250",
                sourceNameHint = "HBL",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_OUT,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = purpose,
                reference = null,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.92,
            )
        }

        // --- HBL Debit Card charged ---
        // Example: "Your HBL Debit Card has been charged for a Transaction of PKR 3,124.00 on 29/04/2026 ."
        val debitCard = Regex(
            "Debit Card has been charged.*?of (.+?) on (.+?)[\\.|$]",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (debitCard != null) {
            val amountStr = debitCard.groupValues[1].trim()
            val dateStr = debitCard.groupValues[2].trim()
            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis(
                text = dateStr,
                patterns = listOf("dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "d/M/yy"),
                locale = Locale.US
            ) ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$body")
            return TransactionDraft(
                sourceShortCode = "14250",
                sourceNameHint = "HBL",
                sourceTypeHint = DataSourceType.BANK,
                timestampMillis = ts,
                type = TransactionType.DEBIT_PURCHASE,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = "DEBIT CARD",
                reference = null,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.75,
            )
        }

        // Statement SMS are useful for the Cards screen but not a transaction; keep for review later.
        return null
    }

    private fun isHblSender(sender: String): Boolean {
        val s = sender.trim().uppercase(Locale.US)
        return s == "14250" || s.contains("14250") || s.contains("HBL")
    }
}
