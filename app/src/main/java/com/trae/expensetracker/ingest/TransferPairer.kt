package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.data.repo.TransactionRepository
import com.trae.expensetracker.ingest.parsers.ParseUtils

/**
 * Detects internal transfers between two of the user's own accounts.
 *
 * Why this matters: when you move money HBL -> Meezan over Raast you receive two SMS messages,
 * one TRANSFER_OUT from HBL and one TRANSFER_IN to Meezan. Storing both as-is inflates both
 * "spent" and "income" by the same amount. Pairing them and excluding the group from totals
 * keeps the dashboard honest.
 *
 * Pairing rules (all must hold):
 *  - opposite directions (one OUT, one IN)
 *  - identical amount and currency
 *  - different sources (a transfer between two of your own accounts, not a self-post)
 *  - timestamps within [MAX_SKEW_MILLIS]
 *  - neither leg already belongs to another transfer group
 *  - both legs are transfer-typed (never purchases / bill payments / card activity)
 */
class TransferPairer(
    private val transactionRepository: TransactionRepository,
) {
    companion object {
        /** Bank SMS for the two legs typically arrive seconds apart; allow a generous window. */
        const val MAX_SKEW_MILLIS: Long = 10 * 60 * 1000L
    }

    /**
     * Attempts to pair [candidate] with an existing opposite-direction leg.
     *
     * @return the group id when a pair was formed, otherwise null.
     */
    suspend fun tryPair(candidate: TransactionEntity): String? {
        val partner = findPartner(candidate) ?: return null

        val groupId = ParseUtils.sha256Hex(
            listOf(
                "transfer",
                candidate.amountMinor.toString(),
                candidate.currency,
                minOf(candidate.timestampMillis, partner.timestampMillis).toString(),
                listOf(candidate.sourceId, partner.sourceId).sorted().joinToString(","),
            ).joinToString("|")
        ).take(32)

        transactionRepository.setTransferGroup(candidate.id, groupId)
        transactionRepository.setTransferGroup(partner.id, groupId)
        return groupId
    }

    /** Finds the opposite leg for a candidate without persisting anything (used for previews/tests). */
    suspend fun findPartner(candidate: TransactionEntity): TransactionEntity? {
        if (candidate.transferGroupId != null) return null
        if (!isTransferLike(candidate)) return null

        val opposite = if (candidate.direction == TransactionDirection.OUT) {
            TransactionDirection.IN
        } else {
            TransactionDirection.OUT
        }

        return transactionRepository.findCandidates(
            direction = opposite,
            amountMinor = candidate.amountMinor,
            currency = candidate.currency,
            fromMillis = candidate.timestampMillis - MAX_SKEW_MILLIS,
            toMillis = candidate.timestampMillis + MAX_SKEW_MILLIS,
            anchorMillis = candidate.timestampMillis,
        ).firstOrNull { other ->
            other.id != candidate.id &&
                other.transferGroupId == null &&
                other.sourceId != candidate.sourceId &&
                isTransferLike(other)
        }
    }

    /**
     * Only genuine account-to-account movement qualifies. Purchases, bill payments and card
     * activity must never be paired, even when amounts coincidentally match.
     */
    private fun isTransferLike(tx: TransactionEntity): Boolean =
        tx.type == TransactionType.TRANSFER_IN || tx.type == TransactionType.TRANSFER_OUT
}
