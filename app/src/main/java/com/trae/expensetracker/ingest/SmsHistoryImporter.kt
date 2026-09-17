package com.trae.expensetracker.ingest

import android.content.Context
import android.provider.Telephony
import com.trae.expensetracker.data.repo.TransactionRepository
import com.trae.expensetracker.notify.BudgetAlertNotifier

class SmsHistoryImporter(
    private val appContext: Context,
    private val smsIngestor: SmsIngestor,
    private val transactionRepository: TransactionRepository,
    private val budgetAlertNotifier: BudgetAlertNotifier,
) {
    data class ImportResult(val scanned: Int, val saved: Int)

    suspend fun importRecent(limit: Int = 1200): ImportResult {
        return try {
            importRecentInternal(limit)
        } catch (_: SecurityException) {
            ImportResult(scanned = 0, saved = 0)
        } catch (_: Throwable) {
            ImportResult(scanned = 0, saved = 0)
        }
    }

    private suspend fun importRecentInternal(limit: Int): ImportResult {
        smsIngestor.invalidateUserFormatsCache()
        smsIngestor.invalidateMerchantRuleCache()
        val before = transactionRepository.countAll()
        var scanned = 0
        val resolver = appContext.contentResolver
        val projection = arrayOf(
            Telephony.TextBasedSmsColumns.ADDRESS,
            Telephony.TextBasedSmsColumns.BODY,
            Telephony.TextBasedSmsColumns.DATE,
        )
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.TextBasedSmsColumns.DATE} DESC"
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.DATE)

            while (cursor.moveToNext() && scanned < limit) {
                val sender = cursor.getString(addressIdx).orEmpty()
                val body = cursor.getString(bodyIdx).orEmpty()
                val date = cursor.getLong(dateIdx)
                smsIngestor.ingest(SmsMessage(sender = sender, body = body, receivedAtMillis = date), allowLlmFallback = false)
                scanned++
            }
        }
        val after = transactionRepository.countAll()
        val saved = (after - before).toInt().coerceAtLeast(0)

        // Re-evaluate budgets once after a bulk import rather than per message.
        if (saved > 0) {
            runCatching { budgetAlertNotifier.evaluateAndNotify(transactionRepository.getAll()) }
        }

        return ImportResult(scanned = scanned, saved = saved)
    }
}
