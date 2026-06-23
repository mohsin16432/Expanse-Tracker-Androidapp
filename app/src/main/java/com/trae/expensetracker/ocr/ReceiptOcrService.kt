package com.trae.expensetracker.ocr

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ReceiptOcrResult(
    val rawText: String,
    val merchantHint: String?,
    val merchantCandidates: List<String>,
    val amountMinorHint: Long?,
    val currencyHint: String?,
)

class ReceiptOcrService(
    private val appContext: Context,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scanReceipt(uri: Uri): Result<ReceiptOcrResult> = runCatching {
        val image = InputImage.fromFilePath(appContext, uri)
        buildResult(recognize(image))
    }

    suspend fun scanReceipt(bitmap: Bitmap): Result<ReceiptOcrResult> = runCatching {
        val image = InputImage.fromBitmap(bitmap, 0)
        buildResult(recognize(image))
    }

    private suspend fun recognize(image: InputImage): String {
        return suspendCancellableCoroutine<String> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resume(throw e)
                }
        }
    }

    private fun buildResult(text: String): ReceiptOcrResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val merchantCandidates = detectMerchantCandidates(lines)
        val merchant = merchantCandidates.firstOrNull()
        val amountMatch = detectAmount(lines)
        val currency = when {
            text.contains("PKR", ignoreCase = true) || text.contains("RS", ignoreCase = true) -> "PKR"
            text.contains("USD", ignoreCase = true) -> "USD"
            else -> null
        }

        return ReceiptOcrResult(
            rawText = text,
            merchantHint = merchant,
            merchantCandidates = merchantCandidates,
            amountMinorHint = amountMatch,
            currencyHint = currency,
        )
    }

    private fun detectMerchantCandidates(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val blacklist = listOf(
            "invoice", "receipt", "total", "subtotal", "discount", "tax", "ntn", "strn",
            "cashier", "cash", "payment", "credit", "debit", "change",
            "visa", "master", "bank", "ltd", "limited", "auth", "rrn", "tid", "mid", "batch"
        )
        val boosts = listOf("restaurant", "resturant", "cafe", "coffee", "hotel", "mart", "store", "shop")

        fun score(line: String, index: Int): Int {
            val t = line.trim()
            val lowered = t.lowercase()
            if (t.length !in 3..48) return Int.MIN_VALUE / 4
            if (!t.any { it.isLetter() }) return Int.MIN_VALUE / 4
            if (t.count { it.isDigit() } >= 3) return Int.MIN_VALUE / 4 // phone/address like lines
            if (blacklist.any { lowered.contains(it) }) return Int.MIN_VALUE / 4

            val letters = t.count { it.isLetter() }
            val upper = t.count { it.isUpperCase() }
            val upperRatio = if (letters == 0) 0.0 else upper.toDouble() / letters.toDouble()

            var s = 0
            // Prefer lines near the top, but not necessarily the first one (logos/bank lines exist).
            s += (30 - index).coerceAtLeast(0)
            // Prefer "name-like" lines.
            s += letters.coerceAtMost(18)
            // Many receipts have merchant in uppercase.
            if (upperRatio > 0.6) s += 10
            // Strong semantic hints.
            if (boosts.any { lowered.contains(it) }) s += 25
            // Penalize long addressy lines.
            if (t.contains(",") || "markaz" in lowered || "islamabad" in lowered || "phone" in lowered || "ph:" in lowered) s -= 8
            return s
        }

        return lines.take(15)
            .mapIndexed { idx, line -> line to score(line, idx) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(5)
    }

    private fun detectAmount(lines: List<String>): Long? {
        val amountRegex = Regex("""(?i)(?:PKR|RS\.?|RS)?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})|[0-9]+(?:\.[0-9]{1,2}))""")
        val preferred = lines.mapNotNull { line ->
            val lowered = line.lowercase()
            val score = when {
                "grand total" in lowered -> 5
                "total" in lowered -> 4
                "amount due" in lowered -> 3
                "net total" in lowered -> 3
                "subtotal" in lowered -> 2
                else -> 0
            }
            val value = amountRegex.findAll(line)
                .mapNotNull { it.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() }
                .maxOrNull()
            if (value != null) score to value else null
        }.sortedByDescending { it.first }

        val chosen = preferred.firstOrNull { it.first > 0 }?.second
            ?: lines.flatMap { line ->
                amountRegex.findAll(line).mapNotNull { it.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() }.toList()
            }.filter { it in 1.0..500000.0 }
                .maxOrNull()

        return chosen?.let { (it * 100).toLong() }
    }
}
