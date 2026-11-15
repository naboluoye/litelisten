package com.litelisten.player.core.ai

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import com.litelisten.player.BuildConfig
import com.litelisten.player.core.datastore.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Failure categories translated to user-facing messages in the UI layer. */
enum class AiErrorCode { AUTH, RATE, BAD_REQUEST, SERVER, NETWORK, EMPTY, DISABLED }

sealed interface AiChatResult {
    data class Success(val reply: String) : AiChatResult
    data class Failure(val code: AiErrorCode) : AiChatResult
}

/**
 * Optional AI chat client (OpenAI-incompatible, custom JSON envelope).
 * All service coordinates are injected at build time via BuildConfig; this source
 * deliberately contains no endpoint, key, or vendor identifiers.
 * Envelope: {code: Int, data: {reply: String}, msg: String}; code 0 = success.
 * Read timeout 55s.
 */
@Singleton
class AiChatClient @Inject constructor(
    private val dataStore: androidx.datastore.core.DataStore<Preferences>,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(55, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        message: String,
        systemPrompt: String? = null,
        history: List<Pair<String, String>> = emptyList(),
    ): AiChatResult = withContext(Dispatchers.IO) {
        if (BuildConfig.AI_API_KEY.isBlank() || BuildConfig.AI_CHAT_URL.isBlank()) {
            return@withContext AiChatResult.Failure(AiErrorCode.DISABLED)
        }
        runCatching {
            val body = JSONObject().apply {
                put("message", message)
                put("api_key", BuildConfig.AI_API_KEY)
                put("user_id", resolveUserId())
                systemPrompt?.takeIf { it.isNotBlank() }?.let { put("system_prompt", it) }
                if (history.isNotEmpty()) {
                    put(
                        "history",
                        JSONArray().apply {
                            history.takeLast(MAX_HISTORY).forEach { (role, content) ->
                                put(JSONObject().apply {
                                    put("role", role)
                                    put("content", content)
                                })
                            }
                        },
                    )
                }
            }
            val request = Request.Builder()
                .url(BuildConfig.AI_CHAT_URL)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                parseEnvelope(response.code, text)
            }
        }.getOrElse { AiChatResult.Failure(AiErrorCode.NETWORK) }
    }

    private suspend fun resolveUserId(): String {
        val existing = dataStore.data.first()[SettingsKeys.AI_USER_ID_KEY].orEmpty()
        if (existing.isNotBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        dataStore.edit { it[SettingsKeys.AI_USER_ID_KEY] = fresh }
        return fresh
    }

    companion object {
        const val MAX_HISTORY = 10

        /** Pure envelope mapping so unit tests can cover every error branch. */
        fun parseEnvelope(httpCode: Int, text: String): AiChatResult {
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: return if (httpCode >= 500) AiChatResult.Failure(AiErrorCode.SERVER)
                else AiChatResult.Failure(AiErrorCode.NETWORK)
            val code = json.optInt("code", -1)
            if (code == 0) {
                val reply = json.optJSONObject("data")?.optString("reply").orEmpty()
                return if (reply.isBlank()) AiChatResult.Failure(AiErrorCode.EMPTY)
                else AiChatResult.Success(reply)
            }
            return AiChatResult.Failure(
                when {
                    code == 403 -> AiErrorCode.AUTH
                    code == 429 -> AiErrorCode.RATE
                    code == 400 -> AiErrorCode.BAD_REQUEST
                    httpCode >= 500 || code >= 500 -> AiErrorCode.SERVER
                    else -> AiErrorCode.NETWORK
                },
            )
        }
    }
}
