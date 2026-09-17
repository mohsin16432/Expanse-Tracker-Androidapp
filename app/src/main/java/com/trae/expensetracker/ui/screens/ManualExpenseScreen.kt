package com.trae.expensetracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionEntity
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.ocr.ReceiptOcrResult
import com.trae.expensetracker.ui.CategoryRules
import com.trae.expensetracker.ui.theme.Border
import com.trae.expensetracker.ui.theme.Bad
import com.trae.expensetracker.ui.theme.Good
import com.trae.expensetracker.ui.theme.Primary
import com.trae.expensetracker.ui.theme.PrimaryContainer
import com.trae.expensetracker.ui.theme.Surface
import com.trae.expensetracker.ui.theme.Surface2
import com.trae.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun ManualExpenseScreen(
    container: AppContainer,
    existingTransaction: TransactionEntity? = null,
    onSaved: (() -> Unit)? = null,
    onDeleted: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sources by container.dataSourceRepository.observeAll().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    val availableSources = remember(sources) {
        sources.sortedWith(compareBy<com.trae.expensetracker.data.model.DataSourceEntity> {
            if (it.type == DataSourceType.CASH) 0 else 1
        }.thenBy { it.name.lowercase() })
    }
    val quickCategories = remember(categories) {
        val preferred = listOf("Dining", "Food", "Shopping", "Travel", "Transport", "Health")
        val existing = categories.map { it.name }
        (preferred + existing).distinct().take(4)
    }

    var merchant by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.merchantRaw.orEmpty()) }
    var amount by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.let { "%.2f".format(it.amountMinor / 100.0) }.orEmpty()) }
    var currency by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.currency ?: "PKR") }
    var selectedSourceId by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.sourceId.orEmpty()) }
    var direction by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.direction ?: TransactionDirection.OUT) }
    var selectedCategory by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.categoryId ?: existingTransaction?.let { CategoryRules.detect(it) }) }
    var ocrStatus by remember(existingTransaction?.id) {
        mutableStateOf(
            if (existingTransaction == null) "Use Cash by default, or choose another source."
            else "Update the transaction details below."
        )
    }
    var receiptResult by remember(existingTransaction?.id) { mutableStateOf<ReceiptOcrResult?>(null) }
    var candidateMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                applyReceiptScan(container, uri) { status, parsed ->
                    ocrStatus = status
                    if (parsed != null) {
                        receiptResult = parsed
                        merchant = parsed.merchantHint?.takeIf { it.isNotBlank() } ?: merchant
                        amount = parsed.amountMinorHint?.let { "%.2f".format(it / 100.0) } ?: amount
                        currency = parsed.currencyHint ?: currency
                    }
                }
            }
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                applyReceiptScan(container, uri, fromCamera = true) { status, parsed ->
                    ocrStatus = status
                    if (parsed != null) {
                        receiptResult = parsed
                        merchant = parsed.merchantHint?.takeIf { it.isNotBlank() } ?: merchant
                        amount = parsed.amountMinorHint?.let { "%.2f".format(it / 100.0) } ?: amount
                        currency = parsed.currencyHint ?: currency
                    }
                }
            }
        } else {
            ocrStatus = "Camera capture cancelled."
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCameraCapture(context) { uri ->
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
            }
        } else {
            ocrStatus = "Camera permission denied."
        }
    }

    LaunchedEffect(availableSources) {
        if (selectedSourceId.isBlank()) {
            selectedSourceId = availableSources.firstOrNull { it.type == DataSourceType.CASH }?.id
                ?: availableSources.firstOrNull()?.id.orEmpty()
        }
    }

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
            OutlinedButton(
                onClick = onDone,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Border),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                if (existingTransaction == null) "Add transaction" else "Edit transaction",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (existingTransaction != null && onDeleted != null) {
                TextButton(onClick = onDeleted) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Bad)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = Bad)
                }
            } else {
                Spacer(Modifier.width(64.dp))
            }
        }

        Text(ocrStatus, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)

        Text(
            "$currency   ${amount.ifBlank { "0.00" }}",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )

        SegmentedDirectionSwitcher(
            direction = direction,
            onSelect = { direction = it }
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Merchant / Note", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = TextSecondary) },
                placeholder = { Text("Where did you spend?") },
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        if (!receiptResult?.merchantCandidates.isNullOrEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { candidateMenu = true }) { Text("Pick detected merchant") }
                DropdownMenu(expanded = candidateMenu, onDismissRequest = { candidateMenu = false }) {
                    receiptResult?.merchantCandidates?.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate) },
                            onClick = {
                                merchant = candidate
                                candidateMenu = false
                            }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase().take(3) },
                label = { Text("Currency") },
                modifier = Modifier.width(110.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Source", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                availableSources.forEach { source ->
                    SourceChip(
                        label = source.name,
                        icon = sourceIcon(source.type),
                        selected = selectedSourceId == source.id,
                        onClick = { selectedSourceId = source.id }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Category", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { categoryMenu = true }) { Text("See all") }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategory = category.name
                                categoryMenu = false
                            }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                quickCategories.forEach { category ->
                    CategoryTile(
                        label = category,
                        icon = categoryIcon(category),
                        selected = selectedCategory == category,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedCategory = category }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.weight(1f).height(56.dp),
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp),
                onClick = {
                    val granted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        launchCameraCapture(context) { uri ->
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        }
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Take Picture")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f).height(56.dp),
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp),
                onClick = { imagePicker.launch("image/*") }
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Attach Receipt")
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            enabled = merchant.isNotBlank() && amount.isNotBlank() && selectedSourceId.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Surface),
            shape = RoundedCornerShape(8.dp),
            onClick = {
                val amountMinor = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
                val entity = TransactionEntity(
                    id = existingTransaction?.id ?: UUID.randomUUID().toString(),
                    timestampMillis = existingTransaction?.timestampMillis ?: System.currentTimeMillis(),
                    sourceId = selectedSourceId,
                    type = if (direction == TransactionDirection.OUT) TransactionType.DEBIT_PURCHASE else TransactionType.CREDIT_RECEIVED,
                    direction = direction,
                    amountMinor = kotlin.math.abs(amountMinor),
                    currency = currency.ifBlank { "PKR" },
                    merchantRaw = merchant.trim(),
                    merchantNormalized = merchant.trim().lowercase(),
                    categoryId = selectedCategory?.trim()?.ifBlank { null },
                    reference = null,
                    externalId = null,
                    rawMessage = receiptResult?.rawText,
                    // Preserve classification flags when editing an existing row, otherwise the
                    // transfer / duplicate / refund markers would be silently wiped.
                    transferGroupId = existingTransaction?.transferGroupId,
                    duplicateOfId = existingTransaction?.duplicateOfId,
                    refundOfId = existingTransaction?.refundOfId,
                )
                scope.launch {
                    if (existingTransaction == null) {
                        container.transactionRepository.insertIgnore(entity)
                    } else {
                        container.transactionRepository.upsert(entity)
                    }
                    merchant = ""
                    amount = ""
                    selectedCategory = null
                    receiptResult = null
                    onSaved?.invoke()
                    onDone()
                }
            }
        ) {
            Text(
                if (existingTransaction == null) "Save Transaction" else "Update Transaction",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SegmentedDirectionSwitcher(
    direction: TransactionDirection,
    onSelect: (TransactionDirection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface2, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentButton("Expense", direction == TransactionDirection.OUT, Modifier.weight(1f)) { onSelect(TransactionDirection.OUT) }
        SegmentButton("Income", direction == TransactionDirection.IN, Modifier.weight(1f)) { onSelect(TransactionDirection.IN) }
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier.height(48.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Surface else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 1.dp else 0.dp)
    ) { Text(label) }
}

@Composable
private fun SourceChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (selected) PrimaryContainer else Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) PrimaryContainer else Surface,
            contentColor = if (selected) Primary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun CategoryTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(if (selected) Primary else Surface2, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Surface else TextSecondary)
        }
        Text(label, color = if (selected) MaterialTheme.colorScheme.onSurface else TextSecondary)
    }
}

