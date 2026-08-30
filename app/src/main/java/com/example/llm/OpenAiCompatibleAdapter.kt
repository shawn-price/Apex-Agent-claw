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

class OpenAiCompatibleAdapter(
    override val providerConfig: LlmProviderConfig
) : LlmAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getEndpointUrl(): String {
        var base = providerConfig.baseUrl.trimEnd('/')
        return if (base.endsWith("/chat/completions")) {
            base
        } else {
            "$base/chat/completions"
        }
    }

    override suspend fun testConnection(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = getEndpointUrl()
            val testBody = JSONObject().apply {
                put("model", providerConfig.defaultModel.ifBlank { "default" })
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
                put("max_tokens", 5)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(testBody.toString().toRequestBody("application/json".toMediaType()))

            if (providerConfig.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${providerConfig.apiKey}")
            }
            providerConfig.customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
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
        val url = getEndpointUrl()
        val requestJson = buildRequestBody(messages, settings, stream = false)

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))

            if (providerConfig.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${providerConfig.apiKey}")
            }
            providerConfig.customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "${providerConfig.name} Error (${response.code}): $responseBody"
                )
            }

            parseResponse(responseBody)
        } catch (e: Exception) {
            LlmGenerateResult(
                content = "",
                isSuccess = false,
                errorMessage = "Network Exception: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    override suspend fun generateStream(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String?,
        onChunk: (deltaText: String, deltaThinking: String?) -> Unit
    ): LlmGenerateResult = withContext(Dispatchers.IO) {
        val url = getEndpointUrl()
        val requestJson = buildRequestBody(messages, settings, stream = true)

        val fullContent = StringBuilder()
        val fullThinking = StringBuilder()
        val extractedToolCalls = mutableListOf<ToolCall>()

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))

            if (providerConfig.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${providerConfig.apiKey}")
            }
            providerConfig.customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "${providerConfig.name} Stream Error (${response.code}): $err"
                )
            }

            val source = response.body?.source() ?: return@withContext LlmGenerateResult("", isSuccess = false)
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break
                    if (data.isNotBlank()) {
                        try {
                            val chunk = JSONObject(data)
                            val choices = chunk.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")
                                if (delta != null) {
                                    // Text content
                                    if (delta.has("content") && !delta.isNull("content")) {
                                        val text = delta.getString("content")
                                        fullContent.append(text)
                                        onChunk(text, null)
                                    }
                                    // Reasoning/Thinking content (DeepSeek-R1 / Groq / OpenAI)
                                    if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                                        val reason = delta.getString("reasoning_content")
                                        fullThinking.append(reason)
                                        onChunk("", reason)
                                    }
                                    // Tool Calls
                                    if (delta.has("tool_calls")) {
                                        val tcArr = delta.getJSONArray("tool_calls")
                                        for (i in 0 until tcArr.length()) {
                                            val tcObj = tcArr.getJSONObject(i)
                                            val fn = tcObj.optJSONObject("function")
                                            if (fn != null) {
                                                extractedToolCalls.add(
                                                    ToolCall(
                                                        id = tcObj.optString("id", UUID.randomUUID().toString()),
                                                        name = fn.optString("name", "tool"),
                                                        arguments = fn.optString("arguments", "{}")
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Continue stream
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
                thinking = if (fullThinking.isNotEmpty()) fullThinking.toString() else null,
                toolCalls = extractedToolCalls,
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
        root.put("stream", stream)

        val messagesArray = JSONArray()

        // System prompt
        if (settings.systemPrompt.isNotBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", settings.systemPrompt)
            })
        }

        // Chat turns
        for (m in messages) {
            val role = when (m.role) {
                "assistant" -> "assistant"
                "system" -> "system"
                "tool" -> "tool"
                else -> "user"
            }
            messagesArray.put(JSONObject().apply {
                put("role", role)
                put("content", m.content)
            })
        }
        root.put("messages", messagesArray)

        // Parameters
        root.put("temperature", settings.temperature.toDouble())
        root.put("top_p", settings.topP.toDouble())
        if (settings.frequencyPenalty != 0.0f) root.put("frequency_penalty", settings.frequencyPenalty.toDouble())
        if (settings.presencePenalty != 0.0f) root.put("presence_penalty", settings.presencePenalty.toDouble())
        if (settings.maxTokens > 0) root.put("max_tokens", settings.maxTokens)

        if (settings.stopSequences.isNotEmpty()) {
            root.put("stop", JSONArray(settings.stopSequences))
        }

        if (settings.jsonModeEnabled) {
            root.put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        return root
    }

    private fun parseResponse(responseBody: String): LlmGenerateResult {
        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            return LlmGenerateResult(
                content = "",
                isSuccess = false,
                errorMessage = "No choices in response: $responseBody"
            )
        }

        val first = choices.getJSONObject(0)
        val msg = first.optJSONObject("message")
        val content = msg?.optString("content", "") ?: ""
        val thinking = msg?.optString("reasoning_content", null)

        val toolCalls = mutableListOf<ToolCall>()
        val tcArr = msg?.optJSONArray("tool_calls")
        if (tcArr != null) {
            for (i in 0 until tcArr.length()) {
                val tcObj = tcArr.getJSONObject(i)
                val fn = tcObj.optJSONObject("function")
                if (fn != null) {
                    toolCalls.add(
                        ToolCall(
                            id = tcObj.optString("id", UUID.randomUUID().toString()),
                            name = fn.optString("name", "tool"),
                            arguments = fn.optString("arguments", "{}")
                        )
                    )
                }
            }
        }

        val usage = json.optJSONObject("usage")
        val totalTokens = usage?.optInt("total_tokens", 0) ?: 0

        return LlmGenerateResult(
            content = content,
            thinking = thinking,
            toolCalls = toolCalls,
            totalTokens = totalTokens,
            isSuccess = true
        )
    }
}
