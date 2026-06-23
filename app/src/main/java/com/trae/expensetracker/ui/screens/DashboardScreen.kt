package com.trae.expensetracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.ui.CategoryRules
import com.trae.expensetracker.ui.MoneyFormat
import com.trae.expensetracker.ui.theme.Bad
import com.trae.expensetracker.ui.theme.BadBg
import com.trae.expensetracker.ui.theme.Border
import com.trae.expensetracker.ui.theme.Good
import com.trae.expensetracker.ui.theme.GoodBg
import com.trae.expensetracker.ui.theme.Primary
import com.trae.expensetracker.ui.theme.PrimaryContainer
import com.trae.expensetracker.ui.theme.Surface
import com.trae.expensetracker.ui.theme.Surface2
import com.trae.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun DashboardScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var refreshNote by remember { mutableStateOf("Tap Refresh to import the latest inbox SMS.") }
    var isRefreshing by remember { mutableStateOf(false) }
    val now = LocalDate.now()
    val cycleStartDay by container.settingsRepository.budgetCycleStartDay().collectAsState(initial = 1)
    val (cycleStart, cycleEnd) = remember(now, cycleStartDay) { computeCycleRange(now, cycleStartDay) }
    val from = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val to = cycleEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    val sources by container.dataSourceRepository.observeEnabled().collectAsState(initial = emptyList())
    val txs by container.transactionRepository.observeBetween(from, to).collectAsState(initial = emptyList())

    val outgoing = txs.filter { it.direction == TransactionDirection.OUT }
    val incoming = txs.filter { it.direction == TransactionDirection.IN }
    val spentTotalMinor = outgoing.sumOf { it.amountMinor }
    val incomeTotalMinor = incoming.sumOf { it.amountMinor }

    // Breakdown by sourceId (dynamic).
    val spentBySource = outgoing.groupBy { it.sourceId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
    val incomeBySource = incoming.groupBy { it.sourceId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
    val netBySource = sources.associate { s ->
        val out = spentBySource[s.id] ?: 0L
        val inc = incomeBySource[s.id] ?: 0L
        s.id to (inc - out)
    }

    val categoryTotals = outgoing.groupBy { CategoryRules.detect(it) }
        .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        .toList()
        .sortedByDescending { it.second }
    val recentTxs = txs.sortedWith(compareByDescending<com.trae.expensetracker.data.model.TransactionEntity> { it.timestampMillis }.thenByDescending { it.id }).take(5)
    val netMinor = incomeTotalMinor - spentTotalMinor
    val totalFlow = (incomeTotalMinor + spentTotalMinor).coerceAtLeast(1L)
    val spendRatio = (spentTotalMinor.toFloat() / totalFlow.toFloat()).coerceIn(0f, 1f)
    val daysLeft = (cycleEnd.toEpochDay() - now.toEpochDay()).toInt().coerceAtLeast(0)

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(PrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = Primary, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Tracker", style = MaterialTheme.typography.headlineMedium, color = Primary, fontWeight = FontWeight.Bold)
                    Text("Your money at a glance", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Icon(Icons.Outlined.NotificationsNone, contentDescription = "Notifications", tint = TextSecondary)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CURRENT CYCLE", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CycleDateCell(label = "Start", value = cycleStart.toFriendlyUi())
                            Text("→", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            CycleDateCell(label = "End", value = cycleEnd.toFriendlyUi())
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(PrimaryContainer, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("$daysLeft Days Left", color = Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = Border)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Net Position", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (netMinor < 0) "- " else "") + MoneyFormat.format("PKR", abs(netMinor)),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { spendRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Primary,
                    trackColor = Surface2,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Spent: ${MoneyFormat.format("PKR", spentTotalMinor)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("Income: ${MoneyFormat.format("PKR", incomeTotalMinor)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DirectionSummaryCard(
                        title = "Spent",
                        value = "-${MoneyFormat.format("PKR", spentTotalMinor)}",
                        bg = BadBg,
                        fg = Bad,
                        modifier = Modifier.weight(1f)
                    )
                    DirectionSummaryCard(
                        title = "Income",
                        value = "+${MoneyFormat.format("PKR", incomeTotalMinor)}",
                        bg = GoodBg,
                        fg = Good,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Net by Source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(
                enabled = !isRefreshing,
                onClick = {
                    scope.launch {
                        try {
                            isRefreshing = true
                            if (sources.isEmpty()) {
                                refreshNote = "Add data sources in Settings first, then tap Refresh."
                                return@launch
                            }
                            val res = container.smsHistoryImporter.importRecent()
                            refreshNote = "Scanned ${res.scanned} SMS, saved ${res.saved} transactions."
                        } finally {
                            isRefreshing = false
                        }
                    }
                }
            ) { Text(if (isRefreshing) "Refreshing..." else "Refresh") }
        }

        Text(refreshNote, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sources.forEach { s ->
                val net = netBySource[s.id] ?: 0L
                SourceNetCard(
                    label = s.name,
                    value = MoneyFormat.format("PKR", abs(net)),
                    icon = sourceIconForName(s.name),
                    modifier = Modifier.widthIn(min = 150.dp)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Top Spending", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (categoryTotals.isEmpty()) {
                    Text("No spending categories yet.", color = TextSecondary)
                } else {
                    val totalSpending = spentTotalMinor.coerceAtLeast(1L)
                    categoryTotals.take(4).forEach { (category, total) ->
                        TopSpendingRow(
                            category = category,
                            total = total,
                            percent = ((total * 100) / totalSpending).toInt(),
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recent Activity", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                recentTxs.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(PrimaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(categoryIcon(CategoryRules.detect(t)), contentDescription = null, tint = Primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.merchantRaw, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${CategoryRules.detect(t)} • ${Instant.ofEpochMilli(t.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            (if (t.direction == TransactionDirection.IN) "+" else "-") + MoneyFormat.format(t.currency, t.amountMinor),
                            fontWeight = FontWeight.Bold,
                            color = if (t.direction == TransactionDirection.IN) Good else Bad
                        )
                    }
                    HorizontalDivider(color = Border)
                }
            }
        }
    }
}

@Composable
private fun DirectionSummaryCard(
    title: String,
    value: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = fg)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = fg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CycleDateCell(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SourceNetCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = TextSecondary)
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TopSpendingRow(
    category: String,
    total: Long,
    percent: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Surface2, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(categoryIcon(category), contentDescription = null, tint = Primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(category, fontWeight = FontWeight.SemiBold)
            Text("$percent% of total", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Text("-${MoneyFormat.format("PKR", total)}", color = Bad, fontWeight = FontWeight.Bold)
    }
}

private fun categoryIcon(category: String): ImageVector = when {
    category.contains("food", ignoreCase = true) || category.contains("dining", ignoreCase = true) -> Icons.Outlined.Restaurant
    category.contains("shop", ignoreCase = true) -> Icons.Outlined.ShoppingBag
    category.contains("transport", ignoreCase = true) || category.contains("travel", ignoreCase = true) -> Icons.Outlined.LocalTaxi
    category.contains("health", ignoreCase = true) -> Icons.Outlined.MonitorHeart
    else -> Icons.Outlined.Payments
}

private fun sourceIconForName(name: String): ImageVector = when {
    name.contains("cash", ignoreCase = true) -> Icons.Outlined.AccountBalanceWallet
    name.contains("card", ignoreCase = true) -> Icons.Outlined.CreditCard
    else -> Icons.Outlined.AccountBalance
}

private fun LocalDate.toFriendlyUi(): String = format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))

private fun computeCycleRange(today: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
    val safeStart = startDay.coerceIn(1, 28)
    val candidateStartThisMonth = today.withDayOfMonth(safeStart)
    val start = if (today.dayOfMonth >= safeStart) candidateStartThisMonth else candidateStartThisMonth.minusMonths(1)
    val end = start.plusMonths(1).minusDays(1)
    return start to end
}
