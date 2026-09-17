package com.trae.expensetracker.ui.screens

import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.PendingImportEntity
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.ingest.ApprovedSmsFormat
import com.trae.expensetracker.ingest.TransactionDraft
import com.trae.expensetracker.ui.MoneyFormat
import com.trae.expensetracker.ui.theme.Surface
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val pending by container.pendingImportRepository.observeNeedsReview().collectAsState(initial = emptyList())
    val count by container.pendingImportRepository.observeNeedsReviewCount().collectAsState(initial = 0)
    val sources by container.dataSourceRepository.observeAll().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    var selectedItem by remember { mutableStateOf<PendingImportEntity?>(null) }

    // Duplicates are stored in the transactions table (not the pending queue), so they need
    // their own feed. Without this the dashboard could report a duplicate that was invisible here.
    val flaggedDuplicates by container.transactionRepository.observeFlaggedDuplicates()
        .collectAsState(initial = emptyList())
    val flaggedRefunds by container.transactionRepository.observeFlaggedRefunds()
        .collectAsState(initial = emptyList())
    val sourceById = remember(sources) { sources.associateBy { it.id } }

    // Resolve the original each duplicate was matched against, for a useful side-by-side.
    var originalsById by remember { mutableStateOf<Map<String, TransactionEntity>>(emptyMap()) }
    LaunchedEffect(flaggedDuplicates) {
        val missing = flaggedDuplicates
            .mapNotNull { it.duplicateOfId }
            .distinct()
            .filter { originalsById[it] == null }
        if (missing.isNotEmpty()) {
            val loaded = missing.mapNotNull { id -> container.transactionRepository.findById(id)?.let { id to it } }
            if (loaded.isNotEmpty()) originalsById = originalsById + loaded
        }
    }

    val totalReviewCount = count + flaggedDuplicates.size + flaggedRefunds.size
    val context = LocalContext.current

    fun notify(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            when {
                totalReviewCount == 0 -> "Nothing needs review right now."
                else -> "$totalReviewCount item${if (totalReviewCount == 1) "" else "s"} need review."
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (flaggedDuplicates.isNotEmpty()) {
                item {
                    Text(
                        "Possible duplicates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "These were captured twice (for example from SMS and a notification). They are already excluded from your totals.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                items(flaggedDuplicates, key = { it.id }) { dupe ->
                    val original = dupe.duplicateOfId?.let { originalsById[it] }
                    Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("DUPLICATE", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    MoneyFormat.format(dupe.currency, dupe.amountMinor),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(dupe.merchantRaw, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${sourceById[dupe.sourceId]?.name ?: dupe.sourceId} • ${formatReviewDate(dupe.timestampMillis)}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (original != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Matches ${original.merchantRaw} • ${sourceById[original.sourceId]?.name ?: original.sourceId} • ${formatReviewDate(original.timestampMillis)}",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Keep: clear the flag so it counts as a real transaction again.
                                TextButton(onClick = {
                                    scope.launch {
                                        container.transactionRepository.setDuplicateOf(dupe.id, null)
                                        notify("Kept as a separate transaction.")
                                    }
                                }) { Text("Keep") }

                                // Delete: drop the row and remember it, because the source SMS is
                                // still in the inbox and the next Refresh would re-import it.
                                TextButton(onClick = {
                                    scope.launch {
                                        // Tombstone first: if the delete below fails we have still
                                        // prevented a re-import, and the row stays visible to retry.
                                        dupe.rawMessage?.takeIf { it.isNotBlank() }?.let {
                                            container.ignoredImportRepository.ignore(it, reason = "deleted")
                                        }
                                        val removed = container.transactionRepository.deleteById(dupe.id)
                                        notify(
                                            if (removed) "Deleted. It will not come back on refresh."
                                            else "Already deleted."
                                        )
                                    }
                                }) { Text("Delete") }

                                // Not a duplicate: unflag and remember, so the same pair of sources
                                // is never flagged again.
                                TextButton(
                                    enabled = original != null,
                                    onClick = {
                                        val orig = original
                                        scope.launch {
                                            container.transactionRepository.setDuplicateOf(dupe.id, null)
                                            if (orig != null) {
                                                container.merchantRuleRepository
                                                    .suppressDuplicate(dupe.sourceId, orig.sourceId)
                                                // Clear other rows flagged for this same source pair.
                                                container.transactionRepository
                                                    .clearDuplicateFlagsForPair(orig.id, dupe.sourceId)
                                                container.smsIngestor.invalidateMerchantRuleCache()
                                            }
                                            notify("These two are never matched again.")
                                        }
                                    }
                                ) { Text("Not a duplicate") }
                            }
                        }
                    }
                }
                if (pending.isNotEmpty() || flaggedRefunds.isNotEmpty()) {
                    item {
                        Text(
                            "Pending imports",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (flaggedRefunds.isNotEmpty()) {
                item {
                    Text(
                        "Refunds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "These credits look like reversals of an earlier charge, so they reduce your spending instead of counting as income.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                items(flaggedRefunds, key = { it.id }) { refund ->
                    val originalCharge = refund.refundOfId?.let { originalsById[it] }
                    Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("REFUND", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    MoneyFormat.format(refund.currency, refund.amountMinor),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(refund.merchantRaw, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${sourceById[refund.sourceId]?.name ?: refund.sourceId} • ${formatReviewDate(refund.timestampMillis)}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (originalCharge != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Reverses ${originalCharge.merchantRaw} • ${formatReviewDate(originalCharge.timestampMillis)}",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Not a refund: count it as a normal credit again.
                                TextButton(onClick = {
                                    scope.launch {
                                        container.transactionRepository.setRefundOf(refund.id, null)
                                        notify("Treated as a normal credit.")
                                    }
                                }) { Text("Not a refund") }

                                // Confirm: keep it linked to the charge so it reduces spending.
                                TextButton(onClick = {
                                    scope.launch {
                                        notify("Kept as a refund.")
                                    }
                                }) { Text("Keep as refund") }
                            }
                        }
                    }
                }
                if (pending.isNotEmpty()) {
                    item {
                        Text(
                            "Pending imports",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
                    // Remember the deletion, otherwise the next import re-queues the same message.
                    container.ignoredImportRepository.ignore(item.rawText, reason = "discarded")
                    container.pendingImportRepository.remove(item.id)
                    selectedItem = null
                    notify("Discarded. It will not come back on refresh.")
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
                    // A manually approved transfer leg should still be paired with its other leg.
                    container.transferPairer.tryPair(tx)

                    // Learn from the user's correction so the same merchant is categorised
                    // automatically on every future import.
                    val correctedCategory = tx.categoryId?.trim().orEmpty()
                    if (correctedCategory.isNotBlank()) {
                        container.merchantRuleRepository.learnCategory(tx.merchantRaw, correctedCategory)
                    }
                    val originalMerchant = draft?.merchantRaw?.trim().orEmpty()
                    if (originalMerchant.isNotBlank() &&
                        !originalMerchant.equals(tx.merchantRaw.trim(), ignoreCase = true)
                    ) {
                        container.merchantRuleRepository.learnAlias(originalMerchant, tx.merchantRaw.trim())
                    }
                    container.smsIngestor.invalidateMerchantRuleCache()

                    if (draft != null && !item.sender.isNullOrBlank()) {
                        container.settingsRepository.addApprovedSmsFormat(
                            ApprovedSmsFormat.fromApprovedDraft(
                                sender = item.sender,
                                rawText = item.rawText,
                                draft = draft,
                            )
                        )
                    }
                    // The transaction is saved; the queue entry has served its purpose.
                    container.pendingImportRepository.remove(item.id)
                    selectedItem = null
                }
            }
        )
    }
}

/** Compact timestamp for review rows, e.g. "12 Sep, 16:06". */
private fun formatReviewDate(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

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
