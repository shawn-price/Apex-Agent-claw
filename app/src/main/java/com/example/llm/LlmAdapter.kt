package com.example.llm

import com.example.storage.LlmGenerationSettings
import com.example.storage.LlmProviderConfig
import com.example.storage.Message
import com.example.storage.ToolCall

data class LlmGenerateResult(
    val content: String,
    val thinking: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val totalTokens: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

interface LlmAdapter {
    val providerConfig: LlmProviderConfig

    suspend fun generateStream(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String? = null,
        onChunk: (deltaText: String, deltaThinking: String?) -> Unit
    ): LlmGenerateResult

    suspend fun generate(
        messages: List<Message>,
        settings: LlmGenerationSettings,
        availableToolsJson: String? = null
    ): LlmGenerateResult

    suspend fun testConnection(): Pair<Boolean, Long> // isSuccess to latencyMs
}
