package com.trae.expensetracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.UserSmsRegexFormat
import com.trae.expensetracker.ingest.UserSmsRegexParser
import com.trae.expensetracker.ingest.parsers.FblParser
import com.trae.expensetracker.ingest.parsers.HblParser
import com.trae.expensetracker.ingest.parsers.JazzCashParser
import com.trae.expensetracker.ui.theme.Border
import com.trae.expensetracker.ui.theme.Surface
import com.trae.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val baseUrl by container.settingsRepository.llmBaseUrl().collectAsState(initial = "https://api.poe.com/v1")
    val model by container.settingsRepository.llmModel().collectAsState(initial = "Claude-Sonnet-4.6")
    val cutoffDay by container.settingsRepository.statementCutoffDay().collectAsState(initial = 10)
    val cycleStartDay by container.settingsRepository.budgetCycleStartDay().collectAsState(initial = 1)
    val sources by container.dataSourceRepository.observeAll().collectAsState(initial = emptyList())
    val existingApiKey = remember { container.settingsRepository.llmApiKey().orEmpty() }
    val builtInParsers = remember { listOf(HblParser(), FblParser(), JazzCashParser()) }

    var baseUrlEdit by remember(baseUrl) { mutableStateOf(baseUrl) }
    var modelEdit by remember(model) { mutableStateOf(model) }
    var apiKeyEdit by remember { mutableStateOf(existingApiKey) }
    var cutoffEdit by remember(cutoffDay) { mutableStateOf(cutoffDay.toString()) }
    var cycleStartEdit by remember(cycleStartDay) { mutableStateOf(cycleStartDay.toString()) }
    var status by remember { mutableStateOf("Settings are ready.") }
    var isImporting by remember { mutableStateOf(false) }

    var editingSourceId by remember { mutableStateOf<String?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var senderShortCode by remember { mutableStateOf("") }
    var cardLast4 by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf(DataSourceType.BANK) }
    var typeMenuOpen by remember { mutableStateOf(false) }

    var backupExpanded by remember { mutableStateOf(false) }
    var budgetExpanded by remember { mutableStateOf(false) }
    var smsExpanded by remember { mutableStateOf(false) }
    var sourcesExpanded by remember { mutableStateOf(false) }
    var llmExpanded by remember { mutableStateOf(false) }
    var cardCycleExpanded by remember { mutableStateOf(false) }
    var smsFormatExpanded by remember { mutableStateOf(false) }

    var fmtSender by remember { mutableStateOf("") }
    var fmtRegex by remember { mutableStateOf("") }
    var fmtAmountGroup by remember { mutableStateOf("1") }
    var fmtMerchantGroup by remember { mutableStateOf("2") }
    var fmtDateGroup by remember { mutableStateOf("") }
    var fmtTimeGroup by remember { mutableStateOf("") }
    var fmtDatePattern by remember { mutableStateOf("dd/MM/yyyy") }
    var fmtTimePattern by remember { mutableStateOf("HH:mm:ss") }
    var fmtReferenceGroup by remember { mutableStateOf("") }
    var fmtCurrencyOverride by remember { mutableStateOf("") }
    var fmtTestSms by remember { mutableStateOf("") }
    var fmtTestResult by remember { mutableStateOf<String?>(null) }
    var isGeneratingSmsFormat by remember { mutableStateOf(false) }

    var fmtTypeMenu by remember { mutableStateOf(false) }

    fun notify(msg: String) {
        status = msg
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun buildUserFormatOrThrow(spec: com.trae.expensetracker.llm.SmsRegexGenerationResult): UserSmsRegexFormat {
        Regex(spec.regex)
        require(spec.amountGroup > 0) { "Amount group must be a positive number." }
        if ((spec.dateGroup != null || spec.timeGroup != null) && (spec.datePattern.isNullOrBlank() || spec.timePattern.isNullOrBlank())) {
            throw IllegalArgumentException("Generated format is missing date/time patterns.")
        }
        return UserSmsRegexFormat(
            sender = fmtSender.trim(),
            sourceShortCode = fmtSender.trim(),
            sourceNameHint = spec.sourceNameHint.trim().ifBlank { "Source ${fmtSender.trim()}" },
            sourceTypeHint = spec.sourceTypeHint,
            type = spec.type,
            direction = spec.direction,
            regex = spec.regex,
            amountGroup = spec.amountGroup,
            merchantGroup = spec.merchantGroup,
            dateGroup = spec.dateGroup,
            timeGroup = spec.timeGroup,
            datePattern = spec.datePattern?.trim(),
            timePattern = spec.timePattern?.trim(),
            referenceGroup = spec.referenceGroup,
            currencyOverride = spec.currencyOverride?.trim()?.ifBlank { null },
        )
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = container.backupService.exportBackupToUri(uri)
                notify(if (ok) "Backup saved to selected location." else "Backup failed.")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = container.backupService.restoreFromUri(uri)
                notify(if (ok) "Restore completed." else "Restore failed.")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(status, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }

        item {
            CollapsibleSettingsCard(
                title = "Backup & restore",
                subtitle = "Save or restore a JSON backup",
                expanded = backupExpanded,
                onToggle = { backupExpanded = !backupExpanded }
            ) {
                    Text("Backup & restore", fontWeight = FontWeight.Bold)
                    Text("Choose Google Drive in the file picker to store or restore backups there.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { backupLauncher.launch("expense_tracker_backup.json") }) { Text("Backup to Drive") }
                        Button(onClick = { restoreLauncher.launch(arrayOf("application/json")) }) { Text("Restore from Drive") }
                    }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Budget cycle",
                subtitle = "Cycle starts on day $cycleStartDay",
                expanded = budgetExpanded,
                onToggle = { budgetExpanded = !budgetExpanded }
            ) {
                    Text("Budget cycle", fontWeight = FontWeight.Bold)
                    Text("Your dashboard/transactions 'This month' will follow this cycle start day.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = cycleStartEdit,
                        onValueChange = { cycleStartEdit = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Cycle start day (1-28), e.g. 26") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        val v = cycleStartEdit.toIntOrNull()?.coerceIn(1, 28) ?: 1
                        scope.launch {
                            container.settingsRepository.setBudgetCycleStartDay(v)
                            notify("Budget cycle start day saved.")
                        }
                    }) { Text("Save cycle start") }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "SMS refresh",
                subtitle = "Import latest inbox SMS manually",
                expanded = smsExpanded,
                onToggle = { smsExpanded = !smsExpanded }
            ) {
                    Text("SMS refresh", fontWeight = FontWeight.Bold)
                    Text("Messages import only when you ask for it. Sources may be auto-created for recognized senders.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Button(
                        enabled = !isImporting,
                        onClick = {
                        scope.launch {
                            try {
                                isImporting = true
                                val res = container.smsHistoryImporter.importRecent()
                                notify("Scanned ${res.scanned} SMS, saved ${res.saved} transactions.")
                            } finally {
                                isImporting = false
                            }
                        }
                    }) { Text(if (isImporting) "Importing..." else "Import SMS now") }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Add SMS format",
                subtitle = "Paste one sample SMS and let the app learn it",
                expanded = smsFormatExpanded,
                onToggle = { smsFormatExpanded = !smsFormatExpanded }
            ) {
                Text(
                    "Paste a sample SMS. The app will generate the parsing format in the background, validate it, and save it automatically if it works.",
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = fmtSender,
                    onValueChange = { fmtSender = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Sender short code (e.g., 8558)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = fmtTestSms,
                    onValueChange = { fmtTestSms = it },
                    label = { Text("Sample SMS message") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (fmtTestSms.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    fmtTestSms = ""
                                    fmtTestResult = null
                                }
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = fmtSender.isNotBlank() && fmtTestSms.isNotBlank() && !isGeneratingSmsFormat,
                        onClick = {
                            scope.launch {
                                try {
                                    isGeneratingSmsFormat = true
                                    val sampleMessage = SmsMessage(
                                        sender = fmtSender.trim(),
                                        body = fmtTestSms,
                                        receivedAtMillis = System.currentTimeMillis(),
                                    )
                                    val builtInMatch = builtInParsers.firstNotNullOfOrNull { it.parse(sampleMessage) }
                                    if (builtInMatch != null) {
                                        fmtTestResult = "This SMS is already supported"
                                        notify("This SMS is already supported")
                                        return@launch
                                    }
                                    val spec = container.llmClient.generateSmsRegexSpec(
                                        sampleMessage
                                    ).getOrThrow()
                                    val format = buildUserFormatOrThrow(spec)
                                    val draft = UserSmsRegexParser.parse(
                                        format,
                                        sampleMessage
                                    )
                                    if (draft == null) {
                                        fmtTestResult = "Invalid transactional SMS"
                                        notify("Invalid transactional SMS")
                                    } else {
                                        container.settingsRepository.addUserSmsRegexFormat(format)
                                        container.smsIngestor.invalidateUserFormatsCache()
                                        fmtTestResult = "SMS format added"
                                        notify("SMS format added")
                                    }
                                } catch (t: Throwable) {
                                    fmtTestResult = "Invalid transactional SMS"
                                    notify("Invalid transactional SMS")
                                } finally {
                                    isGeneratingSmsFormat = false
                                }
                            }
                        }
                    ) { Text(if (isGeneratingSmsFormat) "Generating..." else "Generate and add format") }
                }
                fmtTestResult?.let { result ->
                    Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
                        Text(
                            result,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Data sources",
                subtitle = "${sources.size} configured source${if (sources.size == 1) "" else "s"}",
                expanded = sourcesExpanded,
                onToggle = { sourcesExpanded = !sourcesExpanded }
            ) {
                    Text("Data sources", fontWeight = FontWeight.Bold)
                    Text("Edit name, short code, and type. Deleting a source also deletes its transactions.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                    sources.forEach { s ->
                        val isCashSource = s.id == "cash" || s.type == DataSourceType.CASH
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${s.name} (${s.shortCode})", fontWeight = FontWeight.SemiBold)
                                val extra = if (s.type == DataSourceType.CREDIT_CARD) " • ${s.cardLast4 ?: "????"}" else ""
                                Text("${s.type}$extra", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            if (!isCashSource) {
                                Button(onClick = {
                                    editingSourceId = s.id
                                    sourceName = s.name
                                    senderShortCode = s.shortCode
                                    cardLast4 = s.cardLast4.orEmpty()
                                    sourceType = s.type
                                    notify("Editing ${s.name}.")
                                }) { Text("Edit") }
                                Button(onClick = {
                                    scope.launch {
                                        container.transactionRepository.deleteBySourceId(s.id)
                                        container.dataSourceRepository.deleteById(s.id)
                                        if (editingSourceId == s.id) {
                                            editingSourceId = null
                                            sourceName = ""
                                            senderShortCode = ""
                                            cardLast4 = ""
                                            sourceType = DataSourceType.BANK
                                        }
                                        notify("Deleted ${s.name} and its transactions.")
                                    }
                                }) { Text("Delete") }
                            } else {
                                Text("Default", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(if (editingSourceId == null) "Add new source" else "Edit source", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = sourceName, onValueChange = { sourceName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    if (sourceType != DataSourceType.CASH) {
                        OutlinedTextField(
                            value = senderShortCode,
                            onValueChange = { senderShortCode = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Sender short code (e.g., 14250)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    if (sourceType == DataSourceType.CREDIT_CARD) {
                        OutlinedTextField(
                            value = cardLast4,
                            onValueChange = { cardLast4 = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Card last 4 (e.g., 9529)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Text(
                            "This is required to separate card charges from bank account SMS (same sender short code).",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { typeMenuOpen = true }) { Text("Type: ${sourceType.name}") }
                        DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                            DataSourceType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(t.name) }, onClick = {
                                    sourceType = t
                                    if (t == DataSourceType.CREDIT_CARD && cardLast4.isBlank()) {
                                        notify("Enter the last 4 digits so credit card SMS are tracked separately.")
                                    }
                                    typeMenuOpen = false
                                })
                            }
                        }
                        Button(
                            enabled = sourceName.isNotBlank() &&
                                (sourceType == DataSourceType.CASH || senderShortCode.isNotBlank()) &&
                                (sourceType != DataSourceType.CREDIT_CARD || cardLast4.length == 4),
                            onClick = {
                                scope.launch {
                                    val sender = senderShortCode.trim()
                                    val id = editingSourceId ?: when (sourceType) {
                                        DataSourceType.CASH -> "cash"
                                        DataSourceType.BANK -> "${sender}-BANK"
                                        DataSourceType.WALLET -> "${sender}-WALLET"
                                        DataSourceType.CREDIT_CARD -> "${sender}-CC-${cardLast4}"
                                    }
                                    container.dataSourceRepository.upsert(
                                        DataSourceEntity(
                                            id = id,
                                            name = sourceName.trim(),
                                            shortCode = sender,
                                            type = sourceType,
                                            cardLast4 = cardLast4.takeIf { sourceType == DataSourceType.CREDIT_CARD }?.ifBlank { null },
                                            enabled = true,
                                        )
                                    )
                                    editingSourceId = null
                                    sourceName = ""
                                    senderShortCode = ""
                                    cardLast4 = ""
                                    sourceType = DataSourceType.BANK
                                    notify("Source saved.")
                                }
                            }
                        ) { Text(if (editingSourceId == null) "Add" else "Save changes") }
                    }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "LLM API",
                subtitle = model,
                expanded = llmExpanded,
                onToggle = { llmExpanded = !llmExpanded }
            ) {
                    Text("LLM API (OpenAI-compatible)", fontWeight = FontWeight.Bold)
                    Text("Editable URL and key. Test API now performs a real request.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    OutlinedTextField(value = baseUrlEdit, onValueChange = { baseUrlEdit = it }, label = { Text("API URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = apiKeyEdit, onValueChange = { apiKeyEdit = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = modelEdit, onValueChange = { modelEdit = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            scope.launch {
                                container.settingsRepository.setLlmBaseUrl(baseUrlEdit.trim())
                                container.settingsRepository.setLlmModel(modelEdit.trim())
                                container.settingsRepository.setLlmApiKey(apiKeyEdit.trim())
                                notify("LLM configuration saved.")
                            }
                        }) { Text("Save") }
                        Button(onClick = {
                            scope.launch {
                                container.settingsRepository.setLlmBaseUrl(baseUrlEdit.trim())
                                container.settingsRepository.setLlmModel(modelEdit.trim())
                                container.settingsRepository.setLlmApiKey(apiKeyEdit.trim())
                                val result = container.llmClient.testConnection()
                                notify(result.getOrElse { "API test failed: ${it.message ?: it.toString()}" })
                            }
                        }) { Text("Test API") }
                    }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Card statement cycle",
                subtitle = "Cutoff day $cutoffDay",
                expanded = cardCycleExpanded,
                onToggle = { cardCycleExpanded = !cardCycleExpanded }
            ) {
                    Text("Card statement cycle", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = cutoffEdit,
                        onValueChange = { cutoffEdit = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Cutoff day (1-28)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        val v = cutoffEdit.toIntOrNull()?.coerceIn(1, 28) ?: 10
                        scope.launch {
                            container.settingsRepository.setStatementCutoffDay(v)
                            notify("Cutoff day saved.")
                        }
                    }) { Text("Save cutoff day") }
            }
        }
    }
}

@Composable
private fun CollapsibleSettingsCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}
