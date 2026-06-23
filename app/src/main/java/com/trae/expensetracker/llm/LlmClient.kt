package com.trae.expensetracker.llm

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft

data class LlmSmsResult(
    val isTransactional: Boolean,
    val draft: TransactionDraft?,
    val rawModelOutput: String,
)

data class SmsRegexGenerationResult(
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
)

interface LlmClient {
    suspend fun classifyAndParseSms(message: SmsMessage): LlmSmsResult
    suspend fun generateSmsRegexSpec(message: SmsMessage): Result<SmsRegexGenerationResult>
    suspend fun testConnection(): Result<String>
}