private fun sourceIcon(type: DataSourceType): ImageVector = when (type) {
    DataSourceType.CASH -> Icons.Outlined.AccountBalanceWallet
    DataSourceType.BANK -> Icons.Outlined.AccountBalance
    DataSourceType.WALLET -> Icons.Outlined.Payments
    DataSourceType.CREDIT_CARD -> Icons.Outlined.CreditCard
    DataSourceType.NOTIFICATION -> Icons.Outlined.Notifications
}

private fun categoryIcon(category: String): ImageVector = when {
    category.contains("food", ignoreCase = true) || category.contains("dining", ignoreCase = true) -> Icons.Outlined.Restaurant
    category.contains("shop", ignoreCase = true) -> Icons.Outlined.LocalMall
    category.contains("travel", ignoreCase = true) || category.contains("transport", ignoreCase = true) -> Icons.Outlined.LocalTaxi
    category.contains("health", ignoreCase = true) -> Icons.Outlined.FitnessCenter
    else -> Icons.Outlined.Payments
}

private fun launchCameraCapture(
    context: android.content.Context,
    onReady: (Uri) -> Unit,
) {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("receipt_", ".jpg", dir)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    onReady(uri)
}

private suspend fun applyReceiptScan(
    container: AppContainer,
    uri: Uri,
    fromCamera: Boolean = false,
    apply: (status: String, parsed: ReceiptOcrResult?) -> Unit,
) {
    apply(if (fromCamera) "Scanning camera image..." else "Scanning receipt...", null)
    val result = container.receiptOcrService.scanReceipt(uri)
    result.onSuccess { parsed ->
        apply(
            if (fromCamera) "Camera receipt scan completed. Review and save." else "Receipt scan completed. Review and save.",
            parsed
        )
    }.onFailure {
        apply(
            if (fromCamera) "Camera receipt scan failed: ${it.message ?: it}" else "Receipt scan failed: ${it.message ?: it}",
            null
        )
    }
}
