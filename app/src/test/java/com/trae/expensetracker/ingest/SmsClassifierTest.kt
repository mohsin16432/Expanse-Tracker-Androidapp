package com.trae.expensetracker.ingest

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsClassifierTest {
    @Test fun withdrawalSmsIsStillFinancial() {
        val body = "Your HBL A/C has been debited with PKR 10,000.00 on 11/Jun/2026 for ATM Cash Withdrawal"
        assertEquals(true, SmsClassifier.isWithdrawal(body))
        assertEquals(true, SmsClassifier.looksLikeFinancialTransaction(body))
    }
    @Test fun otpStillExcluded() {
        assertEquals(false, SmsClassifier.looksLikeFinancialTransaction("Your OTP is 123456 valid for 5 minutes"))
    }
}
