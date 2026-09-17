package com.trae.expensetracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.ui.BudgetEngine
import com.trae.expensetracker.ui.CategoryRules
import com.trae.expensetracker.ui.CycleUtils
import com.trae.expensetracker.ui.MoneyFormat
import com.trae.expensetracker.ui.RecurringDetector
import com.trae.expensetracker.ui.ReportAnalytics
import com.trae.expensetracker.ui.SplitCalculator
import com.trae.expensetracker.ui.TransactionInsights
import com.trae.expensetracker.ui.charts.BarChart
import com.trae.expensetracker.ui.charts.BarEntry
import com.trae.expensetracker.ui.charts.DonutChart
import com.trae.expensetracker.ui.charts.DonutSlice
import com.trae.expensetracker.ui.theme.Bad
import com.trae.expensetracker.ui.theme.Border
import com.trae.expensetracker.ui.theme.Good
import com.trae.expensetracker.ui.theme.Primary
import com.trae.expensetracker.ui.theme.Surface
import com.trae.expensetracker.ui.theme.Surface2
import com.trae.expensetracker.ui.theme.TextSecondary
import com.trae.expensetracker.ui.theme.Warn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Palette for category slices. Cycles if there are more categories than colours. */
private val slicePalette = listOf(
    Color(0xFF2D5DA1),
    Color(0xFF6B5DFF),
    Color(0xFF39D8AB),
    Color(0xFFF4C063),
    Color(0xFFFF6A7C),
    Color(0xFF8D7BFF),
    Color(0xFF4FB3D9),
    Color(0xFF95A0BD),
)

