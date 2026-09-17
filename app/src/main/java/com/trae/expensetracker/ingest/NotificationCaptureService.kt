package com.trae.expensetracker.ingest

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.trae.expensetracker.ExpenseTrackerApp
import com.trae.expensetracker.data.repo.NotificationDebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Captures bank/wallet push notifications (after user grants Notification Access)
 * and routes them through the same ingestion pipeline as SMS.
 */
class NotificationCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = applicationContext as? ExpenseTrackerApp ?: return
        val container = app.container

        val pkg = sbn.packageName.orEmpty()
        if (pkg.isBlank()) return

        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val body = listOf(title, big.ifBlank { text }).filter { it.isNotBlank() }.joinToString("\n").trim()
        if (body.isBlank()) return

        // Quick filter: ignore obvious non-financial notifications to reduce load.
        if (!SmsClassifier.looksLikeFinancialTransaction(body)) {
            CoroutineScope(Dispatchers.IO).launch {
                container.settingsRepository.addNotificationDebugLog(
                    NotificationDebugLog(
                        timestampMillis = System.currentTimeMillis(),
                        packageName = pkg,
                        title = title,
                        bodyPreview = body.take(240),
                        status = "ignored_non_financial",
                    )
                )
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val allowed = container.settingsRepository.getNotificationAllowedPackages()
            // Safer default: if user hasn't selected any apps, do nothing.
            if (allowed.isEmpty()) {
                container.settingsRepository.addNotificationDebugLog(
                    NotificationDebugLog(System.currentTimeMillis(), pkg, title, body.take(240), "ignored_no_apps_selected")
                )
                return@launch
            }
            if (!allowed.contains(pkg)) {
                container.settingsRepository.addNotificationDebugLog(
                    NotificationDebugLog(System.currentTimeMillis(), pkg, title, body.take(240), "ignored_app_not_selected")
                )
                return@launch
            }
            val result = container.smsIngestor.ingest(
                SmsMessage(sender = pkg, body = body, receivedAtMillis = System.currentTimeMillis()),
                allowLlmFallback = true
            )
            container.settingsRepository.addNotificationDebugLog(
                NotificationDebugLog(
                    timestampMillis = System.currentTimeMillis(),
                    packageName = pkg,
                    title = title,
                    bodyPreview = body.take(240),
                    status = result.status,
                    detail = result.detail ?: result.sourceId,
                )
            )
        }
    }
}
