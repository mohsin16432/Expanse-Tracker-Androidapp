package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ingest.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HblParserTest {
    private val parser = HblParser()

    @Test
    fun parsesAtmCashWithdrawalAsWithdrawal() {
        val draft = parser.parse(
            SmsMessage(
                sender = "14250",
                body = "Your HBL A/C 2290***12703 has been debited with PKR 10,000.00 on 11/Jun/2026 for ATM Cash Withdrawal. Ref: 123456",
                receivedAtMillis = 0L,
            )
        )
        assertNotNull(draft)
        assertEquals(TransactionType.ATM_WITHDRAWAL, draft!!.type)
        assertEquals(TransactionDirection.OUT, draft.direction)
        assertEquals(1_000_000L, draft.amountMinor)
        assertEquals("ATM", draft.merchantRaw)
    }

    @Test
    fun parsesAtmWithdrawalCaseInsensitive() {
        val draft = parser.parse(
            SmsMessage(
                sender = "14250",
                body = "Your HBL A/C has been debited with PKR 5,000 on 01/01/2026 for atm withdrawal",
                receivedAtMillis = 0L,
            )
        )
        assertNotNull(draft)
        assertEquals(TransactionType.ATM_WITHDRAWAL, draft!!.type)
    }

    @Test
    fun ignoresNonHblSender() {
        val draft = parser.parse(
            SmsMessage(sender = "9999", body = "ATM Cash Withdrawal PKR 1,000", receivedAtMillis = 0L)
        )
        assertNull(draft)
    }
}
