package com.trae.expensetracker.ingest

import android.content.Context
import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.PendingImportEntity
import com.trae.expensetracker.data.repo.CategoryRepository
import com.trae.expensetracker.data.repo.DataSourceRepository
import com.trae.expensetracker.data.repo.IgnoredImportRepository
import com.trae.expensetracker.data.repo.MerchantRuleRepository
import com.trae.expensetracker.data.repo.PendingImportRepository
import com.trae.expensetracker.data.repo.SettingsRepository
import com.trae.expensetracker.data.repo.TransactionRepository
import com.trae.expensetracker.ingest.parsers.CreditCardChargeParser
import com.trae.expensetracker.ingest.parsers.FblParser
import com.trae.expensetracker.ingest.parsers.GenericBankParser
import com.trae.expensetracker.ingest.parsers.HblParser
import com.trae.expensetracker.ingest.parsers.JazzCashParser
import com.trae.expensetracker.ingest.parsers.MeezanParser
import com.trae.expensetracker.ingest.parsers.ParseUtils
import com.trae.expensetracker.ingest.parsers.WalletNotificationParser
import com.trae.expensetracker.llm.LlmClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class IngestResult(
    val status: String,
    val detail: String? = null,
    val sourceId: String? = null,
)

class SmsIngestor(
    private val appContext: Context,
    private val transactionRepository: TransactionRepository,
    private val pendingImportRepository: PendingImportRepository,
    private val dataSourceRepository: DataSourceRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val ignoredImportRepository: IgnoredImportRepository,
    private val llmClient: LlmClient,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private var cachedUserFormats: List<UserSmsRegexFormat> = emptyList()
    private var lastUserFormatsLoadMillis: Long = 0L
    private var cachedRejectedFormats: List<RejectedSmsFormat> = emptyList()
    private var lastRejectedLoadMillis: Long = 0L
    private val lastLlmCallMillisBySender = mutableMapOf<String, Long>()

    private val transferPairer = TransferPairer(transactionRepository)
    private val duplicateDetector = DuplicateDetector(transactionRepository, merchantRuleRepository)
    private val merchantRuleEngine = MerchantRuleEngine(merchantRuleRepository)

    private val parsers = listOf(
        HblParser(),
        CreditCardChargeParser(),
        FblParser(),
        GenericBankParser,
        JazzCashParser(),
        MeezanParser(),
        WalletNotificationParser(),
    )

    suspend fun ingest(message: SmsMessage, allowLlmFallback: Boolean = true): IngestResult {
        val body = message.body.trim()
        if (body.isBlank()) return IngestResult("ignored_empty")

        // The user deleted this message in a previous run; do not bring it back.
        if (ignoredImportRepository.isIgnored(body)) return IngestResult("ignored_user_deleted")

        // HARD RULE: withdrawals are excluded entirely.
        if (SmsClassifier.isWithdrawal(body)) return IngestResult("ignored_withdrawal")
        if (SmsClassifier.isOtpOrPromo(body)) return IngestResult("ignored_otp_promo")

        // 0) User-defined regex formats (local parsing)
        val userDraft = parseWithUserFormats(message)
        if (userDraft != null) {
            val resolvedSourceId = resolveSourceId(userDraft) ?: return IngestResult("ignored_source_missing", "user_format")
            return persistDraft(userDraft, resolvedSourceId, parserPath = "user_format")
                .copy(sourceId = resolvedSourceId)
        }

        // 1) Try bank-specific parsers
        val parsed = parsers.firstNotNullOfOrNull { it.parse(message) }
        if (parsed != null) {
            val resolvedSourceId = resolveSourceId(parsed) ?: return IngestResult("ignored_source_missing", "builtin_parser")
            return persistDraft(parsed, resolvedSourceId, parserPath = "builtin")
                .copy(sourceId = resolvedSourceId)
        }

        // Reject cached invalid formats only before LLM fallback. Local parsers run first
        // so an old failed-learning attempt cannot block a now-supported transaction.
        if (matchesRejected(message)) return IngestResult("rejected_cached")

        // 2) LLM fallback for unknown format / new bank messages
        // Disabled for bulk import to keep refresh fast.
        if (!allowLlmFallback) return IngestResult("ignored_llm_disabled")

        // Per-sender throttle to control LLM cost/rate-limit.
        val now = System.currentTimeMillis()
        val senderKey = message.sender.trim()
        val last = lastLlmCallMillisBySender[senderKey] ?: 0L
        if (now - last < 60_000) return IngestResult("ignored_llm_throttled")
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
            return IngestResult("rejected_llm_non_transactional")
        }

        val draft = llm.draft
        val approvedFormats = settingsRepository.getApprovedSmsFormats()
        val matchesApprovedFormat = approvedFormats.any { it.matches(message) }
        if (matchesApprovedFormat && SmsClassifier.looksLikeFinancialTransaction(body)) {
            val resolvedSourceId = resolveSourceId(draft)
            if (resolvedSourceId != null) {
                return persistDraft(draft, resolvedSourceId, parserPath = "approved_format")
                    .copy(sourceId = resolvedSourceId)
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
        return IngestResult("pending_review")
    }

    fun invalidateMerchantRuleCache() {
        merchantRuleEngine.invalidateCache()
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
            DataSourceType.NOTIFICATION -> "$shortCode-NOTIF"
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
                when {
                    last4.isNotBlank() -> {
                        candidates.firstOrNull { it.cardLast4 == last4 }?.id
                            ?: all.firstOrNull { it.id == "$senderShortCode-CC-$last4" }?.id
                    }
                    candidates.size == 1 -> candidates.first().id
                    candidates.count { it.cardLast4.isNullOrBlank() } == 1 -> candidates.first { it.cardLast4.isNullOrBlank() }.id
                    else -> null
                }
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


    /**
     * Single funnel for persisting a parsed draft.
     *
     * Order matters:
     *  1. learned merchant rules (alias + category)
     *  2. duplicate detection against earlier captures of the same payment
     *  3. internal-transfer pairing against the opposite leg
     */
    private suspend fun persistDraft(
        draft: TransactionDraft,
        resolvedSourceId: String,
        parserPath: String,
    ): IngestResult {
        val entity = draftToEntity(draft, resolvedSourceId)

        val applied = merchantRuleEngine.apply(entity.merchantRaw, entity.categoryId)
        val merchantRaw = applied.merchantRaw
        val normalizedMerchant = ParseUtils.normalizeMerchant(merchantRaw).lowercase()
        val withRules = entity.copy(
            merchantRaw = merchantRaw,
            merchantNormalized = normalizedMerchant,
            categoryId = applied.categoryId,
        )

        val duplicateOf = duplicateDetector.findOriginal(withRules)
        val refundOf = if (duplicateOf == null) duplicateDetector.findRefundedCharge(withRules) else null
        val toInsert = withRules.copy(
            duplicateOfId = duplicateOf,
            refundOfId = refundOf,
        )

        val inserted = transactionRepository.insertIgnore(toInsert)
        if (!inserted) return IngestResult("ignored_exact_duplicate")

        merchantRuleEngine.recordHits(applied.matchedRuleIds)

        // A duplicate or a refund must never become a transfer leg.
        val groupId = if (duplicateOf == null && refundOf == null) {
            transferPairer.tryPair(toInsert)
        } else {
            null
        }
        val detail = groupId ?: duplicateOf ?: refundOf
        val classification = when {
            groupId != null -> "saved_internal_transfer"
            duplicateOf != null -> "saved_flagged_duplicate"
            refundOf != null -> "saved_flagged_refund"
            else -> "saved"
        }
        // Keep the classification visible while still recording which parser produced it.
        return IngestResult("$classification/$parserPath", detail = detail)
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
