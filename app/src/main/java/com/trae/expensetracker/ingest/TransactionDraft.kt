package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDraft(
    val sourceShortCode: String?,
    val sourceNameHint: String?,
    val sourceTypeHint: DataSourceType?,
    val cardLast4Hint: String? = null,

    val timestampMillis: Long,
    val type: TransactionType,
    val direction: TransactionDirection,

    val amountMinor: Long,
    val currency: String,

    val merchantRaw: String,
    val reference: String?,

    val rawMessage: String,
    val externalId: String,
    val confidence: Double,
)
