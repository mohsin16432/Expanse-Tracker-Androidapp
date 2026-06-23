package com.trae.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.PendingImportEntity
import com.trae.expensetracker.data.model.PendingImportStatus
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.ingest.ApprovedSmsFormat
import com.trae.expensetracker.ingest.TransactionDraft
import com.trae.expensetracker.ui.theme.Surface
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun ReviewScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val pending by container.pendingImportRepository.observeNeedsReview().collectAsState(initial = emptyList())
    val count by container.pendingImportRepository.observeNeedsReviewCount().collectAsState(initial = 0)
    val sources by container.dataSourceRepository.observeAll().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    var selectedItem by remember { mutableStateOf<PendingImportEntity?>(null) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (count == 0) {
                "Only new transaction-like SMS formats appear here."
            } else {
                "$count items need review. Approve one and the app can auto-handle the same format next time."
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pending) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
                    Column(
                        modifier = Modifier
                            .clickable { selectedItem = item }
                            .padding(14.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.kind.uppercase(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(item.sender.orEmpty(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(item.rawText.take(220))
                        Spacer(Modifier.height(8.dp))
                        val draft = item.draftTransactionJson?.let { runCatching { json.decodeFromString(TransactionDraft.serializer(), it) }.getOrNull() }
                        Text(
                            draft?.let { "Tap to review ${it.direction} ${it.currency} ${it.amountMinor / 100.0}" }
                                ?: "Tap to review or discard.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        val draft = item.draftTransactionJson?.let { runCatching { json.decodeFromString(TransactionDraft.serializer(), it) }.getOrNull() }
        ReviewItemDialog(
            item = item,
            draft = draft,
            sources = sources,
            categoryOptions = categories.map { it.name },
            onDismiss = { selectedItem = null },
            onDiscard = {
                scope.launch {
                    container.pendingImportRepository.upsert(item.copy(status = PendingImportStatus.DISCARDED))
                    selectedItem = null
                }
            },
            onSave = { tx ->
                scope.launch {
                    val cat = tx.categoryId?.trim().orEmpty()
                    if (cat.isNotBlank() && categories.none { it.name.equals(cat, ignoreCase = true) }) {
                        container.categoryRepository.upsert(
                            com.trae.expensetracker.data.model.CategoryEntity(
                                id = cat,
                                name = cat,
                                colorArgb = 0xFF6B5DFF,
                                sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                            )
                        )
                    }
                    container.transactionRepository.upsert(tx)
                    if (draft != null && !item.sender.isNullOrBlank()) {
                        container.settingsRepository.addApprovedSmsFormat(
                            ApprovedSmsFormat.fromApprovedDraft(
                                sender = item.sender,
                                rawText = item.rawText,
                                draft = draft,
                            )
                        )
                    }
                    container.pendingImportRepository.upsert(item.copy(status = PendingImportStatus.SAVED))
                    selectedItem = null
                }
            }
        )
    }
}

@Composable
private fun ReviewItemDialog(
    item: PendingImportEntity,
    draft: TransactionDraft?,
    sources: List<com.trae.expensetracker.data.model.DataSourceEntity>,
    categoryOptions: List<String>,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (TransactionEntity) -> Unit,
) {
    val guessedSource = remember(draft, sources) {
        if (draft == null) return@remember ""
        val type = draft.sourceTypeHint
        val shortCode = draft.sourceShortCode.orEmpty()
        sources.firstOrNull {
            it.shortCode == shortCode &&
                (type == null || it.type == type) &&
                (it.type != com.trae.expensetracker.data.model.DataSourceType.CREDIT_CARD || it.cardLast4 == draft.cardLast4Hint)
        }?.id ?: ""
    }
    var merchant by remember(item.id) { mutableStateOf(draft?.merchantRaw.orEmpty()) }
    var amountText by remember(item.id) { mutableStateOf(draft?.amountMinor?.let { "%.2f".format(it / 100.0) }.orEmpty()) }
    var currency by remember(item.id) { mutableStateOf(draft?.currency ?: "PKR") }
    var category by remember(item.id) { mutableStateOf("") }
    var selectedSourceId by remember(item.id) { mutableStateOf(guessedSource) }
    var sourceMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.rawText.take(400), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                if (draft == null) {
                    Text("No structured draft was created. You can still save this item manually.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant") })
                OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Amount") })
                OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase().take(3) }, label = { Text("Currency") })
                Button(onClick = { sourceMenu = true }) {
                    Text(sources.firstOrNull { it.id == selectedSourceId }?.name ?: if (selectedSourceId.isBlank()) "Choose source" else selectedSourceId)
                }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    sources.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s.name} (${s.shortCode})") },
                            onClick = {
                                selectedSourceId = s.id
                                sourceMenu = false
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { categoryMenu = true }) { Text("Pick") }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categoryOptions.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    category = c
                                    categoryMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = merchant.isNotBlank() && amountText.isNotBlank() && selectedSourceId.isNotBlank(),
                onClick = {
                    val amountMinor = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    val tx = TransactionEntity(
                        id = draft?.externalId ?: item.id,
                        timestampMillis = draft?.timestampMillis ?: item.createdAtMillis,
                        sourceId = selectedSourceId,
                        type = draft?.type ?: com.trae.expensetracker.data.model.TransactionType.ADJUSTMENT,
                        direction = draft?.direction ?: com.trae.expensetracker.data.model.TransactionDirection.OUT,
                        amountMinor = kotlin.math.abs(amountMinor),
                        currency = currency.ifBlank { "PKR" },
                        merchantRaw = merchant.trim(),
                        merchantNormalized = merchant.trim().lowercase(),
                        categoryId = category.trim().ifBlank { null },
                        reference = draft?.reference,
                        externalId = draft?.externalId,
                        rawMessage = item.rawText,
                    )
                    onSave(tx)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
