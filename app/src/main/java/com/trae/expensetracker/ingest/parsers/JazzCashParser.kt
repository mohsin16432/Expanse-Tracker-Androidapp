package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import java.util.Locale

class JazzCashParser : BankSmsParser {
    override fun parse(message: SmsMessage): TransactionDraft? {
        val body = message.body.trim()
        val sender = message.sender.trim()
        if (!isJazzSender(sender)) return null

        // "Rs 500.00 successfully transferred to your Salaam Wallet on 16/06/2026 at 21:53:31. Transaction ID 714962..."
        val toWallet = Regex(
            "(Rs\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?transferred to your (.+?) on (\\d{1,2}/\\d{1,2}/\\d{4}) at (\\d{2}:\\d{2}:\\d{2}).*?Transaction ID (\\d+)",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (toWallet != null) {
            val amountStr = toWallet.groupValues[1]
            val wallet = toWallet.groupValues[2].trim()
            val dateStr = toWallet.groupValues[3].trim()
            val timeStr = toWallet.groupValues[4].trim()
            val tid = toWallet.groupValues[5].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis("$dateStr $timeStr", listOf("dd/MM/yyyy HH:mm:ss"), Locale.US)
                ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$tid|$amountStr|$dateStr $timeStr")
            return TransactionDraft(
                sourceShortCode = "8558",
                sourceNameHint = "JazzCash",
                sourceTypeHint = DataSourceType.WALLET,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_OUT,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = wallet,
                reference = tid,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.9,
            )
        }

        // "Rs 1,500.00 received from PAKISTAN MOBILE COMM, A/C: ... on 16/06/2026 at 15:57:08. TID: ... via IBFT"
        val received = Regex(
            "(Rs\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?received from (.+?),.*? on (\\d{1,2}/\\d{1,2}/\\d{4}) at (\\d{2}:\\d{2}:\\d{2}).*?TID:?\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (received != null) {
            val amountStr = received.groupValues[1]
            val from = received.groupValues[2].trim()
            val dateStr = received.groupValues[3].trim()
            val timeStr = received.groupValues[4].trim()
            val tid = received.groupValues[5].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis("$dateStr $timeStr", listOf("dd/MM/yyyy HH:mm:ss"), Locale.US)
                ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$tid|$amountStr|$dateStr $timeStr")
            return TransactionDraft(
                sourceShortCode = "8558",
                sourceNameHint = "JazzCash",
                sourceTypeHint = DataSourceType.WALLET,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_IN,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = from,
                reference = tid,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.9,
            )
        }

        // "Rs 7,900.00 received from Mohsin Enterprises, JazzCash Mobile Account: 0313****595. ... TID: 715509994306."
        val receivedToMobileAccount = Regex(
            "(Rs\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?received\\s+from\\s+(.+?),\\s*JazzCash\\s+Mobile\\s+Account:?\\s*([0-9*]+).*?TID:?\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (receivedToMobileAccount != null) {
            val amountStr = receivedToMobileAccount.groupValues[1]
            val from = receivedToMobileAccount.groupValues[2].trim()
            val accountMasked = receivedToMobileAccount.groupValues[3].trim()
            val tid = receivedToMobileAccount.groupValues[4].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = message.receivedAtMillis
            val externalId = ParseUtils.sha256Hex("$sender|$tid|$amountStr|$ts")
            return TransactionDraft(
                sourceShortCode = "8558",
                sourceNameHint = "JazzCash",
                sourceTypeHint = DataSourceType.WALLET,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_IN,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = from,
                cardLast4Hint = accountMasked.takeLast(4).takeIf { it.length == 4 && it.all(Char::isDigit) },
                reference = tid,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.9,
            )
        }

        // "Rs 4,700.00 sent to MUHAMMAD SHOAIB, A/C: ***2342 on 20/06/2026 at 21:41:55. Fee: Rs 0.00, TID:715335495436 via JazzCash"
        val sentToAccount = Regex(
            "(Rs\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?sent to (.+?),\\s*A/C:\\s*([*0-9]+)\\s*on (\\d{1,2}/\\d{1,2}/\\d{4}) at (\\d{2}:\\d{2}:\\d{2}).*?TID:?\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (sentToAccount != null) {
            val amountStr = sentToAccount.groupValues[1]
            val merchant = sentToAccount.groupValues[2].trim()
            val accountMasked = sentToAccount.groupValues[3].trim()
            val dateStr = sentToAccount.groupValues[4].trim()
            val timeStr = sentToAccount.groupValues[5].trim()
            val tid = sentToAccount.groupValues[6].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis("$dateStr $timeStr", listOf("dd/MM/yyyy HH:mm:ss"), Locale.US)
                ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$tid|$amountStr|$dateStr $timeStr")
            return TransactionDraft(
                sourceShortCode = "8558",
                sourceNameHint = "JazzCash",
                sourceTypeHint = DataSourceType.WALLET,
                timestampMillis = ts,
                type = TransactionType.TRANSFER_OUT,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = merchant,
                cardLast4Hint = accountMasked.takeLast(4).takeIf { it.length == 4 && it.all(Char::isDigit) },
                reference = tid,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.92,
            )
        }

        // "Rs 5,500.00 received in your JazzCash Mobile Account:03079770070 via Raast. TID: 715335861320"
        val receivedInWallet = Regex(
            "(Rs\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?received\\s+in your JazzCash Mobile Account:?\\s*([0-9]+).*?TID:?\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (receivedInWallet != null) {
            val amountStr = receivedInWallet.groupValues[1]
            val accountNo = receivedInWallet.groupValues[2].trim()
            val tid = receivedInWallet.groupValues[3].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val externalId = ParseUtils.sha256Hex("$sender|$tid|$amountStr|${message.receivedAtMillis}")
            return TransactionDraft(
                sourceShortCode = "8558",
                sourceNameHint = "JazzCash",
                sourceTypeHint = DataSourceType.WALLET,
                timestampMillis = message.receivedAtMillis,
                type = TransactionType.TRANSFER_IN,
                direction = TransactionDirection.IN,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = "Raast",
                reference = tid,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.9,
            )
        }

        // "Rs 257.00 paid to OVER DOSE, Till ID: ... on 11/06/2026 at 15:49:57. ... TID: ... via QR Code."
        val paidTo = Regex(
            "(Rs\\s*[0-9,]+(?:\\.[0-9]{1,2})?).*?paid to (.+?),.*? on (\\d{1,2}/\\d{1,2}/\\d{4}) at (\\d{2}:\\d{2}:\\d{2}).*?TID:?\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        ).find(body)
        if (paidTo != null) {
            val amountStr = paidTo.groupValues[1]
            val merchant = paidTo.groupValues[2].trim()
            val dateStr = paidTo.groupValues[3].trim()
            val timeStr = paidTo.groupValues[4].trim()
            val tid = paidTo.groupValues[5].trim()

            val (currency, minor) = ParseUtils.parseAmountMinor(amountStr) ?: return null
            val ts = ParseUtils.tryParseDateMillis("$dateStr $timeStr", listOf("dd/MM/yyyy HH:mm:ss"), Locale.US)
                ?: message.receivedAtMillis

            val externalId = ParseUtils.sha256Hex("$sender|$tid|$amountStr|$dateStr $timeStr")
            return TransactionDraft(
                sourceShortCode = "8558",
                sourceNameHint = "JazzCash",
                sourceTypeHint = DataSourceType.WALLET,
                timestampMillis = ts,
                type = TransactionType.DEBIT_PURCHASE,
                direction = TransactionDirection.OUT,
                amountMinor = kotlin.math.abs(minor),
                currency = currency,
                merchantRaw = merchant,
                reference = tid,
                rawMessage = body,
                externalId = externalId,
                confidence = 0.88,
            )
        }

        return null
    }

    private fun isJazzSender(sender: String): Boolean {
        val s = sender.trim().uppercase(Locale.US)
        return s == "8558" || s.contains("8558") || s.contains("JAZZ")
    }
}
