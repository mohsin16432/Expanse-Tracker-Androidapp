package com.trae.expensetracker.ingest.parsers

import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft

interface BankSmsParser {
    /** Return null if this parser does not recognize the message. */
    fun parse(message: SmsMessage): TransactionDraft?
}

