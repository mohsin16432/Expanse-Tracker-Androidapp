package com.trae.expensetracker.llm

import android.content.Context
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType
import com.trae.expensetracker.data.repo.SettingsRepository
import com.trae.expensetracker.ingest.SmsClassifier
import com.trae.expensetracker.ingest.SmsMessage
import com.trae.expensetracker.ingest.TransactionDraft
import com.trae.expensetracker.ingest.parsers.ParseUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible client configured for Poe (or any compatible endpoint) by user-provided base URL + API key.
 *
 * Docs: https://creator.poe.com/docs/external-applications/openai-compatible-api
 */
class PoeOpenAiClient(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
) : LlmClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun classifyAndParseSms(message: SmsMessage): LlmSmsResult = withContext(Dispatchers.IO) {
        try {
        // Quick local reject before spending LLM calls.
        if (SmsClassifier.isWithdrawal(message.body) || SmsClassifier.isOtpOrPromo(message.body)) {
            return@withContext LlmSmsResult(false, null, rawModelOutput = "local_drop")
        }

        val baseUrl = settingsRepository.llmBaseUrl().first().trim().trimEnd('/')
        val model = settingsRepository.llmModel().first().trim().ifBlank { "Claude-Sonnet-4.6" }
        val apiKey = settingsRepository.llmApiKey()?.trim()
        if (apiKey.isNullOrBlank()) {
            return@withContext LlmSmsResult(false, null, rawModelOutput = "missing_api_key")
        }

        val system = """
You are a strict SMS transaction classifier and parser.
Rules:
1) OTP and promotional messages are NOT transactions.
2) Cash withdrawal SMS (ATM cash withdrawal) must be treated as NOT a transaction and must be excluded.
3) If it is transactional, extract a normalized TransactionDraft.
Output JSON only. No extra text.
""".trimIndent()

        val user = """
Sender: ${message.sender}
ReceivedAtMillis: ${message.receivedAtMillis}
SMS:
${message.body}

Return JSON with this shape:
{
  "isTransactional": true|false,
  "reason": "short reason",
  "draft": {
    "sourceShortCode": "string or null",
    "sourceNameHint": "string or null",
    "sourceTypeHint": "BANK|WALLET|CREDIT_CARD or null",
    "cardLast4Hint": "string or null",
    "timestampMillis": 0,
    "type": "DEBIT_PURCHASE|CREDIT_RECEIVED|TRANSFER_OUT|TRANSFER_IN|CARD_CHARGE|CARD_PAYMENT|BILL_PAYMENT|ADJUSTMENT",
    "direction": "OUT|IN",
    "amountMinor": 0,
    "currency": "PKR|USD|...",
    "merchantRaw": "string",
    "reference": "string or null",
    "rawMessage": "string",
    "externalId": "stable hash string",
    "confidence": 0.0
  }
}
If isTransactional=false, draft must be null.
""".trimIndent()

        val req = ChatCompletionRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = user),
            ),
            temperature = 0.0,
        )

        val url = "$baseUrl/chat/completions"
        val bodyJson = json.encodeToString(ChatCompletionRequest.serializer(), req)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                return@withContext LlmSmsResult(false, null, rawModelOutput = "http_${resp.code}")
            }
            resp.body?.string().orEmpty()
        }

        val content = runCatching {
            val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
            parsed.choices.firstOrNull()?.message?.content.orEmpty()
        }.getOrElse { "" }

        val extractedJson = extractJsonObject(content)
        if (extractedJson.isBlank()) {
            return@withContext LlmSmsResult(false, null, rawModelOutput = content.ifBlank { responseBody })
        }

        val result = runCatching {
            json.decodeFromString(LlmOutput.serializer(), extractedJson)
        }.getOrNull()

        val draft = result?.draft?.toDraft(message)
        return@withContext LlmSmsResult(
            isTransactional = result?.isTransactional == true && draft != null,
            draft = draft,
            rawModelOutput = extractedJson,
        )
        } catch (t: Throwable) {
            return@withContext LlmSmsResult(false, null, rawModelOutput = "error_${t::class.simpleName ?: "unknown"}:${t.message ?: ""}")
        }
    }

    override suspend fun generateSmsRegexSpec(message: SmsMessage): Result<SmsRegexGenerationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = settingsRepository.llmBaseUrl().first().trim().trimEnd('/')
            val model = settingsRepository.llmModel().first().trim().ifBlank { "Claude-Sonnet-4.6" }
            val apiKey = settingsRepository.llmApiKey()?.trim().orEmpty()
            require(apiKey.isNotBlank()) { "API key is missing." }

            val system = """
You generate robust SMS parsing regex specs.
Return JSON only. No extra text.
Goal: create a reusable regex for messages of the same format, not just this one exact SMS.
Use capture groups for:
- amount (required)
- merchant or counterparty if present
- date/time if present
- reference/TID if present
Prefer generic patterns for amounts, names, dates, times, masked accounts, and IDs.
If date/time is captured, also return parse patterns like dd/MM/yyyy and HH:mm:ss.
""".trimIndent()

            val user = """
Sender: ${message.sender}
SMS:
${message.body}

Return JSON with this exact shape:
{
  "sourceNameHint": "JazzCash",
  "sourceTypeHint": "WALLET",
  "type": "TRANSFER_OUT",
  "direction": "OUT",
  "regex": "string",
  "amountGroup": 1,
  "merchantGroup": 2,
  "dateGroup": 3,
  "timeGroup": 4,
  "datePattern": "dd/MM/yyyy",
  "timePattern": "HH:mm:ss",
  "referenceGroup": 5,
  "currencyOverride": null
}

Rules:
- infer a short human-friendly sourceNameHint from the SMS itself
- sourceTypeHint must be one of: CASH, BANK, WALLET, CREDIT_CARD
- type must be a valid transaction type
- direction must be IN or OUT
- amountGroup must be present and > 0
- optional groups can be null
- regex must be compatible with Kotlin/Java regex
- do not include markdown fences
""".trimIndent()

            val req = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = system),
                    ChatMessage(role = "user", content = user),
                ),
                temperature = 0.0,
                maxTokens = 400,
            )

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.encodeToString(ChatCompletionRequest.serializer(), req).toRequestBody("application/json".toMediaType()))
                .build()

            val body = http.newCall(request).execute().use { resp ->
                require(resp.isSuccessful) { "HTTP ${resp.code}" }
                resp.body?.string().orEmpty()
            }

            val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), body)
            val content = parsed.choices.firstOrNull()?.message?.content.orEmpty()
            val extractedJson = extractJsonObject(content)
            require(extractedJson.isNotBlank()) { "Model returned no JSON." }
            val spec = json.decodeFromString(RegexSpecOutput.serializer(), extractedJson)
            require(spec.regex.isNotBlank()) { "Generated regex was empty." }
            require(spec.amountGroup > 0) { "Generated amountGroup was invalid." }
            SmsRegexGenerationResult(
                sourceNameHint = spec.sourceNameHint,
                sourceTypeHint = parseSourceType(spec.sourceTypeHint),
                type = parseTransactionType(spec.type),
                direction = parseDirection(spec.direction),
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
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = settingsRepository.llmBaseUrl().first().trim().trimEnd('/')
            val model = settingsRepository.llmModel().first().trim().ifBlank { "Claude-Sonnet-4.6" }
            val apiKey = settingsRepository.llmApiKey()?.trim().orEmpty()
            require(apiKey.isNotBlank()) { "API key is missing." }

            val req = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = "Reply in one short sentence."),
                    ChatMessage(role = "user", content = "Say TEST_OK only."),
                ),
                temperature = 0.0,
                maxTokens = 20,
            )

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.encodeToString(ChatCompletionRequest.serializer(), req).toRequestBody("application/json".toMediaType()))
                .build()

            val body = http.newCall(request).execute().use { resp ->
                require(resp.isSuccessful) { "HTTP ${resp.code}" }
                resp.body?.string().orEmpty()
            }
            val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), body)
            parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifBlank { "Connection succeeded." }
        }
    }

    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        // Best-effort: find first {...} block.
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return ""
    }

    @Serializable
    private data class LlmOutput(
        val isTransactional: Boolean,
        val reason: String? = null,
        val draft: LlmDraft? = null,
    )

    @Serializable
    private data class LlmDraft(
        val sourceShortCode: String? = null,
        val sourceNameHint: String? = null,
        val sourceTypeHint: DataSourceType? = null,
        val cardLast4Hint: String? = null,
        val timestampMillis: Long,
        val type: TransactionType,
        val direction: TransactionDirection,
        val amountMinor: Long,
        val currency: String,
        val merchantRaw: String,
        val reference: String? = null,
        val rawMessage: String,
        val externalId: String? = null,
        val confidence: Double = 0.6,
    ) {
        fun toDraft(message: SmsMessage): TransactionDraft {
            val extId = externalId?.ifBlank { null } ?: ParseUtils.sha256Hex("${message.sender}|${message.body}")
            return TransactionDraft(
                sourceShortCode = sourceShortCode ?: message.sender,
                sourceNameHint = sourceNameHint,
                sourceTypeHint = sourceTypeHint,
                cardLast4Hint = cardLast4Hint,
                timestampMillis = timestampMillis.takeIf { it > 0 } ?: message.receivedAtMillis,
                type = type,
                direction = direction,
                amountMinor = kotlin.math.abs(amountMinor),
                currency = currency.ifBlank { "PKR" },
                merchantRaw = merchantRaw,
                reference = reference,
                rawMessage = rawMessage.ifBlank { message.body },
                externalId = extId,
                confidence = confidence,
            )
        }
    }

    @Serializable
    private data class RegexSpecOutput(
        val sourceNameHint: String,
        val sourceTypeHint: String,
        val type: String,
        val direction: String,
        val regex: String,
        val amountGroup: Int,
        val merchantGroup: Int? = null,
        val dateGroup: Int? = null,
        val timeGroup: Int? = null,
        val datePattern: String? = null,
        val timePattern: String? = null,
        val referenceGroup: Int? = null,
        val currencyOverride: String? = null,
    )

    private fun parseSourceType(value: String): com.trae.expensetracker.data.model.DataSourceType {
        val normalized = value.trim().uppercase()
        return com.trae.expensetracker.data.model.DataSourceType.entries.firstOrNull { it.name == normalized }
            ?: throw IllegalArgumentException("Unsupported source type: $value")
    }

    private fun parseTransactionType(value: String): com.trae.expensetracker.data.model.TransactionType {
        val normalized = value.trim().uppercase()
        return com.trae.expensetracker.data.model.TransactionType.entries.firstOrNull { it.name == normalized }
            ?: throw IllegalArgumentException("Unsupported transaction type: $value")
    }

    private fun parseDirection(value: String): com.trae.expensetracker.data.model.TransactionDirection {
        val normalized = value.trim().uppercase()
        return com.trae.expensetracker.data.model.TransactionDirection.entries.firstOrNull { it.name == normalized }
            ?: throw IllegalArgumentException("Unsupported direction: $value")
    }

    // --- OpenAI-compatible schema (chat completions) ---
    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double? = null,
        @SerialName("max_tokens") val maxTokens: Int? = 500,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
    ) {
        @Serializable
        data class Choice(
            val message: ChatMessageOut,
        )

        @Serializable
        data class ChatMessageOut(
            val role: String,
            val content: String,
        )
    }
}