@Composable
fun ReportsScreen(container: AppContainer) {
    val cycleStartDay by container.settingsRepository.budgetCycleStartDay().collectAsState(initial = 1)
    val budgets by container.budgetRepository.observeAll().collectAsState(initial = emptyList())

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val (cycleFrom, cycleToExclusive, cycleStart) = remember(today, cycleStartDay) {
        CycleUtils.cycleRangeMillis(today, cycleStartDay, zone)
    }
    val cycleEnd = remember(cycleStart, cycleStartDay) {
        CycleUtils.cycleRange(cycleStart, cycleStartDay).second
    }

    // Six months of history powers the trend chart and subscription detection.
    val historyFrom = remember(cycleFrom) {
        java.time.Instant.ofEpochMilli(cycleFrom)
            .atZone(zone).toLocalDate().minusMonths(5).withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val history by container.transactionRepository.observeBetween(historyFrom, cycleToExclusive - 1)
        .collectAsState(initial = emptyList())

    val cycleTxs = history.filter { it.timestampMillis >= cycleFrom && it.timestampMillis < cycleToExclusive }
    val spending = TransactionInsights.countableOutgoing(cycleTxs)
    val income = TransactionInsights.incomingTotalMinor(cycleTxs)
    val spent = spending.sumOf { it.amountMinor }

    val splits by container.transactionSplitRepository.observeAll().collectAsState(initial = emptyList())
    val splitsByTx = remember(splits) { splits.groupBy { it.transactionId } }
    // Split-aware: a split transaction contributes to each of its categories.
    val breakdown = remember(spending, splitsByTx) {
        SplitCalculator.totalsByCategory(spending, splitsByTx) { CategoryRules.detect(it) }
            .toList()
            .map { (category, total) ->
                ReportAnalytics.CategorySlice(
                    category = category,
                    totalMinor = total,
                    fraction = if (spent > 0) total.toDouble() / spent.toDouble() else 0.0,
                    count = 1,
                )
            }
            .sortedByDescending { it.totalMinor }
    }
    val merchants = ReportAnalytics.topMerchants(spending, limit = 5)
    val trend = ReportAnalytics.monthlyTrend(history, months = 6, today = today, zone = zone)
    val recurring = RecurringDetector.detect(history)

    val daysElapsed = ((today.toEpochDay() - cycleStart.toEpochDay()) + 1).coerceAtLeast(1).toInt()
    val daysInCycle = ((cycleEnd.toEpochDay() - cycleStart.toEpochDay()) + 1).toInt()
    val averageDaily = ReportAnalytics.averageDailySpend(spent, daysElapsed)
    val projected = ReportAnalytics.projectedCycleSpend(spent, daysElapsed, daysInCycle)

    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM", Locale.US) }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Cycle ${cycleStart.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))} onwards • last 6 months of history",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This cycle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Spent", MoneyFormat.format("PKR", spent), Bad)
                        StatBlock("Income", MoneyFormat.format("PKR", income), Good)
                    }
                    HorizontalDivider(color = Border)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Avg / day", MoneyFormat.format("PKR", averageDaily), TextSecondary)
                        StatBlock(
                            "Projected",
                            MoneyFormat.format("PKR", projected),
                            if (projected > spent) Warn else TextSecondary
                        )
                    }
                    Text(
                        "Projection assumes the current pace continues for the remaining ${
                            (daysInCycle - daysElapsed).coerceAtLeast(0)
                        } day(s) of this cycle.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (budgets.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Border)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Budget progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val progress = BudgetEngine.progress(
                            budgets = budgets,
                            spending = spending,
                            categoryOf = { CategoryRules.detect(it) },
                        )
                        if (progress.isEmpty()) {
                            Text("No budgets configured yet. Add one in Settings.", color = TextSecondary)
                        }
                        progress.forEach { p ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(p.categoryName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${MoneyFormat.format(p.budget.currency, p.spentMinor)} / ${MoneyFormat.format(p.budget.currency, p.limitMinor)}",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { p.fraction.coerceIn(0.0, 1.0).toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = when {
                                        p.isOverBudget -> Bad
                                        p.isWarning -> Warn
                                        else -> Primary
                                    },
                                    trackColor = Surface2,
                                )
                                Text(
                                    when {
                                        p.isOverBudget -> "Over by ${MoneyFormat.format(p.budget.currency, p.spentMinor - p.limitMinor)}"
                                        else -> "${MoneyFormat.format(p.budget.currency, p.remainingMinor)} left"
                                    },
                                    color = if (p.isOverBudget) Bad else TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spending by category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (breakdown.isEmpty()) {
                        Text("No spending recorded in this cycle yet.", color = TextSecondary)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DonutChart(
                                slices = breakdown.mapIndexed { index, slice ->
                                    DonutSlice(
                                        label = slice.category,
                                        value = slice.totalMinor.toDouble(),
                                        color = slicePalette[index % slicePalette.size],
                                    )
                                },
                                center = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Spent", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text(
                                            MoneyFormat.format("PKR", spent),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                breakdown.take(5).forEachIndexed { index, slice ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(slicePalette[index % slicePalette.size], CircleShape)
                                        )
                                        Text(
                                            slice.category,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "${(slice.fraction * 100).toInt()}%",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("6-month trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(color = Bad, label = "Spent")
                        LegendDot(color = Good, label = "Income")
                    }
                    BarChart(
                        entries = trend.map { point ->
                            BarEntry(
                                label = point.month.format(monthFormatter),
                                primary = point.spentMinor.toDouble(),
                                secondary = point.incomeMinor.toDouble(),
                            )
                        },
                        primaryColor = Bad,
                        secondaryColor = Good,
                    )
                }
            }
        }

        if (merchants.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Border)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Top merchants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        merchants.forEach { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.merchant, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        "${m.count} transaction${if (m.count == 1) "" else "s"}",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    MoneyFormat.format("PKR", m.totalMinor),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = Border)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Border)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Detected subscriptions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (recurring.isEmpty()) {
                        Text(
                            "No recurring charges detected yet. The app needs at least 3 charges from the same merchant at a steady interval.",
                            color = TextSecondary
                        )
                    } else {
                        val monthlyTotal = recurring.sumOf { it.monthlyCostMinor }
                        Text(
                            "About ${MoneyFormat.format("PKR", monthlyTotal)} per month across ${recurring.size} subscription(s).",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        recurring.forEach { r ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        "${RecurringDetector.cadenceLabel(r.cadenceDays)} • ${r.occurrences} charges",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    MoneyFormat.format(r.currency, r.averageAmountMinor),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = Border)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
