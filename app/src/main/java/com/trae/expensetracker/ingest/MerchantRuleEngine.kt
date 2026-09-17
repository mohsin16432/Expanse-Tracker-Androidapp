package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.MerchantRuleEntity
import com.trae.expensetracker.data.model.MerchantRuleType
import com.trae.expensetracker.data.repo.MerchantRuleRepository

/**
 * Applies user-learned merchant rules to incoming transactions.
 *
 * Rules are cheap substring matches, so they are applied in-memory against a cached list
 * instead of hitting the database for every SMS.
 */
class MerchantRuleEngine(
    private val merchantRuleRepository: MerchantRuleRepository,
) {
    private var cachedRules: List<MerchantRuleEntity> = emptyList()
    private var lastLoadMillis: Long = 0L

    data class Applied(
        val merchantRaw: String,
        val categoryId: String?,
        val matchedRuleIds: List<String>,
    )

    suspend fun apply(merchantRaw: String, existingCategoryId: String?): Applied {
        val rules = load()
        if (rules.isEmpty()) {
            return Applied(merchantRaw = merchantRaw, categoryId = existingCategoryId, matchedRuleIds = emptyList())
        }

        val normalized = MerchantRuleRepository.patternForStatic(merchantRaw) ?: return Applied(
            merchantRaw = merchantRaw,
            categoryId = existingCategoryId,
            matchedRuleIds = emptyList(),
        )

        var alias: String? = null
        var categoryId = existingCategoryId
        val matched = mutableListOf<String>()

        for (rule in rules) {
            if (!rule.enabled) continue
            // Duplicate suppressions are not merchant-name transformations.
            if (rule.type == MerchantRuleType.DUPLICATE_SUPPRESSION) continue
            if (rule.pattern.isBlank() || !normalized.contains(rule.pattern)) continue

            when (rule.type) {
                MerchantRuleType.MERCHANT_ALIAS -> {
                    if (alias == null && !rule.alias.isNullOrBlank()) {
                        alias = rule.alias
                        matched += rule.id
                    }
                }
                MerchantRuleType.MERCHANT_CATEGORY -> {
                    if (categoryId.isNullOrBlank() && !rule.categoryId.isNullOrBlank()) {
                        categoryId = rule.categoryId
                        matched += rule.id
                    }
                }
                MerchantRuleType.DUPLICATE_SUPPRESSION -> Unit
            }
        }

        return Applied(
            merchantRaw = alias?.takeIf { it.isNotBlank() } ?: merchantRaw,
            categoryId = categoryId,
            matchedRuleIds = matched,
        )
    }

    /** Records that a rule was used, so frequently-useful rules sort first in the UI. */
    suspend fun recordHits(ids: List<String>) {
        ids.forEach { id -> runCatching { merchantRuleRepository.incrementHit(id) } }
    }

    fun invalidateCache() {
        lastLoadMillis = 0L
    }

    private suspend fun load(): List<MerchantRuleEntity> {
        val now = System.currentTimeMillis()
        if (now - lastLoadMillis > CACHE_TTL_MILLIS) {
            cachedRules = runCatching { merchantRuleRepository.getAll() }.getOrDefault(emptyList())
            lastLoadMillis = now
        }
        return cachedRules
    }

    private companion object {
        const val CACHE_TTL_MILLIS: Long = 60_000L
    }
}
