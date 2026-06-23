package com.trae.expensetracker.ingest.parsers

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

object ParseUtils {
    fun normalizeMerchant(input: String): String =
        input.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Parses money like "PKR 7,000.00" / "Rs 1,500.00" / "PKR-4,124.28". */
    fun parseAmountMinor(raw: String): Pair<String, Long>? {
        val s = raw.trim()
            .replace(",", "")
            .replace("PKR-", "PKR -")
            .replace("Rs.", "Rs")
            .replace("RS.", "RS")

        val currency = when {
            s.uppercase(Locale.US).contains("PKR") -> "PKR"
            s.uppercase(Locale.US).contains("RS") -> "PKR"
            s.uppercase(Locale.US).contains("USD") -> "USD"
            else -> null
        } ?: return null

        // Extract first signed decimal number.
        val m = Regex("(-?\\d+(?:\\.\\d+)?)").find(s) ?: return null
        val num = m.groupValues[1]
        val parts = num.split(".")
        val major = parts[0].toLongOrNull() ?: return null
        val minor2 = when (parts.getOrNull(1)?.length ?: 0) {
            0 -> 0
            1 -> (parts[1].toIntOrNull() ?: 0) * 10
            else -> (parts[1].take(2).toIntOrNull() ?: 0)
        }
        val sign = if (major < 0 || num.startsWith("-")) -1 else 1
        val majorAbs = kotlin.math.abs(major)
        val total = (majorAbs * 100L + minor2) * sign
        return currency to total
    }

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun tryParseDateMillis(
        text: String,
        patterns: List<String>,
        locale: Locale = Locale.US,
    ): Long? {
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, locale)
                sdf.isLenient = true
                val d = sdf.parse(text) ?: continue
                return d.time
            } catch (_: Throwable) {
                // ignore
            }
        }
        return null
    }
}

