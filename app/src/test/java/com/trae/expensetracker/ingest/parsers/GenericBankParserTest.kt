package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GenericBankParserTest {
    // This shape (no Raast keyword) goes to LLM/Review — proves any format lands somewhere.
    @Test
    fun unknownMoneySmsIsNotParsedByGenericButIsFinancial() {
        val body = "PKR 2,500 debited from your account 1234 on 01/06/2026 for payment at STORE X"
        assertNull(GenericBankParser.parse(SmsMessage(sender = "9999", body = body, receivedAtMillis = 0L)))
        assertEquals(true, com.trae.expensetracker.ingest.SmsClassifier.looksLikeFinancialTransaction(body))
    }

    @Test
    fun parsesIncomingRaastTransferWithoutBankSpecificFormat() {
        val draft = GenericBankParser.parse(
            SmsMessage(
                sender = "8870",
                body = "PKR. 3,100.00 received from PK*JCMA0070 in AKBL PKASCM*0061 MOHSIN MUSTAFA via Raast on 07 09 26 at 16 06 Ref# 013160614887",
                receivedAtMillis = 0L,
            )
        )

        assertNotNull(draft)
        assertEquals(310000L, draft!!.amountMinor)
        assertEquals("PKR", draft.currency)
        assertEquals("AKBL", draft.sourceShortCode)
        assertEquals(DataSourceType.BANK, draft.sourceTypeHint)
        assertEquals(TransactionType.TRANSFER_IN, draft.type)
        assertEquals(TransactionDirection.IN, draft.direction)
        assertEquals("PK*JCMA0070", draft.merchantRaw)
        assertEquals("013160614887", draft.reference)
    }

    @Test
    fun parsesOutgoingRaastTransfer() {
        val draft = GenericBankParser.parse(
            SmsMessage(
                sender = "8870",
                body = "PKR. 1,250.00 sent to PK*TEST123 in AKBL PKASCM*0061 MOHSIN MUSTAFA via Raast on 07 09 26 at 16 06 Ref# 1234567890",
                receivedAtMillis = 0L,
            )
        )

        assertNotNull(draft)
        assertEquals(125000L, draft!!.amountMinor)
        assertEquals(TransactionType.TRANSFER_OUT, draft.type)
        assertEquals(TransactionDirection.OUT, draft.direction)
    }
}
