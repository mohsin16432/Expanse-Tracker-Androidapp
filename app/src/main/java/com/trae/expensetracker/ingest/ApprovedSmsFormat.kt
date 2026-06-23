package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import kotlinx.serialization.Serializable

@Serializable
data class ApprovedSmsFormat(
    val sender: String,
    val templateRegex: String,
    val sourceShortCode: String?,
    val sourceTypeHint: DataSourceType?,
    val cardLast4Hint: String?,
    val type: TransactionType,
    val direction: TransactionDirection,
) {
    fun matches(message: SmsMessage): Boolean {
        if (!sender.equals(message.sender.trim(), ignoreCase = true)) return false
        return runCatching { Regex(templateRegex, RegexOption.IGNORE_CASE).matches(message.body.trim()) }.getOrDefault(false)
    }

    companion object {
        fun fromApprovedDraft(
            sender: String,
            rawText: String,
            draft: TransactionDraft,
        ): ApprovedSmsFormat {
            return ApprovedSmsFormat(
                sender = sender.trim(),
                templateRegex = buildTemplateRegex(rawText.trim(), draft),
                sourceShortCode = draft.sourceShortCode,
                sourceTypeHint = draft.sourceTypeHint,
                cardLast4Hint = draft.cardLast4Hint,
                type = draft.type,
                direction = draft.direction,
            )
        }

        private fun buildTemplateRegex(
            rawText: String,
            draft: TransactionDraft,
        ): String {
            var template = rawText

            template = replaceFirstIgnoreCase(template, draft.merchantRaw.trim(), "__MERCHANT__")
            template = template.replace(Regex("""(?i)\b(?:PKR|RS\.?|RS)?\s*-?[0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?\b"""), "__AMOUNT__")
            template = template.replace(Regex("""\b\d{1,2}[-/][A-Za-z0-9]{1,3}[-/]\d{2,4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?)?\b""", RegexOption.IGNORE_CASE), "__DATE__")
            template = template.replace(Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?\b""", RegexOption.IGNORE_CASE), "__TIME__")
            template = template.replace(Regex("""\b\d{4,}\*+\d{2,4}\b"""), "__CARD__")

            return "^" + Regex.escape(template)
                .replace("__MERCHANT__", "(.+?)")
                .replace("__AMOUNT__", "((?:PKR|RS\\.?|RS)?\\s*-?[0-9,]+(?:\\.[0-9]{1,2})?)")
                .replace("__DATE__", "([A-Za-z0-9:/\\-\\s]+?)")
                .replace("__TIME__", "([0-9:AMPMapm\\s]+)")
                .replace("__CARD__", "([0-9*]+)")
                .replace("\\ ", "\\s+")
        }

        private fun replaceFirstIgnoreCase(source: String, needle: String, replacement: String): String {
            if (needle.isBlank()) return source
            val index = source.indexOf(needle, ignoreCase = true)
            if (index < 0) return source
            return source.replaceRange(index, index + needle.length, replacement)
        }
    }
}
