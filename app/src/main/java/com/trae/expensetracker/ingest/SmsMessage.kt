package com.trae.expensetracker.ingest

data class SmsMessage(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
)

