package com.trae.expensetracker.ingest

import com.trae.expensetracker.data.model.MerchantRuleEntity
import com.trae.expensetracker.data.model.MerchantRuleType
import com.trae.expensetracker.data.repo.MerchantRuleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantRuleEngineTest {

    private fun rule(
        id: String,
        type: MerchantRuleType,
        pattern: String,
        categoryId: String? = null,
        alias: String? = null,
    ) = MerchantRuleEntity(
        id = id,
        type = type,
        pattern = pattern,
        categoryId = categoryId,
        alias = alias,
        createdAtMillis = 1L,
        hitCount = 0,
        enabled = true,
    )

    @Test
    fun `learned alias and category are applied to a new merchant name`() = runBlocking {
        val rules = listOf(
            rule("r1", MerchantRuleType.MERCHANT_ALIAS, "pk jcma0070", alias = "Ahmed"),
            rule("r2", MerchantRuleType.MERCHANT_CATEGORY, "pk jcma0070", categoryId = "Transfer"),
        )
        val engine = MerchantRuleEngine(FakeRuleRepository(rules))

        val applied = engine.apply("PK*JCMA0070", existingCategoryId = null)

        assertEquals("Ahmed", applied.merchantRaw)
        assertEquals("Transfer", applied.categoryId)
        assertEquals(2, applied.matchedRuleIds.size)
    }

    @Test
    fun `existing category is not overwritten by a rule`() = runBlocking {
        val rules = listOf(
            rule("r1", MerchantRuleType.MERCHANT_CATEGORY, "daraz", categoryId = "Shopping"),
        )
        val engine = MerchantRuleEngine(FakeRuleRepository(rules))

        val applied = engine.apply("DARAZ LAHORE", existingCategoryId = "Other")

        assertEquals("Other", applied.categoryId)
    }

    @Test
    fun `duplicate suppression rules never rewrite a merchant name`() = runBlocking {
        val rules = listOf(
            rule("r1", MerchantRuleType.DUPLICATE_SUPPRESSION, "hbl-bank|pkg.wallet"),
        )
        val engine = MerchantRuleEngine(FakeRuleRepository(rules))

        val applied = engine.apply("SOME SHOP", existingCategoryId = null)

        assertEquals("SOME SHOP", applied.merchantRaw)
        assertNull(applied.categoryId)
        assertEquals(0, applied.matchedRuleIds.size)
    }

    @Test
    fun `unmatched merchant passes through untouched`() = runBlocking {
        val rules = listOf(
            rule("r1", MerchantRuleType.MERCHANT_CATEGORY, "daraz", categoryId = "Shopping"),
        )
        val engine = MerchantRuleEngine(FakeRuleRepository(rules))

        val applied = engine.apply("TOTALLY UNKNOWN SHOP", existingCategoryId = null)

        assertEquals("TOTALLY UNKNOWN SHOP", applied.merchantRaw)
        assertNull(applied.categoryId)
    }
}

private class FakeRuleRepository(
    private val rules: List<MerchantRuleEntity>,
) : MerchantRuleRepository(UnsupportedRuleDao) {
    override suspend fun getAll(): List<MerchantRuleEntity> = rules
    override suspend fun incrementHit(id: String) = Unit
}

private object UnsupportedRuleDao : com.trae.expensetracker.data.db.MerchantRuleDao {
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
