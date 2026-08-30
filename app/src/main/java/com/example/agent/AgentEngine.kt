package com.example.agent

import com.example.llm.LlmGenerateResult
import com.example.llm.UniversalLlmManager
import com.example.storage.EncryptedStorageManager
import com.example.storage.Message
import com.example.storage.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class AgentEngine(
    private val storageManager: EncryptedStorageManager,
    private val llmManager: UniversalLlmManager,
    private val agentTools: AgentTools
) {

    suspend fun processUserMessage(
        conversationId: String,
        userText: String,
        isNetworkAvailable: Boolean,
        onDelta: (deltaText: String, deltaThinking: String?) -> Unit,
        onToolStarted: (ToolCall) -> Unit,
        onToolFinished: (ToolCall) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): Message = withContext(Dispatchers.IO) {
        val userMsg = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "user",
            content = userText
        )
        storageManager.addMessage(conversationId, userMsg)

        val allMessages = storageManager.getMessages(conversationId)
        val executedToolCalls = mutableListOf<ToolCall>()

        // Check if user prompt is a direct intent triggering tool execution
        val directTool = detectDirectToolIntent(userText)
        if (directTool != null) {
            onToolStarted(directTool)
            onStatusUpdate("Executing tool: ${directTool.name}...")
            val (result, isError) = agentTools.executeTool(directTool)
            val updatedTool = directTool.copy(
                status = if (isError) "error" else "success",
                output = result
            )
            executedToolCalls.add(updatedTool)
            onToolFinished(updatedTool)
        }

        // Invoke Universal LLM Stream
        onStatusUpdate("Agent generating response...")
        val responseBuilder = StringBuilder()
        val thinkingBuilder = StringBuilder()

        val llmResult = llmManager.generateStream(
            messages = allMessages,
            isNetworkAvailable = isNetworkAvailable,
            onChunk = { deltaText, deltaThinking ->
                if (deltaText.isNotEmpty()) {
                    responseBuilder.append(deltaText)
                    onDelta(deltaText, null)
                }
                if (!deltaThinking.isNullOrEmpty()) {
                    thinkingBuilder.append(deltaThinking)
                    onDelta("", deltaThinking)
                }
            },
            onFallbackTriggered = { reason, fallbackModel ->
                onStatusUpdate("Routing: $reason (Model: $fallbackModel)")
            }
        )

        // Process any tool calls requested by the model
        for (tc in llmResult.toolCalls) {
            onToolStarted(tc)
            onStatusUpdate("Executing requested tool: ${tc.name}...")
            val (res, isErr) = agentTools.executeTool(tc)
            val finishedTc = tc.copy(
                status = if (isErr) "error" else "success",
                output = res
            )
            executedToolCalls.add(finishedTc)
            onToolFinished(finishedTc)
        }

        // If direct tool was run and LLM response was short or empty, provide detailed summary
        val finalContent = if (responseBuilder.isEmpty() && executedToolCalls.isNotEmpty()) {
            val tc = executedToolCalls.first()
            "I've executed `${tc.name}` for you.\n\n**Output:**\n```\n${tc.output}\n```"
        } else {
            responseBuilder.toString().ifBlank { llmResult.content }
        }

        val assistantMsg = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "assistant",
            content = finalContent,
            thinkingReasoning = if (thinkingBuilder.isNotEmpty()) thinkingBuilder.toString() else llmResult.thinking,
            toolCalls = executedToolCalls
        )

        storageManager.addMessage(conversationId, assistantMsg)
        assistantMsg
    }

    private fun detectDirectToolIntent(text: String): ToolCall? {
        val lower = text.lowercase().trim()

        // Code execution pattern
        if (lower.startsWith("run:") || lower.startsWith("python:") || lower.startsWith("js:") ||
            (lower.contains("```python") || lower.contains("```javascript") || lower.contains("```js"))
        ) {
            val lang = if (lower.contains("js") || lower.contains("javascript")) "javascript" else "python"
            val code = if (text.contains("```")) {
                text.substringAfter("```").substringAfter("\n").substringBefore("```")
            } else if (text.contains(":")) {
                text.substringAfter(":")
            } else {
                text
            }
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "code_execution",
                arguments = JSONObject().apply {
                    put("language", lang)
                    put("code", code.trim())
                }.toString()
            )
        }

        // Web search pattern
        if (lower.startsWith("search:") || lower.startsWith("search web for") || lower.startsWith("google:")) {
            val q = text.substringAfter("for").substringAfter(":").trim()
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "web_search",
                arguments = JSONObject().apply { put("query", q) }.toString()
            )
        }

        // Storage stats pattern
        if (lower == "storage stats" || lower == "storage" || lower.contains("storage health")) {
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "get_storage_stats",
                arguments = "{}"
            )
        }

        // List files pattern
        if (lower == "list files" || lower == "ls" || lower == "workspace files") {
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "file_list",
                arguments = "{}"
            )
        }

        // Communication channel patterns
        if (lower.startsWith("telegram:") || lower.startsWith("send telegram:") || lower.startsWith("tg:")) {
            val msg = text.substringAfter(":").trim()
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "send_telegram",
                arguments = JSONObject().apply {
                    put("message", msg)
                }.toString()
            )
        }

        if (lower.startsWith("whatsapp:") || lower.startsWith("send whatsapp:") || lower.startsWith("wa:")) {
            val msg = text.substringAfter(":").trim()
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "send_whatsapp",
                arguments = JSONObject().apply {
                    put("message", msg)
                }.toString()
            )
        }

        if (lower.startsWith("sms:") || lower.startsWith("send sms:")) {
            val msg = text.substringAfter(":").trim()
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "send_sms",
                arguments = JSONObject().apply {
                    put("message", msg)
                }.toString()
            )
        }

        if (lower.startsWith("email:") || lower.startsWith("send email:")) {
            val msg = text.substringAfter(":").trim()
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "send_email",
                arguments = JSONObject().apply {
                    put("subject", "OpenClaw AI Dispatch")
                    put("body", msg)
                }.toString()
            )
        }

        if (lower.startsWith("broadcast:") || lower.startsWith("broadcast alert:") || lower.startsWith("alert all:")) {
            val msg = text.substringAfter(":").trim()
            return ToolCall(
                id = UUID.randomUUID().toString(),
                name = "broadcast_alert",
                arguments = JSONObject().apply {
                    put("title", "High Priority System Alert")
                    put("message", msg)
                }.toString()
            )
        }

        return null
    }
}
