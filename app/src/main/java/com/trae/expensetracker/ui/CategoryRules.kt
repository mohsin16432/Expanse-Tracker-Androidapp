package com.trae.expensetracker.ui

import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType

object CategoryRules {
    fun detect(tx: TransactionEntity): String {
        tx.categoryId?.takeIf { it.isNotBlank() }?.let { return it }
        val m = tx.merchantNormalized.lowercase()
        return when {
            tx.type == TransactionType.TRANSFER_IN || tx.type == TransactionType.CREDIT_RECEIVED -> "Income"
            tx.type == TransactionType.TRANSFER_OUT -> "Transfer"
            "daraz" in m || "cash and carry" in m || "mart" in m || "hypermarket" in m || "save mart" in m -> "Shopping"
            "food panda" in m || "cafe" in m || "chilli" in m || "dose" in m -> "Food"
            "netflix" in m || "google one" in m || "nayatel" in m || "water" in m -> "Bills"
            "medicine" in m || "clinic" in m || "oladoc" in m -> "Health"
            else -> "Other"
        }
    }
}

