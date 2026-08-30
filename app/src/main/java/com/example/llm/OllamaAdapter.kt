package com.example.llm

import com.example.storage.LlmGenerationSettings
import com.example.storage.LlmProviderConfig
import com.example.storage.Message
import com.example.storage.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OllamaAdapter(
    override val providerConfig: LlmProviderConfig
) : LlmAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun testConnection(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = "${providerConfig.baseUrl.trimEnd('/')}/api/tags"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            Pair(response.isSuccessful, latency)
        } catch (e: Exception) {
            Pair(false, -1L)
        }
    }

    override suspend fun generate(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String?
    ): LlmGenerateResult = withContext(Dispatchers.IO) {
        val url = "${providerConfig.baseUrl.trimEnd('/')}/api/chat"
        val body = buildOllamaBody(messages, settings, stream = false)

        try {
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "Ollama Error (${response.code}): $responseBody"
                )
            }

            val json = JSONObject(responseBody)
            val msg = json.optJSONObject("message")
            val content = msg?.optString("content", "") ?: ""

            LlmGenerateResult(
                content = content,
                isSuccess = true
            )
        } catch (e: Exception) {
            LlmGenerateResult(
                content = "",
                isSuccess = false,
                errorMessage = "Ollama Connection Failed: Ensure Ollama is running on ${providerConfig.baseUrl}. (${e.localizedMessage})"
            )
        }
    }

    override suspend fun generateStream(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String?,
        onChunk: (deltaText: String, deltaThinking: String?) -> Unit
    ): LlmGenerateResult = withContext(Dispatchers.IO) {
        val url = "${providerConfig.baseUrl.trimEnd('/')}/api/chat"
        val body = buildOllamaBody(messages, settings, stream = true)

        val fullContent = StringBuilder()

        try {
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "Ollama Stream Error (${response.code}): $err"
                )
            }

            val source = response.body?.source() ?: return@withContext LlmGenerateResult("", isSuccess = false)
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isNotBlank()) {
                    try {
                        val obj = JSONObject(line)
                        val msg = obj.optJSONObject("message")
                        val text = msg?.optString("content")
                        if (!text.isNullOrEmpty()) {
                            fullContent.append(text)
                            onChunk(text, null)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            LlmGenerateResult(
                content = fullContent.toString(),
                isSuccess = true
            )
        } catch (e: Exception) {
            LlmGenerateResult(
                content = fullContent.toString(),
                isSuccess = fullContent.isNotEmpty(),
                errorMessage = "Ollama Stream Exception: ${e.localizedMessage}"
            )
        }
    }

    private fun buildOllamaBody(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        stream: Boolean
    ): JSONObject {
        val root = JSONObject()
        val model = settings.activeModelId.ifBlank { providerConfig.defaultModel.ifBlank { "llama3.2" } }
        root.put("model", model)
        root.put("stream", stream)

        val messagesArr = JSONArray()
        if (settings.systemPrompt.isNotBlank()) {
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", settings.systemPrompt)
            })
        }
        for (m in messages) {
            messagesArr.put(JSONObject().apply {
                put("role", if (m.role == "assistant") "assistant" else "user")
                put("content", m.content)
            })
        }
        root.put("messages", messagesArr)

        val options = JSONObject().apply {
            put("temperature", settings.temperature.toDouble())
            put("top_p", settings.topP.toDouble())
            put("top_k", settings.topK)
            if (settings.stopSequences.isNotEmpty()) {
                put("stop", JSONArray(settings.stopSequences))
            }
        }
        root.put("options", options)

        if (settings.jsonModeEnabled) {
            root.put("format", "json")
        }

        return root
    }
}
