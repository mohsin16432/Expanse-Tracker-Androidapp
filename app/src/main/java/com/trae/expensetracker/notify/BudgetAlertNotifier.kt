package com.trae.expensetracker.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trae.expensetracker.R
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.repo.BudgetRepository
import com.trae.expensetracker.data.repo.SettingsRepository
import com.trae.expensetracker.ui.BudgetEngine
import com.trae.expensetracker.ui.CategoryRules
import com.trae.expensetracker.ui.CycleUtils
import com.trae.expensetracker.ui.MoneyFormat
import com.trae.expensetracker.ui.TransactionInsights
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Posts a one-off local notification when a budget crosses its warning or over-limit threshold.
 *
 * Each threshold fires at most once per budget per cycle, tracked in settings so the user is
 * not spammed on every import.
 */
class BudgetAlertNotifier(
    private val appContext: Context,
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val CHANNEL_ID = "budget_alerts"
        private const val CHANNEL_NAME = "Budget alerts"
    }

    /**
     * Evaluates budgets against the current cycle's spending and posts any new alerts.
     *
     * @param cycleTransactions transactions for the current cycle (unfiltered).
     */
    suspend fun evaluateAndNotify(cycleTransactions: List<TransactionEntity>) {
        val budgets = runCatching { budgetRepository.getAll() }.getOrDefault(emptyList())
        if (budgets.isEmpty()) return

        val startDay = runCatching { settingsRepository.budgetCycleStartDay().first() }.getOrDefault(1)
        val zone = ZoneId.systemDefault()
        val (cycleFrom, _, cycleStartDate) = CycleUtils.cycleRangeMillis(LocalDate.now(), startDay, zone)
        // Date-based key so the value is stable across the whole cycle.
        val cycleKey = cycleStartDate.toString()

        val spending = TransactionInsights.countableOutgoing(
            cycleTransactions.filter { it.timestampMillis >= cycleFrom }
        )

        val progress = BudgetEngine.progress(
            budgets = budgets,
            spending = spending,
            categoryOf = { CategoryRules.detect(it) },
        )

        val alreadySent = runCatching { settingsRepository.getBudgetAlertsSent() }.getOrDefault(emptySet())
        val pending = BudgetEngine.pendingAlerts(progress, cycleKey, alreadySent)
        if (pending.isEmpty()) return

        pending.forEach { p ->
            post(
                id = p.budget.id.hashCode(),
                title = if (p.isOverBudget) {
                    "Budget exceeded: ${p.categoryName}"
                } else {
                    "Budget warning: ${p.categoryName}"
                },
                message = if (p.isOverBudget) {
                    "Spent ${MoneyFormat.format(p.budget.currency, p.spentMinor)} of ${MoneyFormat.format(p.budget.currency, p.limitMinor)}."
                } else {
                    "${MoneyFormat.format(p.budget.currency, p.remainingMinor)} left of ${MoneyFormat.format(p.budget.currency, p.limitMinor)}."
                },
            )
        }

        val keys = pending.map { BudgetEngine.alertKey(it.budget.id, cycleKey) }
        runCatching { settingsRepository.markBudgetAlertsSent(keys) }
    }

    private fun post(id: Int, title: String, message: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Silently ignore if the user has not granted notification permission.
        runCatching {
            NotificationManagerCompat.from(appContext).notify(id, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Alerts when spending approaches or exceeds a budget limit."
        }
        manager.createNotificationChannel(channel)
    }
}
