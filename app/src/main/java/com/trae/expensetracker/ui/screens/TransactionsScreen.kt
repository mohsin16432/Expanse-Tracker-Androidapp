package com.trae.expensetracker.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.ui.CategoryRules
import com.trae.expensetracker.ui.CycleUtils
import com.trae.expensetracker.ui.MoneyFormat
import com.trae.expensetracker.ui.TransactionInsights
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
import java.time.format.DateTimeFormatter

private enum class RangePreset { TODAY, THIS_MONTH }
private enum class DirectionFilter { ALL, SPENDING, INCOME }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(RangePreset.THIS_MONTH) }
    var directionFilter by remember { mutableStateOf(DirectionFilter.ALL) }
    var refreshNote by remember { mutableStateOf("Search by merchant, category, or source.") }
    var isRefreshing by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var draftSelectedSourceId by remember { mutableStateOf<String?>(null) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var draftFromDate by remember { mutableStateOf<LocalDate?>(null) }
    var draftToDate by remember { mutableStateOf<LocalDate?>(null) }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var viewingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var draftSelectedCategory by remember { mutableStateOf<String?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var minAmountEdit by remember { mutableStateOf("") }
    var maxAmountEdit by remember { mutableStateOf("") }
    var appliedMinAmountMinor by remember { mutableStateOf<Long?>(null) }
    var appliedMaxAmountMinor by remember { mutableStateOf<Long?>(null) }
    var hideTransfersAndDuplicates by remember { mutableStateOf(true) }

    val sources by container.dataSourceRepository.observeAll().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    val cycleStartDay by container.settingsRepository.budgetCycleStartDay().collectAsState(initial = 1)

    val now = LocalDate.now()
    val presetRange = when (preset) {
        RangePreset.TODAY -> now to now
        RangePreset.THIS_MONTH -> CycleUtils.cycleRange(now, cycleStartDay)
    }
    val effectiveFrom = fromDate ?: presetRange.first
    val effectiveTo = toDate ?: presetRange.second

    val fromMillis = effectiveFrom.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val toMillis = effectiveTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    val txsFlow = if (query.isBlank()) {
        container.transactionRepository.observeBetween(fromMillis, toMillis)
    } else {
        container.transactionRepository.searchBetween(query, fromMillis, toMillis)
    }
    val txs by txsFlow.collectAsState(initial = emptyList())

    val filteredTxs = when (directionFilter) {
        DirectionFilter.ALL -> txs
        DirectionFilter.SPENDING -> txs.filter { it.direction == TransactionDirection.OUT }
        DirectionFilter.INCOME -> txs.filter { it.direction == TransactionDirection.IN }
    }
        .filter { selectedSourceId == null || it.sourceId == selectedSourceId }
        .filter { selectedCategory == null || CategoryRules.detect(it).equals(selectedCategory, ignoreCase = true) }
        .filter { appliedMinAmountMinor == null || it.amountMinor >= appliedMinAmountMinor!! }
        .filter { appliedMaxAmountMinor == null || it.amountMinor <= appliedMaxAmountMinor!! }
        .filter { !hideTransfersAndDuplicates || !TransactionInsights.isExcludedFromTotals(it) }
        .sortedWith(compareByDescending<TransactionEntity> { it.timestampMillis }.thenByDescending { it.id })
    // Transfers and duplicates stay visible in the list but are excluded from the totals row.
    val spentTotal = TransactionInsights.outgoingTotalMinor(filteredTxs)
    val incomeTotal = TransactionInsights.incomingTotalMinor(filteredTxs)
    val sourceMap = sources.associateBy { it.id }
    val groupedTxs = filteredTxs
        .sortedByDescending { it.timestampMillis }
        .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate() }
        .toList()
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchFocusRequester = remember { FocusRequester() }

    fun notify(msg: String) {
        refreshNote = msg
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
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
                    Text("Transactions", style = MaterialTheme.typography.headlineLarge, color = Primary, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { searchFocusRequester.requestFocus() }
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Focus search",
                            tint = TextSecondary
                        )
                    }
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = "Notifications", tint = TextSecondary)
                }
            }
        }

        item {
            Text(refreshNote, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChipButton(
                    label = "Today",
                    selected = preset == RangePreset.TODAY,
                    onClick = {
                        preset = RangePreset.TODAY
                        if (!showFilter) {
                            draftFromDate = null
                            draftToDate = null
                            fromDate = null
                            toDate = null
                        }
                    }
                )
                FilterChipButton(
                    label = "This month",
                    selected = preset == RangePreset.THIS_MONTH,
                    onClick = {
                        preset = RangePreset.THIS_MONTH
                        if (!showFilter) {
                            draftFromDate = null
                            draftToDate = null
                            fromDate = null
                            toDate = null
                        }
                    }
                )
                FilterChipButton(
                    label = "Filter",
                    selected = showFilter,
                    onClick = {
                        draftFromDate = fromDate
                        draftToDate = toDate
                        draftSelectedSourceId = selectedSourceId
                        showFilter = !showFilter
                    }
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester),
                label = { Text("Search merchant, source, or category") },
                singleLine = true,
            )
        }

        if (showFilter) {
            item {
                DateRangeFilterCard(
                    sources = sources,
                    fromDate = draftFromDate,
                    toDate = draftToDate,
                    selectedSourceId = draftSelectedSourceId,
                    categoryOptions = categories.map { it.name },
                    selectedCategory = draftSelectedCategory,
                    onCategoryClick = { categoryMenuOpen = true },
                    onCategorySelected = {
                        draftSelectedCategory = it
                        categoryMenuOpen = false
                    },
                    categoryMenuOpen = categoryMenuOpen,
                    minAmountEdit = minAmountEdit,
                    maxAmountEdit = maxAmountEdit,
                    onMinAmountChange = { minAmountEdit = it },
                    onMaxAmountChange = { maxAmountEdit = it },
                    hideTransfersAndDuplicates = hideTransfersAndDuplicates,
                    onToggleHideTransfers = { hideTransfersAndDuplicates = it },
                    onSourceClick = { sourceMenuOpen = true },
                    onSourceSelected = {
                        draftSelectedSourceId = it
                        sourceMenuOpen = false
                    },
                    sourceMenuOpen = sourceMenuOpen,
                    directionFilter = directionFilter,
                    onDirectionSelected = { directionFilter = it },
                    onPickFrom = { pickDate(context, draftFromDate) { draftFromDate = it } },
                    onPickTo = { pickDate(context, draftToDate) { draftToDate = it } },
                    onFind = {
                        fromDate = draftFromDate
                        toDate = draftToDate
                        selectedSourceId = draftSelectedSourceId
                        selectedCategory = draftSelectedCategory
                        appliedMinAmountMinor = minAmountEdit.toDoubleOrNull()?.let { (it * 100).toLong() }
                        appliedMaxAmountMinor = maxAmountEdit.toDoubleOrNull()?.let { (it * 100).toLong() }
                        notify("Filters applied.")
                    },
                    onClear = {
                        draftFromDate = null
                        draftToDate = null
                        fromDate = null
                        toDate = null
                        draftSelectedSourceId = null
                        selectedSourceId = null
                        draftSelectedCategory = null
                        selectedCategory = null
                        minAmountEdit = ""
                        maxAmountEdit = ""
                        appliedMinAmountMinor = null
                        appliedMaxAmountMinor = null
                        directionFilter = DirectionFilter.ALL
                        hideTransfersAndDuplicates = true
                        notify("Filters cleared.")
                    }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TotalsCard(
                    title = "Monthly Spend",
                    value = "-${MoneyFormat.format("PKR", spentTotal)}",
                    fg = Bad,
                    bg = BadBg,
                    modifier = Modifier.weight(1f)
                )
                TotalsCard(
                    title = "Monthly Income",
                    value = "+${MoneyFormat.format("PKR", incomeTotal)}",
                    fg = Good,
                    bg = GoodBg,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                "Range: ${effectiveFrom.formatUi()} → ${effectiveTo.formatUi()}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (groupedTxs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Border)
                ) {
                    Text(
                        "No transactions found for the selected filters.",
                        modifier = Modifier.padding(16.dp),
                        color = TextSecondary
                    )
                }
            }
        } else {
            groupedTxs.forEach { (date, itemsForDate) ->
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
                            (if (dayNet >= 0) "+" else "-") + MoneyFormat.format(dayCurrency, kotlin.math.abs(dayNet)),
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
                                TransactionRow(
                                    tx = t,
                                    sourceLabel = sourceMap[t.sourceId]?.name ?: t.sourceId,
                                    onEdit = {
                                        if (t.externalId != null && !t.rawMessage.isNullOrBlank()) {
                                            viewingTx = t
                                        } else {
                                            editingTx = t
                                        }
                                    }
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

    editingTx?.let { tx ->
        ModalBottomSheet(
            onDismissRequest = { editingTx = null },
            sheetState = editSheetState,
            containerColor = Surface,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            ManualExpenseScreen(
                container = container,
                existingTransaction = tx,
                onSaved = { notify("Transaction updated.") },
                onDeleted = {
                    scope.launch {
                        // Remember the deletion: the source SMS is still in the inbox and would
                        // otherwise be re-imported on the next refresh.
                        tx.rawMessage?.takeIf { it.isNotBlank() }?.let {
                            container.ignoredImportRepository.ignore(it, reason = "deleted")
                        }
                        val removed = container.transactionRepository.deleteById(tx.id)
                        notify(if (removed) "Transaction deleted. It will not come back on refresh." else "Already deleted.")
                        editingTx = null
                    }
                },
                onDone = { editingTx = null }
            )
        }
    }

    viewingTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { viewingTx = null },
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = Primary) },
            title = { Text("Transaction details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tx.merchantRaw, fontWeight = FontWeight.SemiBold)
                    Text("Amount: ${MoneyFormat.format(tx.currency, tx.amountMinor)}")
                    Text("Direction: ${if (tx.direction == TransactionDirection.IN) "Income" else "Spending"}")
                    Text("Category: ${tx.categoryId ?: CategoryRules.detect(tx)}")
                    Text("Source: ${sourceMap[tx.sourceId]?.name ?: tx.sourceId}")
                    Text(
                        "Received: ${
                            Instant.ofEpochMilli(tx.timestampMillis)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                        }"
                    )
                    tx.rawMessage?.let {
                        Text("SMS", fontWeight = FontWeight.SemiBold)
                        Text(it, color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingTx = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun DateRangeFilterCard(
    sources: List<com.trae.expensetracker.data.model.DataSourceEntity>,
    fromDate: LocalDate?,
    toDate: LocalDate?,
    selectedSourceId: String?,
    categoryOptions: List<String>,
    selectedCategory: String?,
    onCategoryClick: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    categoryMenuOpen: Boolean,
    minAmountEdit: String,
    maxAmountEdit: String,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    hideTransfersAndDuplicates: Boolean,
    onToggleHideTransfers: (Boolean) -> Unit,
    onSourceClick: () -> Unit,
    onSourceSelected: (String?) -> Unit,
    sourceMenuOpen: Boolean,
    directionFilter: DirectionFilter,
    onDirectionSelected: (DirectionFilter) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onFind: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChipButton(
                    label = "All",
                    selected = directionFilter == DirectionFilter.ALL,
                    onClick = { onDirectionSelected(DirectionFilter.ALL) }
                )
                FilterChipButton(
                    label = "Spending",
                    selected = directionFilter == DirectionFilter.SPENDING,
                    onClick = { onDirectionSelected(DirectionFilter.SPENDING) }
                )
                FilterChipButton(
                    label = "Income",
                    selected = directionFilter == DirectionFilter.INCOME,
                    onClick = { onDirectionSelected(DirectionFilter.INCOME) }
                )
            }
            OutlinedButton(
                onClick = onSourceClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    sources.firstOrNull { it.id == selectedSourceId }?.name ?: "All sources"
                )
            }
            DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { onSourceSelected(selectedSourceId) }) {
                DropdownMenuItem(
                    text = { Text("All sources") },
                    onClick = { onSourceSelected(null) }
                )
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.name) },
                        onClick = { onSourceSelected(source.id) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hideTransfersAndDuplicates,
                    onCheckedChange = onToggleHideTransfers
                )
                Text("Hide transfers, duplicates and refunds", color = TextSecondary)
            }
            OutlinedButton(
                onClick = onCategoryClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(selectedCategory ?: "All categories")
            }
            DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { onCategorySelected(selectedCategory) }) {
                DropdownMenuItem(
                    text = { Text("All categories") },
                    onClick = { onCategorySelected(null) }
                )
                categoryOptions.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onCategorySelected(name) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = minAmountEdit,
                    onValueChange = { onMinAmountChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                    label = { Text("Min amount") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = maxAmountEdit,
                    onValueChange = { onMaxAmountChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                    label = { Text("Max amount") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPickFrom,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(fromDate?.formatUi() ?: "From") }
                OutlinedButton(
                    onClick = onPickTo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(toDate?.formatUi() ?: "To") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onFind,
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Apply") }
                TextButton(onClick = onClear) { Text("Clear all", color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    tx: TransactionEntity,
    sourceLabel: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
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
                categoryIcon(CategoryRules.detect(tx)),
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
            (if (tx.direction == TransactionDirection.IN) "+" else "-") + MoneyFormat.format(tx.currency, tx.amountMinor),
            fontWeight = FontWeight.Bold,
            color = if (tx.direction == TransactionDirection.IN) Good else Bad,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) PrimaryContainer else Surface,
            labelColor = if (selected) Primary else MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun TotalsCard(
    title: String,
    value: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = fg, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                color = fg,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun categoryIcon(category: String): ImageVector = when {
    category.contains("food", ignoreCase = true) || category.contains("dining", ignoreCase = true) -> Icons.Outlined.Restaurant
    category.contains("shop", ignoreCase = true) -> Icons.Outlined.ShoppingBag
    category.contains("transport", ignoreCase = true) || category.contains("travel", ignoreCase = true) -> Icons.Outlined.LocalTaxi
    else -> Icons.Outlined.Payments
}

private fun pickDate(context: android.content.Context, initial: LocalDate?, onPicked: (LocalDate) -> Unit) {
    val start = initial ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(LocalDate.of(year, month + 1, dayOfMonth)) },
        start.year,
        start.monthValue - 1,
        start.dayOfMonth
    ).show()
}

private fun LocalDate.formatUi(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun LocalDate.formatHeader(): String {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    return when (this) {
        today -> "TODAY, ${format(DateTimeFormatter.ofPattern("MMM d"))}".uppercase()
        yesterday -> "YESTERDAY, ${format(DateTimeFormatter.ofPattern("MMM d"))}".uppercase()
        else -> format(DateTimeFormatter.ofPattern("MMM d")).uppercase()
    }
}
