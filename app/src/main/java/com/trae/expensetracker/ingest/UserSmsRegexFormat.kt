package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.parsers.ParseUtils
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * User-defined SMS format that allows local parsing without shipping a new app build.
 *
 * This is intentionally "low-level": user supplies a regex + capture-group indices.
 * Capture groups are 1-based, same as standard regex groups.
 */
@Serializable
data class UserSmsRegexFormat(
    val sender: String,
    val sourceShortCode: String,
    val sourceNameHint: String,
    val sourceTypeHint: DataSourceType,
    val type: TransactionType,
    val direction: TransactionDirection,
    val regex: String,
    val amountGroup: Int,
    val merchantGroup: Int? = null,
    val dateGroup: Int? = null,
    val timeGroup: Int? = null,
    val datePattern: String? = null,
    val timePattern: String? = null,
    val referenceGroup: Int? = null,
    val currencyOverride: String? = null,
) {
    fun senderMatches(actualSender: String): Boolean {
        val s1 = sender.trim()
        val s2 = actualSender.trim()
        if (s1.isBlank() || s2.isBlank()) return false
        return s1.equals(s2, ignoreCase = true) || s2.contains(s1, ignoreCase = true)
    }
}

object UserSmsRegexParser {
    fun parse(
        format: UserSmsRegexFormat,
        message: SmsMessage,
    ): TransactionDraft? {
        if (!format.senderMatches(message.sender)) return null

        val regex = runCatching {
            Regex(format.regex, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        }.getOrNull() ?: return null

        val match = regex.find(message.body.trim()) ?: return null

        fun groupOrNull(index: Int?): String? {
            if (index == null || index <= 0) return null
            return match.groupValues.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
        }

        val amountRaw = groupOrNull(format.amountGroup) ?: return null
        val parsedAmount = ParseUtils.parseAmountMinor(amountRaw) ?: return null
        val currency = format.currencyOverride?.trim()?.takeIf { it.isNotBlank() } ?: parsedAmount.first
        val minor = kotlin.math.abs(parsedAmount.second)

        val merchant = groupOrNull(format.merchantGroup) ?: format.sourceNameHint.ifBlank { "Transaction" }
        val ref = groupOrNull(format.referenceGroup)

        val ts = run {
            val dg = groupOrNull(format.dateGroup)
            val tg = groupOrNull(format.timeGroup)
            val dp = format.datePattern?.trim().orEmpty()
            val tp = format.timePattern?.trim().orEmpty()
            if (!dg.isNullOrBlank() && !tg.isNullOrBlank() && dp.isNotBlank() && tp.isNotBlank()) {
                ParseUtils.tryParseDateMillis("$dg $tg", listOf("$dp $tp"), Locale.US) ?: message.receivedAtMillis
            } else {
                message.receivedAtMillis
            }
        }

        val externalId = ParseUtils.sha256Hex(
            "usr|${format.sourceShortCode}|${message.sender.trim()}|${format.regex}|$amountRaw|$ts|${ref.orEmpty()}"
        )

        return TransactionDraft(
            sourceShortCode = format.sourceShortCode.trim(),
            sourceNameHint = format.sourceNameHint.trim(),
            sourceTypeHint = format.sourceTypeHint,
            timestampMillis = ts,
            type = format.type,
            direction = format.direction,
            amountMinor = minor,
            currency = currency,
            merchantRaw = merchant,
            reference = ref,
            rawMessage = message.body.trim(),
            externalId = externalId,
            confidence = 0.86,
        )
    }
}
