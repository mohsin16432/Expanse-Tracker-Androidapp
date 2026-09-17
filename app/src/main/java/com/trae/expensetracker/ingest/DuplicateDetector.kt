package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.data.repo.TransactionRepository

/**
 * Flags likely duplicates that an exact externalId match would not catch.
 *
 * The common case: the same payment arrives once as a bank SMS and once as a wallet push
 * notification. Those have different senders, so their dedup hashes differ, yet they describe
 * the same money movement.
 */
class DuplicateDetector(
    private val transactionRepository: TransactionRepository,
    private val merchantRuleRepository: com.trae.expensetracker.data.repo.MerchantRuleRepository,
) {
    companion object {
        /** Two captures of one payment normally land within a couple of minutes. */
        const val MAX_SKEW_MILLIS: Long = 3 * 60 * 1000L

        /** Refunds are matched against charges from further back (up to 45 days). */
        const val REFUND_LOOKBACK_MILLIS: Long = 45L * 24 * 60 * 60 * 1000
    }

    /**
     * @return the id of the original transaction when [candidate] looks like a duplicate.
     */
    suspend fun findOriginal(candidate: TransactionEntity): String? {
        val candidates = transactionRepository.findDuplicateCandidates(
            amountMinor = candidate.amountMinor,
            direction = candidate.direction,
            currency = candidate.currency,
            fromMillis = candidate.timestampMillis - MAX_SKEW_MILLIS,
            toMillis = candidate.timestampMillis + MAX_SKEW_MILLIS,
            anchorMillis = candidate.timestampMillis,
            excludeId = candidate.id,
        )

        val usable = candidates
            .filter { it.id != candidate.id }
            .filter { it.duplicateOfId == null }
            .filter { !isSuppressed(candidate, it) }
            .firstOrNull { isSamePayment(candidate, it) }
            ?: return null
        return usable.id
    }

    /**
     * Detects a refund/reversal: an incoming credit that mirrors an earlier outgoing charge.
     *
     * @return the id of the original charge when [candidate] looks like its refund.
     */
    suspend fun findRefundedCharge(candidate: TransactionEntity): String? {
        if (candidate.direction != TransactionDirection.IN) return null
        if (candidate.type == TransactionType.TRANSFER_IN) return null
        if (candidate.refundOfId != null) return null

        val charge = transactionRepository.findRefundCandidate(
            amountMinor = candidate.amountMinor,
            currency = candidate.currency,
            fromMillis = candidate.timestampMillis - REFUND_LOOKBACK_MILLIS,
            toMillis = candidate.timestampMillis,
            anchorMillis = candidate.timestampMillis,
            excludeId = candidate.id,
        ) ?: return null

        // Require a recognisable merchant match so an unrelated incoming credit of the same
        // amount is not mistaken for a refund.
        return charge.id.takeIf { isSamePayment(candidate, charge) }
    }

    /** Honours a user's earlier "not a duplicate" decision for this pair of sources. */
    private suspend fun isSuppressed(a: TransactionEntity, b: TransactionEntity): Boolean {
        if (a.sourceId == b.sourceId) return false
        val rules = runCatching { merchantRuleRepository.getAll() }.getOrDefault(emptyList())
        return merchantRuleRepository.isDuplicateSuppressed(rules, a.sourceId, b.sourceId)
    }

    /**
     * Two rows are the same payment when the amounts, direction and currency already match
     * (enforced by the query) and the merchant names are recognisably the same party.
     * A blank or generic merchant never triggers a duplicate flag.
     */
    private fun isSamePayment(a: TransactionEntity, b: TransactionEntity): Boolean {
        val left = normalizeParty(a.merchantNormalized)
        val right = normalizeParty(b.merchantNormalized)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true
        if (left.length >= 4 && right.contains(left)) return true
        if (right.length >= 4 && left.contains(right)) return true
        return false
    }

    private fun normalizeParty(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
