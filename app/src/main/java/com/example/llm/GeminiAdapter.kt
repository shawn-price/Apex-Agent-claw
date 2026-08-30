package com.example.llm

import com.example.BuildConfig
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

class GeminiAdapter(
    override val providerConfig: LlmProviderConfig
) : LlmAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getEffectiveApiKey(): String {
        return if (providerConfig.apiKey.isNotBlank()) {
            providerConfig.apiKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }
    }

    override suspend fun testConnection(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext Pair(false, -1L)

        val model = providerConfig.defaultModel.ifBlank { "gemini-3.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "ping") })
                    })
                })
            })
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
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
        val key = getEffectiveApiKey()
        val model = settings.activeModelId.ifBlank { providerConfig.defaultModel.ifBlank { "gemini-3.5-flash" } }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        val requestJson = buildGeminiRequestBody(messages, settings, availableToolsJson)

        try {
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "Gemini API Error (${response.code}): $responseBody"
                )
            }

            parseGeminiResponse(responseBody)
        } catch (e: Exception) {
            LlmGenerateResult(
                content = "",
                isSuccess = false,
                errorMessage = "Network exception: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    override suspend fun generateStream(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String?,
        onChunk: (deltaText: String, deltaThinking: String?) -> Unit
    ): LlmGenerateResult = withContext(Dispatchers.IO) {
        val key = getEffectiveApiKey()
        val model = settings.activeModelId.ifBlank { providerConfig.defaultModel.ifBlank { "gemini-3.5-flash" } }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$key"

        val requestJson = buildGeminiRequestBody(messages, settings, availableToolsJson)

        val fullContent = StringBuilder()
        val fullThinking = StringBuilder()
        val extractedToolCalls = mutableListOf<ToolCall>()

        try {
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                return@withContext LlmGenerateResult(
                    content = "",
                    isSuccess = false,
                    errorMessage = "Gemini Stream Error (${response.code}): $err"
                )
            }

            val source = response.body?.source() ?: return@withContext LlmGenerateResult("", isSuccess = false)
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val jsonStr = line.substring(6).trim()
                    if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                        try {
                            val chunkObj = JSONObject(jsonStr)
                            val candidates = chunkObj.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val cand = candidates.getJSONObject(0)
                                val contentObj = cand.optJSONObject("content")
                                val parts = contentObj?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val p = parts.getJSONObject(i)
                                        if (p.has("text")) {
                                            val t = p.getString("text")
                                            fullContent.append(t)
                                            onChunk(t, null)
                                        }
                                        if (p.has("functionCall")) {
                                            val fc = p.getJSONObject("functionCall")
                                            extractedToolCalls.add(
                                                ToolCall(
                                                    id = UUID.randomUUID().toString(),
                                                    name = fc.optString("name", "unknown_tool"),
                                                    arguments = fc.optJSONObject("args")?.toString() ?: "{}"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Continue streaming on parse glitch
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
                toolCalls = extractedToolCalls,
                isSuccess = fullContent.isNotEmpty(),
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun buildGeminiRequestBody(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String?
    ): JSONObject {
        val root = JSONObject()

        // System Instruction
        if (settings.systemPrompt.isNotBlank()) {
            root.put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", settings.systemPrompt) })
                })
            })
        }

        // Contents
        val contentsArray = JSONArray()
        for (m in messages) {
            val role = if (m.role == "assistant") "model" else "user"
            val partsArray = JSONArray()

            if (m.content.isNotBlank()) {
                partsArray.put(JSONObject().apply { put("text", m.content) })
            }

            for (tc in m.toolCalls) {
                if (tc.status == "success" && tc.output != null) {
                    partsArray.put(JSONObject().apply {
                        put("functionResponse", JSONObject().apply {
                            put("name", tc.name)
                            put("response", JSONObject().apply { put("result", tc.output) })
                        })
                    })
                }
            }

            if (partsArray.length() > 0) {
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", partsArray)
                })
            }
        }
        root.put("contents", contentsArray)

        // Generation Config
        val genConfig = JSONObject().apply {
            put("temperature", settings.temperature.toDouble())
            put("topP", settings.topP.toDouble())
            put("topK", settings.topK)
            if (settings.maxTokens > 0) put("maxOutputTokens", settings.maxTokens)
            if (settings.stopSequences.isNotEmpty()) {
                put("stopSequences", JSONArray(settings.stopSequences))
            }
            if (settings.jsonModeEnabled) {
                put("responseMimeType", "application/json")
            }
        }
        root.put("generationConfig", genConfig)

        return root
    }

    private fun parseGeminiResponse(responseBody: String): LlmGenerateResult {
        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return LlmGenerateResult(
                content = "",
                isSuccess = false,
                errorMessage = "No candidates returned from Gemini API"
            )
        }

        val cand = candidates.getJSONObject(0)
        val contentObj = cand.optJSONObject("content")
        val parts = contentObj?.optJSONArray("parts")

        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.has("text")) {
                    textBuilder.append(p.getString("text"))
                }
                if (p.has("functionCall")) {
                    val fc = p.getJSONObject("functionCall")
                    toolCalls.add(
                        ToolCall(
                            id = UUID.randomUUID().toString(),
                            name = fc.optString("name", "unknown_tool"),
                            arguments = fc.optJSONObject("args")?.toString() ?: "{}"
                        )
                    )
                }
            }
        }

        return LlmGenerateResult(
            content = textBuilder.toString(),
            toolCalls = toolCalls,
            isSuccess = true
        )
    }
}
