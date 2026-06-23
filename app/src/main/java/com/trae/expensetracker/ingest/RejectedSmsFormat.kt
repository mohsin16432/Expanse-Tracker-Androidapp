package com.trae.expensetracker.ingest

import kotlinx.serialization.Serializable

@Serializable
data class RejectedSmsFormat(
    val sender: String,
    val templateRegex: String,
) {
    fun matches(message: SmsMessage): Boolean {
        if (!sender.equals(message.sender.trim(), ignoreCase = true)) return false
        return runCatching { Regex(templateRegex, RegexOption.IGNORE_CASE).matches(message.body.trim()) }
            .getOrDefault(false)
    }

    companion object {
        fun fromInvalidSample(sender: String, rawText: String): RejectedSmsFormat {
            return RejectedSmsFormat(
                sender = sender.trim(),
                templateRegex = buildTemplateRegex(rawText.trim()),
            )
        }

        private fun buildTemplateRegex(rawText: String): String {
            var template = rawText

            template = template.replace(
                Regex("""(?i)\b(?:PKR|RS\.?|RS)?\s*-?[0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?\b"""),
                "__AMOUNT__"
            )
            template = template.replace(
                Regex("""\b\d{1,2}[-/]\d{1,2}[-/]\d{2,4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?)?\b""", RegexOption.IGNORE_CASE),
                "__DATE__"
            )
            template = template.replace(
                Regex("""\b\d{1,2}[-/][A-Za-z]{3}[-/]\d{2,4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?)?\b""", RegexOption.IGNORE_CASE),
                "__DATE__"
            )
            template = template.replace(
                Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?\b""", RegexOption.IGNORE_CASE),
                "__TIME__"
            )
            template = template.replace(
                Regex("""\b[xX*]{2,}[0-9]{2,6}\b"""),
                "__MASK__"
            )
            template = template.replace(
                Regex("""\b\d{0,4}\*+\d{2,6}\b"""),
                "__MASK__"
            )
            template = template.replace(
                Regex("""\b\d{4,}\b"""),
                "__ID__"
            )

            return "^" + Regex.escape(template)
                .replace("__AMOUNT__", "((?:PKR|RS\\.?|RS)?\\s*-?[0-9,]+(?:\\.[0-9]{1,2})?)")
                .replace("__DATE__", "([A-Za-z0-9:/\\-\\s]+?)")
                .replace("__TIME__", "([0-9:AMPMapm\\s]+)")
                .replace("__MASK__", "([xX*0-9]{4,})")
                .replace("__ID__", "([0-9]{4,})")
                .replace("\\ ", "\\s+")
        }
    }
}

