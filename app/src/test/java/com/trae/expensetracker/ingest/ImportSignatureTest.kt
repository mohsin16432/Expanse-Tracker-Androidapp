package com.trae.expensetracker.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSignatureTest {

    @Test
    fun `same body produces the same signature`() {
        val body = "PKR. 3,100.00 received from PK*JCMA0070 in AKBL via Raast on 07 09 26 Ref# 013160614887"

        assertEquals(ImportSignature.of(body), ImportSignature.of(body))
    }

    @Test
    fun `signature ignores whitespace and case differences`() {
        val a = "PKR 100 received   from ALI"
        val b = "pkr 100 received from ali"
        val c = "PKR 100\nreceived\tfrom  ALI"

        assertEquals(ImportSignature.of(a), ImportSignature.of(b))
        assertEquals(ImportSignature.of(a), ImportSignature.of(c))
    }

    @Test
    fun `different transactions get different signatures`() {
        val a = "PKR. 3,100.00 received from PK*JCMA0070 Ref# 013160614887"
        val b = "PKR. 3,100.00 received from PK*JCMA0070 Ref# 099999999999"

        assertNotEquals(ImportSignature.of(a), ImportSignature.of(b))
    }

    @Test
    fun `preview collapses whitespace and truncates`() {
        val body = "PKR 100   received\nfrom   ALI"

        assertEquals("PKR 100 received from ALI", ImportSignature.previewOf(body))
        assertTrue(ImportSignature.previewOf("x".repeat(200)).length <= 80)
    }
}
