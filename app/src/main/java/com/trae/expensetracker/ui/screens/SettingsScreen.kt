package com.trae.expensetracker.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.trae.expensetracker.data.AppContainer
import com.trae.expensetracker.data.model.CategoryEntity
import com.trae.expensetracker.data.model.DataSourceEntity
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.MerchantRuleType
import com.trae.expensetracker.data.repo.NotificationDebugLog
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.UserSmsRegexFormat
import com.trae.expensetracker.ingest.UserSmsRegexParser
import com.trae.expensetracker.ingest.parsers.CreditCardChargeParser
import com.trae.expensetracker.ingest.parsers.FblParser
import com.trae.expensetracker.ingest.parsers.GenericBankParser
import com.trae.expensetracker.ingest.parsers.HblParser
import com.trae.expensetracker.ingest.parsers.JazzCashParser
import com.trae.expensetracker.ingest.parsers.MeezanParser
import com.trae.expensetracker.ingest.parsers.WalletNotificationParser
import com.trae.expensetracker.ui.BudgetEngine
import com.trae.expensetracker.ui.MoneyFormat
import com.trae.expensetracker.ui.theme.Border
import com.trae.expensetracker.work.BackupScheduler
import com.trae.expensetracker.ui.theme.Surface
import com.trae.expensetracker.ui.theme.Surface2
import com.trae.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val baseUrl by container.settingsRepository.llmBaseUrl().collectAsState(initial = "https://api.poe.com/v1")
    val model by container.settingsRepository.llmModel().collectAsState(initial = "Claude-Sonnet-4.6")
    val cutoffDay by container.settingsRepository.statementCutoffDay().collectAsState(initial = 10)
    val cycleStartDay by container.settingsRepository.budgetCycleStartDay().collectAsState(initial = 1)
    val sources by container.dataSourceRepository.observeAll().collectAsState(initial = emptyList())
    val cardCutoffs by container.settingsRepository.cardStatementCutoffs().collectAsState(initial = emptyMap())
    val allowedNotifPackages by container.settingsRepository.notificationAllowedPackages().collectAsState(initial = emptyList())
    val notifDebugLogs by container.settingsRepository.notificationDebugLogs().collectAsState(initial = emptyList())
    val existingApiKey = remember { container.settingsRepository.llmApiKey().orEmpty() }
    val builtInParsers = remember { listOf(HblParser(), CreditCardChargeParser(), FblParser(), GenericBankParser, JazzCashParser(), MeezanParser(), WalletNotificationParser()) }
    val creditCardSources = remember(sources) { sources.filter { it.type == DataSourceType.CREDIT_CARD } }
    val merchantRules by container.merchantRuleRepository.observeAll().collectAsState(initial = emptyList())
    val budgets by container.budgetRepository.observeAll().collectAsState(initial = emptyList())
    val autoBackupPref by container.settingsRepository.autoBackupEnabled().collectAsState(initial = false)
    val appLockPref by container.settingsRepository.appLockEnabled().collectAsState(initial = false)
    val lastBackupPref by container.settingsRepository.lastBackupAt().collectAsState(initial = null)
    val deviceSecure = remember { container.appLockManager.isDeviceSecure() }
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    val ignoredImports by container.ignoredImportRepository.observeAll().collectAsState(initial = emptyList())

    var baseUrlEdit by remember(baseUrl) { mutableStateOf(baseUrl) }
    var modelEdit by remember(model) { mutableStateOf(model) }
    var apiKeyEdit by remember { mutableStateOf(existingApiKey) }
    var cutoffEdit by remember(cutoffDay) { mutableStateOf(cutoffDay.toString()) }
    var cycleStartEdit by remember(cycleStartDay) { mutableStateOf(cycleStartDay.toString()) }
    var status by remember { mutableStateOf("Settings are ready.") }
    var autoBackupEnabled by remember { mutableStateOf(false) }
    var appLockEnabled by remember { mutableStateOf(false) }
    var lastBackupAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(autoBackupPref) { autoBackupEnabled = autoBackupPref }
    LaunchedEffect(appLockPref) { appLockEnabled = appLockPref }
    LaunchedEffect(lastBackupPref) { lastBackupAt = lastBackupPref }

    fun notify(msg: String) {
        status = msg
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = container.backupService.exportCsvToUri(uri)
                notify(if (ok) "CSV exported." else "CSV export failed.")
            }
        }
    }
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
    var notificationsExpanded by remember { mutableStateOf(false) }
    var merchantRulesExpanded by remember { mutableStateOf(false) }
    var budgetsExpanded by remember { mutableStateOf(false) }
    var balancesExpanded by remember { mutableStateOf(false) }
    var ignoredExpanded by remember { mutableStateOf(false) }
    var categoriesExpanded by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var budgetCategory by remember { mutableStateOf(BudgetEngine.OVERALL_KEY) }
    var budgetLimitEdit by remember { mutableStateOf("") }
    var budgetCategoryMenuOpen by remember { mutableStateOf(false) }
    var balanceSourceId by remember { mutableStateOf<String?>(null) }
    var balanceEdit by remember { mutableStateOf("") }
    var balanceSourceMenuOpen by remember { mutableStateOf(false) }

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

    var showAllNotifApps by remember { mutableStateOf(false) }
    var notifAppSearch by remember { mutableStateOf("") }
    var notifSampleText by remember { mutableStateOf("") }
    var notifSampleResult by remember { mutableStateOf<String?>(null) }
    var isLearningNotif by remember { mutableStateOf(false) }
    var notifLearnPackage by remember { mutableStateOf<String?>(null) }
    var notifLearnPackageMenuOpen by remember { mutableStateOf(false) }

    var fmtTypeMenu by remember { mutableStateOf(false) }

    fun isNotificationAccessEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    var installedApps by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            runCatching {
                pm.queryIntentActivities(intent, 0)
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .map { info ->
                        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
                        info.packageName to label
                    }
                    .sortedBy { it.second.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    fun suggestedNotificationApps(): List<Pair<String, String>> {
        val q = notifAppSearch.trim().lowercase()
        val base = if (showAllNotifApps || q.isNotBlank()) {
            installedApps
        } else {
            val keywords = listOf("easypaisa", "easy paisa", "nayapay", "naya", "jazz", "jazzcash", "meezan", "hbl", "habib", "faysal", "bank")
            installedApps.filter { (pkg, label) ->
                val s = (pkg + " " + label).lowercase()
                allowedNotifPackages.contains(pkg) || keywords.any { s.contains(it) }
            }
        }
        if (q.isBlank()) return base
        return base.filter { (pkg, label) ->
            label.lowercase().contains(q) || pkg.lowercase().contains(q)
        }
    }
    val effectiveNotifLearnPackage = notifLearnPackage?.takeIf { allowedNotifPackages.contains(it) } ?: allowedNotifPackages.firstOrNull()
    fun formatDebugTime(ts: Long): String = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.US).format(Date(ts))

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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { csvLauncher.launch("expense_tracker_transactions.csv") }) { Text("Export CSV") }
                    }
                    Text(
                        "CSV opens in Excel, Numbers or Google Sheets.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider(color = Border)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoBackupEnabled,
                            onCheckedChange = { checked ->
                                autoBackupEnabled = checked
                                scope.launch {
                                    container.settingsRepository.setAutoBackupEnabled(checked)
                                    if (checked) {
                                        BackupScheduler.schedule(context)
                                        notify("Daily automatic backup enabled.")
                                    } else {
                                        BackupScheduler.cancel(context)
                                        notify("Automatic backup disabled.")
                                    }
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily automatic backup")
                            Text(
                                "Saves a backup to this device once a day.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        lastBackupAt?.let { "Last backup: ${formatDebugTime(it)}" } ?: "No backup recorded yet.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider(color = Border)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = appLockEnabled,
                            enabled = deviceSecure,
                            onCheckedChange = { checked ->
                                appLockEnabled = checked
                                scope.launch {
                                    container.settingsRepository.setAppLockEnabled(checked)
                                    notify(if (checked) "App lock enabled." else "App lock disabled.")
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require screen lock to open")
                            Text(
                                if (deviceSecure) {
                                    "Uses your device PIN, pattern, password or biometric."
                                } else {
                                    "Set up a screen lock on this device first."
                                },
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                                        fmtTestResult = "Generated parser did not match the sample. Try again."
                                        notify("Generated parser did not match the sample. Try again.")
                                    } else {
                                        container.settingsRepository.addUserSmsRegexFormat(format)
                                        container.smsIngestor.invalidateUserFormatsCache()
                                        fmtTestResult = "SMS format added"
                                        notify("SMS format added")
                                    }
                                } catch (t: Throwable) {
                                    val reason = t.message?.takeIf { it.isNotBlank() } ?: "unknown error"
                                    fmtTestResult = "Could not learn this format: $reason"
                                    notify("Could not learn this format: $reason")
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
                title = "Deleted messages",
                subtitle = "Messages you removed, skipped by future imports",
                expanded = ignoredExpanded,
                onToggle = { ignoredExpanded = !ignoredExpanded }
            ) {
                Text(
                    "Bank messages stay in your inbox, so the app remembers deletions to avoid re-importing them. Restore one to let it import again.",
                    color = TextSecondary
                )
                if (ignoredImports.isEmpty()) {
                    Text("Nothing has been deleted.", color = TextSecondary)
                } else {
                    Text("${ignoredImports.size} remembered", fontWeight = FontWeight.SemiBold)
                    ignoredImports.take(20).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.preview,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${entry.reason} • ${formatDebugTime(entry.createdAtMillis)}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    container.ignoredImportRepository.restore(entry.signature)
                                    notify("Restored. It will import again.")
                                }
                            }) { Text("Restore") }
                        }
                        HorizontalDivider(color = Border)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            container.ignoredImportRepository.clearAll()
                            notify("All deleted messages cleared.")
                        }
                    }) { Text("Clear all", color = TextSecondary) }
                }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Account balances",
                subtitle = "Set a starting balance so running balances are accurate",
                expanded = balancesExpanded,
                onToggle = { balancesExpanded = !balancesExpanded }
            ) {
                Text(
                    "Record what each account held on a given day. The Dashboard then adds every later transaction to show a live balance.",
                    color = TextSecondary
                )
                OutlinedButton(
                    onClick = { balanceSourceMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val selected = sources.firstOrNull { it.id == balanceSourceId }
                    Text(
                        selected?.let { "${it.name} (${it.shortCode})" } ?: "Choose account",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(expanded = balanceSourceMenuOpen, onDismissRequest = { balanceSourceMenuOpen = false }) {
                    sources.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s.name} (${s.shortCode})") },
                            onClick = {
                                balanceSourceId = s.id
                                val existing = s.openingBalanceMinor
                                balanceEdit = existing?.let { "%.2f".format(it / 100.0) } ?: ""
                                balanceSourceMenuOpen = false
                            }
                        )
                    }
                }
                sources.firstOrNull { it.id == balanceSourceId }?.openingBalanceMinor?.let { current ->
                    Text(
                        "Current opening balance: ${MoneyFormat.format("PKR", current)}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedTextField(
                    value = balanceEdit,
                    onValueChange = { balanceEdit = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                    label = { Text("Opening balance (e.g. 45000)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = balanceSourceId != null && balanceEdit.toDoubleOrNull() != null,
                        onClick = {
                            val id = balanceSourceId ?: return@Button
                            val minor = ((balanceEdit.toDoubleOrNull() ?: 0.0) * 100).toLong()
                            scope.launch {
                                container.dataSourceRepository.setOpeningBalance(
                                    id = id,
                                    balanceMinor = minor,
                                    atMillis = System.currentTimeMillis(),
                                )
                                notify("Opening balance saved.")
                            }
                        }
                    ) { Text("Save balance") }
                    TextButton(
                        enabled = balanceSourceId != null,
                        onClick = {
                            val id = balanceSourceId ?: return@TextButton
                            scope.launch {
                                container.dataSourceRepository.setOpeningBalance(id, null, null)
                                balanceEdit = ""
                                notify("Opening balance cleared.")
                            }
                        }
                    ) { Text("Clear", color = TextSecondary) }
                }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Budgets",
                subtitle = "Set a spending limit per category",
                expanded = budgetsExpanded,
                onToggle = { budgetsExpanded = !budgetsExpanded }
            ) {
                Text(
                    "Budgets follow your budget cycle start day. Leave the category empty for an overall spending limit.",
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { budgetCategoryMenuOpen = true }, modifier = Modifier.weight(1f)) {
                        Text(if (budgetCategory == BudgetEngine.OVERALL_KEY) "All spending" else budgetCategory, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = budgetCategoryMenuOpen, onDismissRequest = { budgetCategoryMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("All spending") },
                            onClick = { budgetCategory = BudgetEngine.OVERALL_KEY; budgetCategoryMenuOpen = false }
                        )
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { budgetCategory = c.name; budgetCategoryMenuOpen = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = budgetLimitEdit,
                    onValueChange = { budgetLimitEdit = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Limit per cycle (e.g. 25000)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Button(
                    enabled = budgetLimitEdit.toDoubleOrNull() != null && (budgetLimitEdit.toDoubleOrNull() ?: 0.0) > 0.0,
                    onClick = {
                        val minor = ((budgetLimitEdit.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        scope.launch {
                            container.budgetRepository.setLimit(
                                categoryId = budgetCategory.takeIf { it != BudgetEngine.OVERALL_KEY },
                                limitMinor = minor,
                            )
                            budgetLimitEdit = ""
                            notify("Budget saved.")
                        }
                    }
                ) { Text("Save budget") }

                if (budgets.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    budgets.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (b.categoryId.isNullOrBlank()) "All spending" else b.categoryId,
                                modifier = Modifier.weight(1f)
                            )
                            Text(MoneyFormat.format(b.currency, b.limitMinor), fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = {
                                scope.launch {
                                    container.budgetRepository.delete(b.id)
                                    notify("Budget removed.")
                                }
                            }) { Icon(Icons.Outlined.Close, contentDescription = "Delete budget") }
                        }
                        HorizontalDivider(color = Border)
                    }
                }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Categories",
                subtitle = "Add or remove spending categories",
                expanded = categoriesExpanded,
                onToggle = { categoriesExpanded = !categoriesExpanded }
            ) {
                Text(
                    "Categories are used by budgets, reports and the transaction list.",
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("New category name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        enabled = newCategoryName.isNotBlank(),
                        onClick = {
                            val name = newCategoryName.trim()
                            scope.launch {
                                if (categories.any { it.name.equals(name, ignoreCase = true) }) {
                                    notify("Category already exists.")
                                } else {
                                    container.categoryRepository.upsert(
                                        CategoryEntity(
                                            id = name,
                                            name = name,
                                            colorArgb = 0xFF6B5DFF,
                                            sortOrder = container.categoryRepository.nextSortOrder(),
                                        )
                                    )
                                    newCategoryName = ""
                                    notify("Category added.")
                                }
                            }
                        }
                    ) { Text("Add") }
                }
                categories.forEach { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            scope.launch {
                                container.categoryRepository.delete(c.id)
                                notify("Category deleted.")
                            }
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Delete category") }
                    }
                    HorizontalDivider(color = Border)
                }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Learned merchant rules",
                subtitle = "Categories and names the app remembered from your corrections",
                expanded = merchantRulesExpanded,
                onToggle = { merchantRulesExpanded = !merchantRulesExpanded }
            ) {
                Text(
                    "When you fix a merchant name or category in Review, the app remembers it and applies it to future imports automatically.",
                    color = TextSecondary
                )
                if (merchantRules.isEmpty()) {
                    Text("No learned rules yet. Correct a transaction in Review to create one.", color = TextSecondary)
                } else {
                    Text("${merchantRules.size} rule${if (merchantRules.size == 1) "" else "s"} learned", fontWeight = FontWeight.SemiBold)
                    merchantRules.forEach { rule ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface2),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        rule.pattern,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = {
                                        scope.launch {
                                            container.merchantRuleRepository.delete(rule.id)
                                            container.smsIngestor.invalidateMerchantRuleCache()
                                            notify("Rule deleted.")
                                        }
                                    }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Delete rule")
                                    }
                                }
                                Text(
                                    when (rule.type) {
                                        MerchantRuleType.MERCHANT_CATEGORY -> "Category: ${rule.categoryId.orEmpty()}"
                                        MerchantRuleType.MERCHANT_ALIAS -> "Rename to: ${rule.alias.orEmpty()}"
                                        MerchantRuleType.DUPLICATE_SUPPRESSION ->
                                            "Never flagged as duplicate: ${rule.pattern.replace("|", " + ")}"
                                    },
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Applied ${rule.hitCount} time${if (rule.hitCount == 1) "" else "s"}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            CollapsibleSettingsCard(
                title = "Notifications capture",
                subtitle = "Track bank/wallet push notifications",
                expanded = notificationsExpanded,
                onToggle = { notificationsExpanded = !notificationsExpanded }
            ) {
                Text(
                    "Enable Notification Access, then select which apps to listen to (e.g., Easypaisa, NayaPay).",
                    color = TextSecondary
                )
                val enabled = isNotificationAccessEnabled()
                Text(
                    if (enabled) "Notification access is enabled." else "Notification access is disabled.",
                    color = if (enabled) MaterialTheme.colorScheme.primary else TextSecondary
                )
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Open notification access settings") }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = showAllNotifApps, onCheckedChange = { showAllNotifApps = it })
                    Text("Show all installed apps")
                }
                OutlinedTextField(
                    value = notifAppSearch,
                    onValueChange = { notifAppSearch = it },
                    label = { Text("Search app by name or package") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (notifAppSearch.isNotBlank()) {
                            IconButton(onClick = { notifAppSearch = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                )

                val appList = suggestedNotificationApps()
                if (appList.isEmpty()) {
                    Text("No matching apps found.", color = TextSecondary)
                } else {
                    appList.forEach { (pkg, label) ->
                        val checked = allowedNotifPackages.contains(pkg)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val updated = if (checked) {
                                            allowedNotifPackages.filterNot { it == pkg }
                                        } else {
                                            (allowedNotifPackages + pkg).distinct()
                                        }
                                        container.settingsRepository.setNotificationAllowedPackages(updated)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        val updated = if (isChecked) {
                                            (allowedNotifPackages + pkg).distinct()
                                        } else {
                                            allowedNotifPackages.filterNot { it == pkg }
                                        }
                                        container.settingsRepository.setNotificationAllowedPackages(updated)
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pkg, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text("Showing ${appList.size} app${if (appList.size == 1) "" else "s"}.", color = TextSecondary)
                }

                Spacer(Modifier.height(8.dp))
                Text("Learn notification format", fontWeight = FontWeight.SemiBold)
                Text("Paste one sample notification text from a selected app. The app will learn it in the background.", color = TextSecondary)
                if (allowedNotifPackages.isNotEmpty()) {
                    Button(onClick = { notifLearnPackageMenuOpen = true }) {
                        Text(
                            "App: " + (
                                installedApps.firstOrNull { it.first == effectiveNotifLearnPackage }?.second
                                    ?: effectiveNotifLearnPackage
                                    ?: "Select app"
                                )
                        )
                    }
                    DropdownMenu(
                        expanded = notifLearnPackageMenuOpen,
                        onDismissRequest = { notifLearnPackageMenuOpen = false }
                    ) {
                        allowedNotifPackages.forEach { pkg ->
                            DropdownMenuItem(
                                text = { Text(installedApps.firstOrNull { it.first == pkg }?.second ?: pkg) },
                                onClick = {
                                    notifLearnPackage = pkg
                                    notifLearnPackageMenuOpen = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notifSampleText,
                    onValueChange = { notifSampleText = it },
                    label = { Text("Sample notification text") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (notifSampleText.isNotBlank()) {
                            IconButton(onClick = { notifSampleText = ""; notifSampleResult = null }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
                Button(
                    enabled = effectiveNotifLearnPackage != null && notifSampleText.isNotBlank() && !isLearningNotif,
                    onClick = {
                        scope.launch {
                            try {
                                isLearningNotif = true
                                val pkg = effectiveNotifLearnPackage ?: return@launch
                                val msg = SmsMessage(sender = pkg, body = notifSampleText, receivedAtMillis = System.currentTimeMillis())
                                val builtInMatch = builtInParsers.firstNotNullOfOrNull { it.parse(msg) }
                                if (builtInMatch != null) {
                                    notifSampleResult = "This notification is already supported"
                                    notify("This notification is already supported")
                                    return@launch
                                }
                                val spec = container.llmClient.generateSmsRegexSpec(msg).getOrThrow()
                                val format = UserSmsRegexFormat(
                                    sender = pkg,
                                    sourceShortCode = pkg,
                                    sourceNameHint = installedApps.firstOrNull { it.first == pkg }?.second ?: pkg,
                                    sourceTypeHint = DataSourceType.NOTIFICATION,
                                    type = spec.type,
                                    direction = spec.direction,
                                    regex = spec.regex,
                                    amountGroup = spec.amountGroup,
                                    merchantGroup = spec.merchantGroup,
                                    dateGroup = spec.dateGroup,
                                    timeGroup = spec.timeGroup,
                                    datePattern = spec.datePattern,
                                    timePattern = spec.timePattern,
                                    referenceGroup = spec.referenceGroup,
                                    currencyOverride = spec.currencyOverride,
                                )
                                val draft = UserSmsRegexParser.parse(format, msg)
                                if (draft == null) {
                                    val reason = "Generated format did not match the sample notification"
                                    notifSampleResult = reason
                                    notify(reason)
                                } else {
                                    container.settingsRepository.addUserSmsRegexFormat(format)
                                    container.smsIngestor.invalidateUserFormatsCache()
                                    notifSampleResult = "Notification format added"
                                    notify("Notification format added")
                                }
                            } catch (t: Throwable) {
                                val reason = t.message?.takeIf { it.isNotBlank() }
                                    ?: t::class.simpleName
                                    ?: "Unknown error while learning notification"
                                notifSampleResult = reason
                                notify(reason)
                            } finally {
                                isLearningNotif = false
                            }
                        }
                    }
                ) { Text(if (isLearningNotif) "Learning..." else "Learn from sample") }
                notifSampleResult?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
                        Text(it, modifier = Modifier.padding(14.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent debug log", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        scope.launch {
                            container.settingsRepository.clearNotificationDebugLogs()
                            notify("Notification debug log cleared")
                        }
                    }) { Text("Clear log") }
                }
                Text(
                    "Trigger a fresh notification after enabling access. The latest outcomes will appear here.",
                    color = TextSecondary
                )
                if (notifDebugLogs.isEmpty()) {
                    Text("No notification events logged yet.", color = TextSecondary)
                } else {
                    notifDebugLogs.take(12).forEach { entry ->
                        NotificationDebugLogCard(
                            entry = entry,
                            appLabel = installedApps.firstOrNull { it.first == entry.packageName }?.second
                        ) { formatDebugTime(entry.timestampMillis) }
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
                                val extra = if (s.type == DataSourceType.CREDIT_CARD) {
                                    s.cardLast4?.let { " • $it" } ?: " • no card number"
                                } else ""
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
                            onValueChange = {
                                senderShortCode = if (sourceType == DataSourceType.NOTIFICATION) it else it.filter { ch -> ch.isDigit() }
                            },
                            label = {
                                Text(if (sourceType == DataSourceType.NOTIFICATION) "App package name (e.g., pk.com.telenor.easypaisa)" else "Sender short code (e.g., 14250)")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (sourceType == DataSourceType.NOTIFICATION) KeyboardType.Text else KeyboardType.Number
                            ),
                            singleLine = true
                        )
                        if (sourceType == DataSourceType.NOTIFICATION) {
                            Text(
                                "For notifications this must match the selected app package in Notifications capture. You can usually skip manual source creation; it auto-creates after a valid notification is parsed.",
                                color = TextSecondary
                            )
                        }
                    }
                    if (sourceType == DataSourceType.CREDIT_CARD) {
                        OutlinedTextField(
                            value = cardLast4,
                            onValueChange = { cardLast4 = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Card last 4 (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Text(
                            "Optional. Use it only if the bank includes card digits in SMS or if you need to separate multiple cards on the same sender.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { typeMenuOpen = true }) { Text("Type: ${sourceType.name}") }
                        DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                            DataSourceType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(t.name) }, onClick = {
                                    sourceType = t
                                    senderShortCode = if (t == DataSourceType.NOTIFICATION) senderShortCode else senderShortCode.filter { ch -> ch.isDigit() }
                                    typeMenuOpen = false
                                })
                            }
                        }
                        Button(
                            enabled = sourceName.isNotBlank() &&
                                (sourceType == DataSourceType.CASH || senderShortCode.isNotBlank()),
                            onClick = {
                                scope.launch {
                                    val sender = senderShortCode.trim()
                                    val id = editingSourceId ?: when (sourceType) {
                                        DataSourceType.CASH -> "cash"
                                        DataSourceType.BANK -> "${sender}-BANK"
                                        DataSourceType.WALLET -> "${sender}-WALLET"
                                        DataSourceType.CREDIT_CARD -> if (cardLast4.isBlank()) "${sender}-CC" else "${sender}-CC-${cardLast4}"
                                        DataSourceType.NOTIFICATION -> "${sender}-NOTIF"
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
                subtitle = "Default cutoff $cutoffDay • ${creditCardSources.size} card${if (creditCardSources.size == 1) "" else "s"}",
                expanded = cardCycleExpanded,
                onToggle = { cardCycleExpanded = !cardCycleExpanded }
            ) {
                    Text("Card statement cycle", fontWeight = FontWeight.Bold)
                    Text("Default cutoff day", color = TextSecondary)
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
                    if (creditCardSources.isEmpty()) {
                        Text("Add a credit card source to set a separate cutoff date.", color = TextSecondary)
                    } else {
                        Text("Per-card cutoff dates", fontWeight = FontWeight.SemiBold)
                        creditCardSources.forEach { card ->
                            var cardCutoffEdit by remember(card.id, cardCutoffs[card.id], cutoffDay) {
                                mutableStateOf((cardCutoffs[card.id] ?: cutoffDay).toString())
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        buildString {
                                            append(card.name)
                                            card.cardLast4?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(card.shortCode, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    OutlinedTextField(
                                        value = cardCutoffEdit,
                                        onValueChange = { cardCutoffEdit = it.filter { ch -> ch.isDigit() }.take(2) },
                                        label = { Text("Cutoff day (1-28)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = {
                                            val v = cardCutoffEdit.toIntOrNull()?.coerceIn(1, 28) ?: cutoffDay
                                            scope.launch {
                                                container.settingsRepository.setCardStatementCutoffDay(card.id, v)
                                                notify("${card.name} cutoff saved.")
                                            }
                                        }) { Text("Save") }
                                        Button(onClick = {
                                            scope.launch {
                                                container.settingsRepository.clearCardStatementCutoffDay(card.id)
                                                notify("${card.name} cutoff reset to default.")
                                            }
                                        }) { Text("Use default") }
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
private fun NotificationDebugLogCard(
    entry: NotificationDebugLog,
    appLabel: String?,
    formatTime: () -> String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(appLabel ?: entry.packageName, fontWeight = FontWeight.SemiBold)
            Text(entry.packageName, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text("${entry.status} • ${formatTime()}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            entry.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (entry.title.isNotBlank()) {
                Text(entry.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(entry.bodyPreview, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
