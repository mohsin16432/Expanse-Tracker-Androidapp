package com.trae.expensetracker.ingest

import android.content.Context
import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.PendingImportEntity
import com.trae.expensetracker.data.repo.CategoryRepository
import com.trae.expensetracker.data.repo.DataSourceRepository
import com.trae.expensetracker.data.repo.PendingImportRepository
import com.trae.expensetracker.data.repo.SettingsRepository
import com.trae.expensetracker.data.repo.TransactionRepository
import com.trae.expensetracker.ingest.parsers.FblParser
import com.trae.expensetracker.ingest.parsers.HblParser
import com.trae.expensetracker.ingest.parsers.JazzCashParser
import com.trae.expensetracker.ingest.parsers.MeezanParser
import com.trae.expensetracker.ingest.parsers.ParseUtils
import com.trae.expensetracker.llm.LlmClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SmsIngestor(
    private val appContext: Context,
    private val transactionRepository: TransactionRepository,
    private val pendingImportRepository: PendingImportRepository,
    private val dataSourceRepository: DataSourceRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val llmClient: LlmClient,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private var cachedUserFormats: List<UserSmsRegexFormat> = emptyList()
    private var lastUserFormatsLoadMillis: Long = 0L
    private var cachedRejectedFormats: List<RejectedSmsFormat> = emptyList()
    private var lastRejectedLoadMillis: Long = 0L
    private val lastLlmCallMillisBySender = mutableMapOf<String, Long>()

    private val parsers = listOf(
        HblParser(),
        FblParser(),
        JazzCashParser(),
        MeezanParser(),
    )

    suspend fun ingest(message: SmsMessage, allowLlmFallback: Boolean = true) {
        val body = message.body.trim()
        if (body.isBlank()) return

        // HARD RULE: withdrawals are excluded entirely.
        if (SmsClassifier.isWithdrawal(body)) return
        if (SmsClassifier.isOtpOrPromo(body)) return

        // Reject known invalid/non-transactional formats early (avoid LLM calls).
        if (matchesRejected(message)) return

        // 0) User-defined regex formats (local parsing)
        val userDraft = parseWithUserFormats(message)
        if (userDraft != null) {
            val resolvedSourceId = resolveSourceId(userDraft) ?: return
            transactionRepository.insertIgnore(draftToEntity(userDraft, resolvedSourceId))
            return
        }

        // 1) Try bank-specific parsers
        val parsed = parsers.firstNotNullOfOrNull { it.parse(message) }
        if (parsed != null) {
            val resolvedSourceId = resolveSourceId(parsed) ?: return
            val tx = draftToEntity(parsed, resolvedSourceId)
            transactionRepository.insertIgnore(tx)
            return
        }

        // 2) LLM fallback for unknown format / new bank messages
        // Disabled for bulk import to keep refresh fast.
        if (!allowLlmFallback) return

        // Per-sender throttle to control LLM cost/rate-limit.
        val now = System.currentTimeMillis()
        val senderKey = message.sender.trim()
        val last = lastLlmCallMillisBySender[senderKey] ?: 0L
        if (now - last < 60_000) return
        lastLlmCallMillisBySender[senderKey] = now

        val llm = llmClient.classifyAndParseSms(message)
        if (!llm.isTransactional || llm.draft == null) {
            // Ignore generic/non-transactional noise. Review is only for plausible transaction drafts.
            if (SmsClassifier.looksLikeFinancialTransaction(body)) {
                settingsRepository.addRejectedSmsFormat(
                    RejectedSmsFormat.fromInvalidSample(senderKey, body)
                )
                invalidateRejectedFormatsCache()
            }
            return
        }

        val draft = llm.draft
        val approvedFormats = settingsRepository.getApprovedSmsFormats()
        val matchesApprovedFormat = approvedFormats.any { it.matches(message) }
        if (matchesApprovedFormat && SmsClassifier.looksLikeFinancialTransaction(body)) {
            val resolvedSourceId = resolveSourceId(draft)
            if (resolvedSourceId != null) {
                transactionRepository.insertIgnore(draftToEntity(draft, resolvedSourceId))
                return
            }
        }

        val pendingId = ParseUtils.sha256Hex("pending|${message.sender}|${message.body}|${message.receivedAtMillis}")
        pendingImportRepository.upsert(
            PendingImportEntity(
                id = pendingId,
                createdAtMillis = System.currentTimeMillis(),
                kind = "sms",
                sender = message.sender,
                rawText = message.body,
                draftTransactionJson = json.encodeToString(draft),
            )
        )
    }

    fun invalidateUserFormatsCache() {
        lastUserFormatsLoadMillis = 0L
    }

    private fun invalidateRejectedFormatsCache() {
        lastRejectedLoadMillis = 0L
    }

    private suspend fun upsertSourceIfMissing(draft: TransactionDraft) {
        val shortCode = draft.sourceShortCode?.trim().orEmpty()
        if (shortCode.isBlank()) return
        val type = draft.sourceTypeHint ?: DataSourceType.BANK
        val existing = dataSourceRepository.getAll().firstOrNull { it.shortCode == shortCode && it.type == type }
        if (existing != null) return
        val name = draft.sourceNameHint ?: "Source $shortCode"
        val id = when (type) {
            DataSourceType.CASH -> shortCode
            DataSourceType.BANK -> "$shortCode-BANK"
            DataSourceType.WALLET -> "$shortCode-WALLET"
            DataSourceType.CREDIT_CARD -> "$shortCode-CC-${draft.cardLast4Hint.orEmpty()}"
        }
        dataSourceRepository.upsert(
            DataSourceEntity(
                id = id,
                name = name,
                shortCode = shortCode,
                type = type,
                cardLast4 = draft.cardLast4Hint?.takeIf { type == DataSourceType.CREDIT_CARD }?.ifBlank { null },
                enabled = true,
            )
        )
    }

    private suspend fun resolveSourceId(draft: TransactionDraft): String? {
        val senderShortCode = draft.sourceShortCode?.trim().orEmpty()
        if (senderShortCode.isBlank()) return null

        val type = draft.sourceTypeHint ?: DataSourceType.BANK
        var all = dataSourceRepository.getAll()
        var candidates = all.filter { it.shortCode == senderShortCode && it.type == type }
        if (candidates.isEmpty() && shouldAutoCreateSource(draft)) {
            upsertSourceIfMissing(draft)
            all = dataSourceRepository.getAll()
            candidates = all.filter { it.shortCode == senderShortCode && it.type == type }
        }
        if (candidates.isEmpty()) return null

        return when (type) {
            DataSourceType.CREDIT_CARD -> {
                val last4 = draft.cardLast4Hint?.trim().orEmpty()
                candidates.firstOrNull { it.cardLast4 == last4 }?.id
                    ?: all.firstOrNull { it.id == "$senderShortCode-CC-$last4" }?.id
            }
            else -> {
                candidates.firstOrNull()?.id
                    ?: all.firstOrNull { it.id == senderShortCode }?.id
                    ?: all.firstOrNull { it.id == "$senderShortCode-${type.name}" }?.id
            }
        }
    }

    private fun shouldAutoCreateSource(draft: TransactionDraft): Boolean {
        val shortCode = draft.sourceShortCode?.trim().orEmpty()
        if (shortCode.isBlank()) return false

        val type = draft.sourceTypeHint ?: DataSourceType.BANK
        return when (type) {
            DataSourceType.CREDIT_CARD -> !draft.cardLast4Hint.isNullOrBlank()
            else -> true
        }
    }

    private suspend fun parseWithUserFormats(message: SmsMessage): TransactionDraft? {
        // Load at most once per minute.
        val now = System.currentTimeMillis()
        if (now - lastUserFormatsLoadMillis > 60_000) {
            cachedUserFormats = settingsRepository.getUserSmsRegexFormats()
            lastUserFormatsLoadMillis = now
        }
        if (cachedUserFormats.isEmpty()) return null

        for (fmt in cachedUserFormats) {
            UserSmsRegexParser.parse(fmt, message)?.let { return it }
        }
        return null
    }

    private suspend fun matchesRejected(message: SmsMessage): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRejectedLoadMillis > 60_000) {
            cachedRejectedFormats = settingsRepository.getRejectedSmsFormats()
            lastRejectedLoadMillis = now
        }
        if (cachedRejectedFormats.isEmpty()) return false
        return cachedRejectedFormats.any { it.matches(message) }
    }


    private fun draftToEntity(
        draft: TransactionDraft,
        resolvedSourceId: String,
    ): com.trae.expensetracker.data.model.TransactionEntity {
        val merchantNormalized = ParseUtils.normalizeMerchant(draft.merchantRaw)
        return com.trae.expensetracker.data.model.TransactionEntity(
            id = draft.externalId,
            timestampMillis = draft.timestampMillis,
            sourceId = resolvedSourceId,
            type = draft.type,
            direction = draft.direction,
            amountMinor = draft.amountMinor,
            currency = draft.currency,
            merchantRaw = draft.merchantRaw,
            merchantNormalized = merchantNormalized.lowercase(),
            categoryId = null,
            reference = draft.reference,
            externalId = draft.externalId,
            rawMessage = draft.rawMessage,
        )
    }
}
