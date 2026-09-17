package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GenericBankParserTest {
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
