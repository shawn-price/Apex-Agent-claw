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
import java.util.UUID
import java.util.concurrent.TimeUnit

class AnthropicAdapter(
    override val providerConfig: LlmProviderConfig
) : LlmAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun testConnection(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (providerConfig.apiKey.isBlank()) return@withContext Pair(false, -1L)
        try {
            val url = "https://api.anthropic.com/v1/messages"
            val testBody = JSONObject().apply {
                put("model", providerConfig.defaultModel.ifBlank { "claude-3-5-haiku-20241022" })
                put("max_tokens", 5)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", providerConfig.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(testBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

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
        val url = "https://api.anthropic.com/v1/messages"
        val requestJson = buildRequestBody(messages, settings, stream = false)

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", providerConfig.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "Claude Error (${response.code}): $responseBody"
                )
            }

            val json = JSONObject(responseBody)
            val contentArr = json.optJSONArray("content")
            val textBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()

            if (contentArr != null) {
                for (i in 0 until contentArr.length()) {
                    val block = contentArr.getJSONObject(i)
                    if (block.optString("type") == "text") {
                        textBuilder.append(block.optString("text"))
                    } else if (block.optString("type") == "tool_use") {
                        toolCalls.add(
                            ToolCall(
                                id = block.optString("id", UUID.randomUUID().toString()),
                                name = block.optString("name", "tool"),
                                arguments = block.optJSONObject("input")?.toString() ?: "{}"
                            )
                        )
                    }
                }
            }

            val usage = json.optJSONObject("usage")
            val totalTokens = (usage?.optInt("input_tokens", 0) ?: 0) + (usage?.optInt("output_tokens", 0) ?: 0)

            LlmGenerateResult(
                content = textBuilder.toString(),
                toolCalls = toolCalls,
                totalTokens = totalTokens,
                isSuccess = true
            )
        } catch (e: Exception) {
            LlmGenerateResult(
                content = "",
                isSuccess = false,
                errorMessage = "Network exception: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun generateStream(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String?,
        onChunk: (deltaText: String, deltaThinking: String?) -> Unit
    ): LlmGenerateResult = withContext(Dispatchers.IO) {
        val url = "https://api.anthropic.com/v1/messages"
        val requestJson = buildRequestBody(messages, settings, stream = true)

        val fullContent = StringBuilder()
        val fullThinking = StringBuilder()
        val extractedToolCalls = mutableListOf<ToolCall>()

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", providerConfig.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "Claude Stream Error (${response.code}): $err"
                )
            }

            val source = response.body?.source() ?: return@withContext LlmGenerateResult("", isSuccess = false)
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data.isNotBlank()) {
                        try {
                            val chunk = JSONObject(data)
                            val type = chunk.optString("type")
                            if (type == "content_block_delta") {
                                val delta = chunk.optJSONObject("delta")
                                val text = delta?.optString("text")
                                if (!text.isNullOrEmpty()) {
                                    fullContent.append(text)
                                    onChunk(text, null)
                                }
                                val thinking = delta?.optString("thinking")
                                if (!thinking.isNullOrEmpty()) {
                                    fullThinking.append(thinking)
                                    onChunk("", thinking)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore parse glitch
                        }
                    }
                }
            }

            LlmGenerateResult(
                content = fullContent.toString(),
                thinking = if (fullThinking.isNotEmpty()) fullThinking.toString() else null,
                toolCalls = extractedToolCalls,
                isSuccess = true
            )
        } catch (e: Exception) {
            LlmGenerateResult(
                content = fullContent.toString(),
                isSuccess = fullContent.isNotEmpty(),
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun buildRequestBody(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        stream: Boolean
    ): JSONObject {
        val root = JSONObject()
        val model = settings.activeModelId.ifBlank { providerConfig.defaultModel }
        root.put("model", model)
        root.put("max_tokens", if (settings.maxTokens > 0) settings.maxTokens else 4096)
        root.put("stream", stream)

        if (settings.systemPrompt.isNotBlank()) {
            root.put("system", settings.systemPrompt)
        }

        val messagesArr = JSONArray()
        for (m in messages) {
            val role = if (m.role == "assistant") "assistant" else "user"
            messagesArr.put(JSONObject().apply {
                put("role", role)
                put("content", m.content)
            })
        }
        root.put("messages", messagesArr)

        root.put("temperature", settings.temperature.toDouble())
        root.put("top_p", settings.topP.toDouble())

        if (settings.stopSequences.isNotEmpty()) {
            root.put("stop_sequences", JSONArray(settings.stopSequences))
        }

        return root
    }
}
