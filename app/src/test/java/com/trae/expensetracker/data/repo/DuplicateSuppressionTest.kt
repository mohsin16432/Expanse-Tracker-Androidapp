package com.trae.expensetracker.data.repo

import com.trae.expensetracker.data.model.MerchantRuleEntity
import com.trae.expensetracker.data.model.MerchantRuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSuppressionTest {

    private val repo = MerchantRuleRepository(NoopRuleDao)

    private fun suppression(pattern: String, enabled: Boolean = true) = MerchantRuleEntity(
        id = pattern,
        type = MerchantRuleType.DUPLICATE_SUPPRESSION,
        pattern = pattern,
        categoryId = null,
        alias = null,
        createdAtMillis = 1L,
        hitCount = 0,
        enabled = enabled,
    )

    @Test
    fun `duplicate key is order independent`() {
        assertEquals(
            MerchantRuleRepository.duplicateKey("hbl-bank", "pkg.wallet"),
            MerchantRuleRepository.duplicateKey("pkg.wallet", "hbl-bank"),
        )
    }

    @Test
    fun `duplicate key ignores blank sources`() {
        assertNull(MerchantRuleRepository.duplicateKey("", "hbl-bank"))
        assertNull(MerchantRuleRepository.duplicateKey("hbl-bank", "   "))
    }

    @Test
    fun `suppression is detected regardless of source order`() {
        val rules = listOf(suppression("hbl-bank|pkg.wallet"))

        assertTrue(repo.isDuplicateSuppressed(rules, "hbl-bank", "pkg.wallet"))
        assertTrue(repo.isDuplicateSuppressed(rules, "pkg.wallet", "hbl-bank"))
        assertFalse(repo.isDuplicateSuppressed(rules, "hbl-bank", "other-bank"))
    }

    @Test
    fun `disabled suppressions are ignored`() {
        val rules = listOf(suppression("hbl-bank|pkg.wallet", enabled = false))

        assertFalse(repo.isDuplicateSuppressed(rules, "hbl-bank", "pkg.wallet"))
    }
}

private object NoopRuleDao : com.trae.expensetracker.data.db.MerchantRuleDao {
    override suspend fun upsert(rule: MerchantRuleEntity) = Unit
    override suspend fun insertAllReplace(items: List<MerchantRuleEntity>) = Unit
    override suspend fun getEnabled(): List<MerchantRuleEntity> = emptyList()
    override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<MerchantRuleEntity>())
    override suspend fun getAll(): List<MerchantRuleEntity> = emptyList()
    override suspend fun findByPattern(type: String, pattern: String): MerchantRuleEntity? = null
    override suspend fun incrementHit(id: String) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun clearAll() = Unit
}
