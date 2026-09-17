package com.trae.expensetracker.ingest

object SmsClassifier {
    private val otpKeywords = listOf(
        " otp ",
        "one time password",
        "do not share otp",
        "valid for",
        "confirm your payment using the otp",
        "do not share",
        "verification code",
        "is your otp",
    )

    private val promoKeywords = listOf(
        "discount",
        "offer",
        "sale",
        "promo",
        "loan",
        "limited time",
    )

    private val withdrawalKeywords = listOf(
        "atm cash withdrawal",
        "cash withdrawal",
        "atm withdrawal",
    )

    private val financialKeywords = listOf(
        "credited",
        "debited",
        "charged",
        "purchase",
        "transaction",
        "payment",
        "transferred",
        "transfer",
        "sent",
        "paid",
        "received",
        "spent",
        "card",
        "account",
        "wallet",
    )

    fun isOtpOrPromo(body: String): Boolean {
        val s = " ${body.lowercase()} "
        return otpKeywords.any { s.contains(it) } || promoKeywords.any { s.contains(it) }
    }

    /**
     * Requirement: withdrawal messages must NOT be parsed, stored, or shown anywhere.
     */
    fun isWithdrawal(body: String): Boolean {
        val s = body.lowercase()
        return withdrawalKeywords.any { s.contains(it) }
    }

    fun looksLikeFinancialTransaction(body: String): Boolean {
        val s = body.lowercase()
        val hasMoney =
            s.contains("pkr") ||
                s.contains("rs.") ||
                s.contains("rs ") ||
                s.contains(" rs ") ||
                Regex("""\b[0-9,]+(?:\.[0-9]{1,2})?\b""").containsMatchIn(s)
        val hasFinancialVerb = financialKeywords.any { s.contains(it) }
        return hasMoney && hasFinancialVerb && !isOtpOrPromo(body) && !isWithdrawal(body)
    }
}
