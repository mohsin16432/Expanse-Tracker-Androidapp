package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import java.util.Locale

/**
 * Parses common local bank/Raast transfers without a bank-specific parser.
 *
 * The goal is to support variations like:
 * "PKR. 3,100.00 received from PK*JCMA0070 in AKBL PKASCM*0061 MOHSIN MUSTAFA
 *  via Raast on 07 09 26 at 16 06 Ref# 013160614887"
 *
 * This intentionally uses the bank/account identified in the body as the source,
 * because numeric senders such as 8870 are shared by many banks.
 */
object GenericBankParser : BankSmsParser {
    private val incomingRaast = Regex(
        pattern = """\b(PKR\.?\s*[0-9,]+(?:\.[0-9]{1,2})?|Rs\.?\s*[0-9,]+(?:\.[0-9]{1,2})?)\s+received\s+from\s+(.+?)\s+in\s+([A-Za-z]{2,8})(?:\s+([A-Za-z0-9*/-]{5,}))?.*?\bvia\s+Raast\b.*?\bon\s+(\d{1,2}\s+\d{1,2}\s+\d{2,4})\s+at\s+(\d{1,2}\s+\d{2})\b.*?Ref\s*#\s*([A-Za-z0-9/-]+)""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val outgoingRaast = Regex(
        pattern = """\b(PKR\.?\s*[0-9,]+(?:\.[0-9]{1,2})?|Rs\.?\s*[0-9,]+(?:\.[0-9]{1,2})?)\s+(?:sent|transferred)\s+to\s+(.+?)\s+in\s+([A-Za-z]{2,8})(?:\s+([A-Za-z0-9*/-]{5,}))?.*?\bvia\s+Raast\b.*?\bon\s+(\d{1,2}\s+\d{1,2}\s+\d{2,4})\s+at\s+(\d{1,2}\s+\d{2})\b.*?Ref\s*#\s*([A-Za-z0-9/-]+)""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override fun parse(message: SmsMessage): TransactionDraft? {
        val body = message.body.trim()
        val isSms = message.sender.isNotBlank() && !message.sender.contains('.', ignoreCase = true)
        if (!isSms) return null

        val incoming = incomingRaast.find(body)
        if (incoming != null) {
            return draft(
                message = message,
                match = incoming,
                direction = TransactionDirection.IN,
                type = TransactionType.TRANSFER_IN,
                fallbackVerb = "received",
            )
        }

        val outgoing = outgoingRaast.find(body)
        if (outgoing != null) {
            return draft(
                message = message,
                match = outgoing,
                direction = TransactionDirection.OUT,
                type = TransactionType.TRANSFER_OUT,
                fallbackVerb = "sent",
            )
        }

        return null
    }

    private fun draft(
        message: SmsMessage,
        match: MatchResult,
        direction: TransactionDirection,
        type: TransactionType,
        fallbackVerb: String,
    ): TransactionDraft? {
        val amountRaw = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val (currency, minor) = ParseUtils.parseAmountMinor(amountRaw) ?: return null
        val counterparty = match.groupValues.getOrNull(2)?.trim().takeUnless { it.isNullOrBlank() }
            ?: fallbackVerb.ifBlank { "Raast transfer" }
        val bankCode = match.groupValues.getOrNull(3)?.trim()?.uppercase(Locale.US).orEmpty()
        val account = match.groupValues.getOrNull(4)?.trim().orEmpty()
        val date = match.groupValues.getOrNull(5)?.trim().orEmpty()
        val time = match.groupValues.getOrNull(6)?.trim().orEmpty()
        val reference = match.groupValues.getOrNull(7)?.trim().takeUnless { it.isNullOrBlank() }

        if (bankCode.isBlank()) return null

        val timestamp = ParseUtils.tryParseDateMillis(
            text = "$date $time",
            patterns = listOf(
                "dd MM yy HH mm",
                "dd MM yyyy HH mm",
                "dd-MM-yy HH mm",
                "dd-MM-yyyy HH mm",
                "dd/MM/yy HH mm",
                "dd/MM/yyyy HH mm",
            ),
        ) ?: message.receivedAtMillis

        val sourceShortCode = bankCode
        val sourceName = listOf(bankCode, account)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val externalId = ParseUtils.sha256Hex(
            listOf(
                "generic_raast",
                sourceShortCode,
                direction.name,
                amountRaw,
                counterparty,
                date,
                time,
                reference.orEmpty(),
            ).joinToString("|")
        )

        return TransactionDraft(
            sourceShortCode = sourceShortCode,
            sourceNameHint = sourceName,
            sourceTypeHint = DataSourceType.BANK,
            timestampMillis = timestamp,
            type = type,
            direction = direction,
            amountMinor = kotlin.math.abs(minor),
            currency = currency,
            merchantRaw = counterparty,
            reference = reference,
            rawMessage = message.body.trim(),
            externalId = externalId,
            confidence = 0.91,
        )
    }
}
