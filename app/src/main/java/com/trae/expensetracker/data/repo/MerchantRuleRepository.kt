package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.db.MerchantRuleDao
import com.trae.expensetracker.data.model.MerchantRuleEntity
import com.trae.expensetracker.data.model.MerchantRuleType
import com.trae.expensetracker.ingest.parsers.ParseUtils
import kotlinx.coroutines.flow.Flow

/**
 * Stores user-learned merchant knowledge:
 *  - which category a merchant belongs to
 *  - what display name a merchant should use (alias)
 *
 * Rules are learned automatically when the user corrects a transaction in Review, so the same
 * correction never has to be repeated for future imports.
 */
open class MerchantRuleRepository(
    private val dao: MerchantRuleDao,
) {
    open fun observeAll(): Flow<List<MerchantRuleEntity>> = dao.observeAll()

    open suspend fun getAll(): List<MerchantRuleEntity> = dao.getAll()

    open suspend fun learnCategory(merchantRaw: String, categoryId: String) {
        val pattern = patternFor(merchantRaw) ?: return
        val existing = dao.findByPattern(MerchantRuleType.MERCHANT_CATEGORY.name, pattern)
        dao.upsert(
            MerchantRuleEntity(
                id = existing?.id ?: idFor(MerchantRuleType.MERCHANT_CATEGORY, pattern),
                type = MerchantRuleType.MERCHANT_CATEGORY,
                pattern = pattern,
                categoryId = categoryId,
                alias = null,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                hitCount = existing?.hitCount ?: 0,
                enabled = true,
            )
        )
    }

    open suspend fun learnAlias(merchantRaw: String, alias: String) {
        val pattern = patternFor(merchantRaw) ?: return
        val cleanAlias = alias.trim().takeIf { it.isNotBlank() } ?: return
        val existing = dao.findByPattern(MerchantRuleType.MERCHANT_ALIAS.name, pattern)
        dao.upsert(
            MerchantRuleEntity(
                id = existing?.id ?: idFor(MerchantRuleType.MERCHANT_ALIAS, pattern),
                type = MerchantRuleType.MERCHANT_ALIAS,
                pattern = pattern,
                categoryId = null,
                alias = cleanAlias,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                hitCount = existing?.hitCount ?: 0,
                enabled = true,
            )
        )
    }

    /**
     * Remembers that two sources posting the same amount close together is expected, not a
     * duplicate. Useful when a legitimate transfer is captured by both an SMS and the bank's
     * own notification.
     */
    open suspend fun suppressDuplicate(sourceA: String, sourceB: String) {
        val key = duplicateKey(sourceA, sourceB) ?: return
        val existing = dao.findByPattern(MerchantRuleType.DUPLICATE_SUPPRESSION.name, key)
        dao.upsert(
            MerchantRuleEntity(
                id = existing?.id ?: idFor(MerchantRuleType.DUPLICATE_SUPPRESSION, key),
                type = MerchantRuleType.DUPLICATE_SUPPRESSION,
                pattern = key,
                categoryId = null,
                alias = null,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                hitCount = existing?.hitCount ?: 0,
                enabled = true,
            )
        )
    }

    /** True when the user has already declared this pair of sources non-duplicating. */
    fun isDuplicateSuppressed(rules: List<MerchantRuleEntity>, sourceA: String, sourceB: String): Boolean {
        val key = duplicateKey(sourceA, sourceB) ?: return false
        return rules.any {
            it.enabled && it.type == MerchantRuleType.DUPLICATE_SUPPRESSION && it.pattern == key
        }
    }

    open suspend fun setEnabled(rule: MerchantRuleEntity, enabled: Boolean) {
        dao.upsert(rule.copy(enabled = enabled))
    }

    open suspend fun delete(id: String) = dao.deleteById(id)

    open suspend fun replaceAll(items: List<MerchantRuleEntity>) = dao.insertAllReplace(items)

    open suspend fun clearAll() = dao.clearAll()

    open suspend fun incrementHit(id: String) = dao.incrementHit(id)

    /**
     * A rule pattern is the normalized merchant name, trimmed to a stable prefix so that
     * trailing reference numbers / branch codes do not prevent matching.
     */
    fun patternFor(merchantRaw: String): String? = patternForStatic(merchantRaw)

    private fun idFor(type: MerchantRuleType, pattern: String): String =
        ParseUtils.sha256Hex("${type.name}|$pattern").take(24)

    companion object {
        /** Order-independent key so the pair matches regardless of which row is the original. */
        fun duplicateKey(sourceA: String, sourceB: String): String? {
            val a = sourceA.trim()
            val b = sourceB.trim()
            if (a.isBlank() || b.isBlank()) return null
            return listOf(a, b).sorted().joinToString("|")
        }

        /**
         * A rule pattern is the normalized merchant name, trimmed to a stable prefix so that
         * trailing reference numbers / branch codes do not prevent matching.
         */
        fun patternForStatic(merchantRaw: String): String? {
            val normalized = ParseUtils.normalizeMerchant(merchantRaw).lowercase()
            if (normalized.isBlank()) return null
            return normalized.take(48).trim()
        }
    }
}
