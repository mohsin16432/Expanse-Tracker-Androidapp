package com.trae.expensetracker.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.trae.expensetracker.ingest.ApprovedSmsFormat
import com.trae.expensetracker.ingest.RejectedSmsFormat
import com.trae.expensetracker.ingest.UserSmsRegexFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context,
) {
    private val KEY_BASE_CURRENCY = stringPreferencesKey("base_currency")
    private val KEY_STATEMENT_CUTOFF_DAY = intPreferencesKey("statement_cutoff_day")
    private val KEY_CARD_STATEMENT_CUTOFFS = stringPreferencesKey("card_statement_cutoffs")
    private val KEY_BUDGET_CYCLE_START_DAY = intPreferencesKey("budget_cycle_start_day")
    private val KEY_LLM_BASE_URL = stringPreferencesKey("llm_base_url")
    private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
    private val KEY_APPROVED_SMS_FORMATS = stringPreferencesKey("approved_sms_formats")
    private val KEY_USER_SMS_REGEX_FORMATS = stringPreferencesKey("user_sms_regex_formats")
    private val KEY_REJECTED_SMS_FORMATS = stringPreferencesKey("rejected_sms_formats")
    private val KEY_NOTIFICATION_ALLOWED_PACKAGES = stringPreferencesKey("notification_allowed_packages")
    private val KEY_NOTIFICATION_DEBUG_LOGS = stringPreferencesKey("notification_debug_logs")
    private val KEY_BUDGET_ALERTS_SENT = stringPreferencesKey("budget_alerts_sent")
    private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    private val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    private val json = Json { ignoreUnknownKeys = true }

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun baseCurrency(): Flow<String> = context.dataStore.data.map { it[KEY_BASE_CURRENCY] ?: "PKR" }
    suspend fun setBaseCurrency(value: String) = context.dataStore.edit { it[KEY_BASE_CURRENCY] = value }

    fun statementCutoffDay(): Flow<Int> = context.dataStore.data.map { it[KEY_STATEMENT_CUTOFF_DAY] ?: 10 }
    suspend fun setStatementCutoffDay(value: Int) = context.dataStore.edit { it[KEY_STATEMENT_CUTOFF_DAY] = value }
    fun cardStatementCutoffs(): Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        prefs.decodeCardStatementCutoffs()
    }
    fun cardStatementCutoffDay(sourceId: String): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs.decodeCardStatementCutoffs()[sourceId] ?: prefs[KEY_STATEMENT_CUTOFF_DAY] ?: 10
    }
    suspend fun getCardStatementCutoffs(): Map<String, Int> = context.dataStore.data.first().decodeCardStatementCutoffs()
    suspend fun setCardStatementCutoffDay(sourceId: String, value: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs.decodeCardStatementCutoffs().toMutableMap()
            current[sourceId] = value.coerceIn(1, 28)
            prefs[KEY_CARD_STATEMENT_CUTOFFS] = json.encodeToString(current)
        }
    }
    suspend fun clearCardStatementCutoffDay(sourceId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs.decodeCardStatementCutoffs().toMutableMap()
            current.remove(sourceId)
            prefs[KEY_CARD_STATEMENT_CUTOFFS] = json.encodeToString(current)
        }
    }

    fun budgetCycleStartDay(): Flow<Int> = context.dataStore.data.map { it[KEY_BUDGET_CYCLE_START_DAY] ?: 1 }
    suspend fun setBudgetCycleStartDay(value: Int) = context.dataStore.edit { it[KEY_BUDGET_CYCLE_START_DAY] = value }

    fun llmBaseUrl(): Flow<String> = context.dataStore.data.map { it[KEY_LLM_BASE_URL] ?: "https://api.poe.com/v1" }
    suspend fun setLlmBaseUrl(value: String) = context.dataStore.edit { it[KEY_LLM_BASE_URL] = value }

    fun llmModel(): Flow<String> = context.dataStore.data.map { it[KEY_LLM_MODEL] ?: "Claude-Sonnet-4.6" }
    suspend fun setLlmModel(value: String) = context.dataStore.edit { it[KEY_LLM_MODEL] = value }

    fun approvedSmsFormats(): Flow<List<ApprovedSmsFormat>> = context.dataStore.data.map { prefs ->
        prefs.decodeApprovedFormats()
    }
    suspend fun getApprovedSmsFormats(): List<ApprovedSmsFormat> = context.dataStore.data.first().decodeApprovedFormats()
    suspend fun addApprovedSmsFormat(value: ApprovedSmsFormat) {
        context.dataStore.edit { prefs ->
            val current = prefs.decodeApprovedFormats()
            val updated = (current.filterNot {
                it.sender.equals(value.sender, ignoreCase = true) && it.templateRegex == value.templateRegex
            } + value).takeLast(40)
            prefs[KEY_APPROVED_SMS_FORMATS] = json.encodeToString(updated)
        }
    }

    suspend fun getRejectedSmsFormats(): List<RejectedSmsFormat> = context.dataStore.data.first().decodeRejectedFormats()
    suspend fun addRejectedSmsFormat(value: RejectedSmsFormat) {
        context.dataStore.edit { prefs ->
            val current = prefs.decodeRejectedFormats()
            val updated = (current.filterNot {
                it.sender.equals(value.sender, ignoreCase = true) && it.templateRegex == value.templateRegex
            } + value).takeLast(200)
            prefs[KEY_REJECTED_SMS_FORMATS] = json.encodeToString(updated)
        }
    }

    fun userSmsRegexFormats(): Flow<List<UserSmsRegexFormat>> = context.dataStore.data.map { prefs ->
        prefs.decodeUserFormats()
    }
    suspend fun getUserSmsRegexFormats(): List<UserSmsRegexFormat> = context.dataStore.data.first().decodeUserFormats()
    suspend fun addUserSmsRegexFormat(value: UserSmsRegexFormat) {
        context.dataStore.edit { prefs ->
            val current = prefs.decodeUserFormats()
            val updated = (current + value).takeLast(80)
            prefs[KEY_USER_SMS_REGEX_FORMATS] = json.encodeToString(updated)
        }
    }

    fun notificationAllowedPackages(): Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs.decodeNotificationAllowedPackages()
    }
    suspend fun getNotificationAllowedPackages(): List<String> = context.dataStore.data.first().decodeNotificationAllowedPackages()
    suspend fun setNotificationAllowedPackages(value: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_ALLOWED_PACKAGES] = json.encodeToString(value.distinct())
        }
    }

    fun notificationDebugLogs(): Flow<List<NotificationDebugLog>> = context.dataStore.data.map { prefs ->
        prefs.decodeNotificationDebugLogs()
    }
    suspend fun getNotificationDebugLogs(): List<NotificationDebugLog> = context.dataStore.data.first().decodeNotificationDebugLogs()
    suspend fun addNotificationDebugLog(value: NotificationDebugLog) {
        context.dataStore.edit { prefs ->
            val current = prefs.decodeNotificationDebugLogs()
            prefs[KEY_NOTIFICATION_DEBUG_LOGS] = json.encodeToString((listOf(value) + current).take(60))
        }
    }
    suspend fun clearNotificationDebugLogs() {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_DEBUG_LOGS] = json.encodeToString(emptyList<NotificationDebugLog>())
        }
    }

    /** Budget alert keys already fired for the current cycle. */
    fun budgetAlertsSent(): Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_BUDGET_ALERTS_SENT].orEmpty()
        if (raw.isBlank()) emptySet()
        else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList()).toSet()
    }

    suspend fun getBudgetAlertsSent(): Set<String> = budgetAlertsSent().first()

    suspend fun markBudgetAlertsSent(keys: Collection<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_BUDGET_ALERTS_SENT].orEmpty()
            val existing = if (raw.isBlank()) emptyList()
            else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
            prefs[KEY_BUDGET_ALERTS_SENT] = json.encodeToString((existing + keys).distinct())
        }
    }

    suspend fun clearBudgetAlerts() {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUDGET_ALERTS_SENT] = json.encodeToString(emptyList<String>())
        }
    }

    // ---- App lock ----
    fun appLockEnabled(): Flow<Boolean> = context.dataStore.data.map { it[KEY_APP_LOCK_ENABLED] ?: false }
    suspend fun setAppLockEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK_ENABLED] = value }
    }

    // ---- Automatic backup ----
    fun autoBackupEnabled(): Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_BACKUP_ENABLED] ?: false }
    suspend fun setAutoBackupEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = value }
    }

    fun lastBackupAt(): Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_BACKUP_AT] }
    suspend fun setLastBackupAt(millis: Long) {
        context.dataStore.edit { it[KEY_LAST_BACKUP_AT] = millis }
    }

    fun llmApiKey(): String? = securePrefs.getString("llm_api_key", null)
    fun setLlmApiKey(value: String) {
        securePrefs.edit().putString("llm_api_key", value).apply()
    }

    suspend fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        baseCurrency = baseCurrency().first(),
        statementCutoffDay = statementCutoffDay().first(),
        budgetCycleStartDay = budgetCycleStartDay().first(),
        llmBaseUrl = llmBaseUrl().first(),
        llmModel = llmModel().first(),
        llmApiKey = llmApiKey(),
        approvedSmsFormats = getApprovedSmsFormats(),
        userSmsRegexFormats = getUserSmsRegexFormats(),
        rejectedSmsFormats = getRejectedSmsFormats(),
        cardStatementCutoffs = getCardStatementCutoffs(),
        appLockEnabled = appLockEnabled().first(),
        autoBackupEnabled = autoBackupEnabled().first(),
    )

    suspend fun restore(snapshot: SettingsSnapshot) {
        setBaseCurrency(snapshot.baseCurrency)
        setStatementCutoffDay(snapshot.statementCutoffDay)
        setBudgetCycleStartDay(snapshot.budgetCycleStartDay)
        setAppLockEnabled(snapshot.appLockEnabled)
        setAutoBackupEnabled(snapshot.autoBackupEnabled)
        setLlmBaseUrl(snapshot.llmBaseUrl)
        setLlmModel(snapshot.llmModel)
        context.dataStore.edit { prefs ->
            prefs[KEY_APPROVED_SMS_FORMATS] = json.encodeToString(snapshot.approvedSmsFormats)
            prefs[KEY_USER_SMS_REGEX_FORMATS] = json.encodeToString(snapshot.userSmsRegexFormats)
            prefs[KEY_REJECTED_SMS_FORMATS] = json.encodeToString(snapshot.rejectedSmsFormats)
            prefs[KEY_CARD_STATEMENT_CUTOFFS] = json.encodeToString(snapshot.cardStatementCutoffs)
        }
        snapshot.llmApiKey?.let { setLlmApiKey(it) }
    }

    private fun Preferences.decodeApprovedFormats(): List<ApprovedSmsFormat> {
        val raw = this[KEY_APPROVED_SMS_FORMATS].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ApprovedSmsFormat>>(raw) }.getOrDefault(emptyList())
    }

    private fun Preferences.decodeUserFormats(): List<UserSmsRegexFormat> {
        val raw = this[KEY_USER_SMS_REGEX_FORMATS].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<UserSmsRegexFormat>>(raw) }.getOrDefault(emptyList())
    }

    private fun Preferences.decodeRejectedFormats(): List<RejectedSmsFormat> {
        val raw = this[KEY_REJECTED_SMS_FORMATS].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<RejectedSmsFormat>>(raw) }.getOrDefault(emptyList())
    }

    private fun Preferences.decodeCardStatementCutoffs(): Map<String, Int> {
        val raw = this[KEY_CARD_STATEMENT_CUTOFFS].orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    private fun Preferences.decodeNotificationAllowedPackages(): List<String> {
        val raw = this[KEY_NOTIFICATION_ALLOWED_PACKAGES].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    private fun Preferences.decodeNotificationDebugLogs(): List<NotificationDebugLog> {
        val raw = this[KEY_NOTIFICATION_DEBUG_LOGS].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<NotificationDebugLog>>(raw) }.getOrDefault(emptyList())
    }
}

@Serializable
data class NotificationDebugLog(
    val timestampMillis: Long,
    val packageName: String,
    val title: String,
    val bodyPreview: String,
    val status: String,
    val detail: String? = null,
)

@Serializable
data class SettingsSnapshot(
    val baseCurrency: String,
    val statementCutoffDay: Int,
    val budgetCycleStartDay: Int = 1,
    val llmBaseUrl: String,
    val llmModel: String,
    val llmApiKey: String?,
    val approvedSmsFormats: List<ApprovedSmsFormat> = emptyList(),
    val userSmsRegexFormats: List<UserSmsRegexFormat> = emptyList(),
    val rejectedSmsFormats: List<RejectedSmsFormat> = emptyList(),
    val cardStatementCutoffs: Map<String, Int> = emptyMap(),
    val appLockEnabled: Boolean = false,
    val autoBackupEnabled: Boolean = false,
)
