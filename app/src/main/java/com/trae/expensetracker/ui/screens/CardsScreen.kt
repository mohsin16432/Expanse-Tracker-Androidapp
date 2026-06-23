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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun CardsScreen(container: AppContainer) {
    val cutoffDay by container.settingsRepository.statementCutoffDay().collectAsState(initial = 10)
    val sources by container.dataSourceRepository.observeAll().collectAsState(initial = emptyList())

    val cardSources = sources.filter { it.type == DataSourceType.CREDIT_CARD }.associateBy { it.id }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    // Compute current statement cycle [start, end] based on cutoff day.
    val cycleStart = run {
        val day = cutoffDay.coerceIn(1, 28)
        val thisMonthCutoff = today.withDayOfMonth(day)
        if (today.isBefore(thisMonthCutoff)) thisMonthCutoff.minusMonths(1) else thisMonthCutoff
    }
    val cycleEndExclusive = cycleStart.plusMonths(1)
    val from = cycleStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = cycleEndExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1

    val txs by container.transactionRepository.observeBetween(from, to).collectAsState(initial = emptyList())
    val cardTxs = txs
        .filter { cardSources.containsKey(it.sourceId) }
        .sortedWith(compareByDescending<com.trae.expensetracker.data.model.TransactionEntity> { it.timestampMillis }.thenByDescending { it.id })
    val outTotal = cardTxs.filter { it.direction == TransactionDirection.OUT }.sumOf { it.amountMinor }
    val inTotal = cardTxs.filter { it.direction == TransactionDirection.IN }.sumOf { it.amountMinor }
    val totalFlow = (outTotal + inTotal).coerceAtLeast(1L)
    val spendRatio = (outTotal.toFloat() / totalFlow.toFloat()).coerceIn(0f, 1f)
    val groupedCardTxs = cardTxs.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }.toList()

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cards", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CURRENT CYCLE SPEND", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CycleInfoCell(label = "Start", value = cycleStart.toFriendlyUi())
                            Text("→", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            CycleInfoCell(label = "End", value = cycleEndExclusive.minusDays(1).toFriendlyUi())
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(PrimaryContainer, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("${cardSources.size} Cards", color = Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = Border)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Total card spend", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        MoneyFormat.format("PKR", outTotal),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { spendRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Primary,
                    trackColor = Surface2,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Spent: ${MoneyFormat.format("PKR", outTotal)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("Refunds: ${MoneyFormat.format("PKR", inTotal)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    CardCycleSummaryCard(
                        title = "Spent",
                        value = "-${MoneyFormat.format("PKR", outTotal)}",
                        fg = Bad,
                        bg = BadBg,
                        modifier = Modifier.weight(1f)
                    )
                    CardCycleSummaryCard(
                        title = "Refunds",
                        value = "+${MoneyFormat.format("PKR", inTotal)}",
                        fg = Good,
                        bg = GoodBg,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (groupedCardTxs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Border)
                    ) {
                        Text(
                            "No card transactions found in the current cycle.",
                            modifier = Modifier.padding(16.dp),
                            color = TextSecondary
                        )
                    }
                }
            } else {
                groupedCardTxs.forEach { (date, itemsForDate) ->
                    val dayNet = itemsForDate.sumOf { if (it.direction == TransactionDirection.IN) it.amountMinor else -it.amountMinor }
                    val dayCurrency = itemsForDate.firstOrNull()?.currency ?: "PKR"
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                date.formatHeader(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                (if (dayNet >= 0) "+" else "-") + MoneyFormat.format(dayCurrency, abs(dayNet)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dayNet >= 0) Good else Bad,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            border = BorderStroke(1.dp, Border)
                        ) {
                            Column {
                                itemsForDate.forEachIndexed { index, t ->
                                    CardTransactionRow(
                                        tx = t,
                                        sourceLabel = cardSources[t.sourceId]?.name ?: t.sourceId
                                    )
                                    if (index != itemsForDate.lastIndex) {
                                        HorizontalDivider(color = Border)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardCycleSummaryCard(
    title: String,
    value: String,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
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
            Text(value, style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CycleInfoCell(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CardTransactionRow(
    tx: TransactionEntity,
    sourceLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (tx.direction == TransactionDirection.IN) GoodBg else Surface2,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CreditCard,
                contentDescription = null,
                tint = if (tx.direction == TransactionDirection.IN) Good else Primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.merchantRaw, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(
                "${CategoryRules.detect(tx)} • $sourceLabel",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            (if (tx.direction == TransactionDirection.IN) "+" else "-") + MoneyFormat.format(tx.currency, abs(tx.amountMinor)),
            fontWeight = FontWeight.Bold,
            color = if (tx.direction == TransactionDirection.IN) Good else Bad,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

private fun LocalDate.formatHeader(): String {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    return when (this) {
        today -> "TODAY, ${format(DateTimeFormatter.ofPattern("MMM d"))}".uppercase()
        yesterday -> "YESTERDAY, ${format(DateTimeFormatter.ofPattern("MMM d"))}".uppercase()
        else -> format(DateTimeFormatter.ofPattern("MMM d")).uppercase()
    }
}

private fun LocalDate.toFriendlyUi(): String = format(DateTimeFormatter.ofPattern("dd MMM"))
