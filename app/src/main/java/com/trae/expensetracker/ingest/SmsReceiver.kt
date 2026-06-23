package com.trae.expensetracker.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.trae.expensetracker.ExpenseTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as ExpenseTrackerApp
        val ingestor = app.container.smsIngestor

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            pending.finish()
            return
        }

        // Combine multipart SMS into one body.
        val sender = messages.first().displayOriginatingAddress ?: ""
        val body = buildString {
            messages.forEach { append(it.displayMessageBody ?: "") }
        }
        val receivedAt = messages.first().timestampMillis

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ingestor.ingest(SmsMessage(sender = sender, body = body, receivedAtMillis = receivedAt), allowLlmFallback = true)
            } finally {
                pending.finish()
            }
        }
    }
}
