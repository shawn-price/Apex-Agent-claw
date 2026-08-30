package com.example.storage

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConversationSummary(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val modelUsed: String = "gemini-3.5-flash",
    val providerId: String = "gemini",
    val messageCount: Int = 0,
    val totalTokens: Int = 0,
    val isPinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val monthPartition: String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(updatedAt))
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("lastMessage", lastMessage)
        put("modelUsed", modelUsed)
        put("providerId", providerId)
        put("messageCount", messageCount)
        put("totalTokens", totalTokens)
        put("isPinned", isPinned)
        put("tags", JSONArray(tags))
        put("monthPartition", monthPartition)
    }

    companion object {
        fun fromJson(json: JSONObject): ConversationSummary {
            val tagsList = mutableListOf<String>()
            val tagsArray = json.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(i))
                }
            }
            val uAt = json.optLong("updatedAt", System.currentTimeMillis())
            val mp = json.optString("monthPartition", SimpleDateFormat("yyyy-MM", Locale.US).format(Date(uAt)))
            return ConversationSummary(
                id = json.getString("id"),
                title = json.optString("title", "New Conversation"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = uAt,
                lastMessage = json.optString("lastMessage", ""),
                modelUsed = json.optString("modelUsed", "gemini-3.5-flash"),
                providerId = json.optString("providerId", "gemini"),
                messageCount = json.optInt("messageCount", 0),
                totalTokens = json.optInt("totalTokens", 0),
                isPinned = json.optBoolean("isPinned", false),
                tags = tagsList,
                monthPartition = mp
            )
        }
    }
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val status: String = "pending", // pending, running, success, error
    val output: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("arguments", arguments)
        put("status", status)
        if (output != null) put("output", output)
    }

    companion object {
        fun fromJson(json: JSONObject): ToolCall = ToolCall(
            id = json.optString("id", ""),
            name = json.optString("name", ""),
            arguments = json.optString("arguments", "{}"),
            status = json.optString("status", "pending"),
            output = if (json.has("output")) json.getString("output") else null
        )
    }
}

data class Message(
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val thinkingReasoning: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val monthPartition: String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp))
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("conversationId", conversationId)
        put("role", role)
        put("content", content)
        if (thinkingReasoning != null) put("thinkingReasoning", thinkingReasoning)
        val toolCallsArray = JSONArray()
        toolCalls.forEach { toolCallsArray.put(it.toJson()) }
        put("toolCalls", toolCallsArray)
        put("timestamp", timestamp)
        put("tokenCount", tokenCount)
        put("monthPartition", monthPartition)
    }

    companion object {
        fun fromJson(json: JSONObject): Message {
            val toolCallsList = mutableListOf<ToolCall>()
            val tcArray = json.optJSONArray("toolCalls")
            if (tcArray != null) {
                for (i in 0 until tcArray.length()) {
                    toolCallsList.add(ToolCall.fromJson(tcArray.getJSONObject(i)))
                }
            }
            val ts = json.optLong("timestamp", System.currentTimeMillis())
            val mp = json.optString("monthPartition", SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ts)))
            return Message(
                id = json.getString("id"),
                conversationId = json.optString("conversationId", ""),
                role = json.optString("role", "user"),
                content = json.optString("content", ""),
                thinkingReasoning = if (json.has("thinkingReasoning")) json.getString("thinkingReasoning") else null,
                toolCalls = toolCallsList,
                timestamp = ts,
                tokenCount = json.optInt("tokenCount", 0),
                monthPartition = mp
            )
        }
    }
}

data class ScheduledTask(
    val id: String,
    val title: String,
    val prompt: String,
    val cronExpression: String = "",
    val intervalMinutes: Int = 0, // e.g. 60 min, 0 for one-shot
    val isRecurring: Boolean = false,
    val isEnabled: Boolean = true,
    val nextRunMillis: Long = 0L,
    val lastRunMillis: Long = 0L,
    val lastStatus: String = "idle", // idle, running, success, failed
    val lastOutput: String = "",
    val notifyUser: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("prompt", prompt)
        put("cronExpression", cronExpression)
        put("intervalMinutes", intervalMinutes)
        put("isRecurring", isRecurring)
        put("isEnabled", isEnabled)
        put("nextRunMillis", nextRunMillis)
        put("lastRunMillis", lastRunMillis)
        put("lastStatus", lastStatus)
        put("lastOutput", lastOutput)
        put("notifyUser", notifyUser)
    }

    companion object {
        fun fromJson(json: JSONObject): ScheduledTask = ScheduledTask(
            id = json.getString("id"),
            title = json.optString("title", "Untitled Task"),
            prompt = json.optString("prompt", ""),
            cronExpression = json.optString("cronExpression", ""),
            intervalMinutes = json.optInt("intervalMinutes", 0),
            isRecurring = json.optBoolean("isRecurring", false),
            isEnabled = json.optBoolean("isEnabled", true),
            nextRunMillis = json.optLong("nextRunMillis", 0L),
            lastRunMillis = json.optLong("lastRunMillis", 0L),
            lastStatus = json.optString("lastStatus", "idle"),
            lastOutput = json.optString("lastOutput", ""),
            notifyUser = json.optBoolean("notifyUser", true)
        )
    }
}

data class TaskLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // "INFO", "TOOL", "WARN", "ERROR", "STEP", "STATUS"
    val message: String,
    val details: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("level", level)
        put("message", message)
        if (details != null) put("details", details)
    }

    companion object {
        fun fromJson(json: JSONObject): TaskLogEntry = TaskLogEntry(
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            level = json.optString("level", "INFO"),
            message = json.optString("message", ""),
            details = if (json.has("details")) json.getString("details") else null
        )
    }
}

data class AgentTaskExecution(
    val id: String,
    val taskId: String? = null,
    val title: String,
    val prompt: String,
    val status: String = "pending", // "pending", "running", "completed", "failed", "cancelled"
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val durationMs: Long = 0L,
    val modelUsed: String = "gemini-3.5-flash",
    val triggerType: String = "Cron Schedule", // "Cron Schedule", "Manual Run", "Autonomous Agent", "Webhook Relay", "Direct Prompt"
    val toolCallsCount: Int = 0,
    val outputSummary: String? = null,
    val errorMessage: String? = null,
    val progressPercent: Float = 0f,
    val currentStep: String? = null,
    val cancellationReason: String? = null,
    val logs: List<TaskLogEntry> = emptyList()
) {
    val isCancellable: Boolean
        get() = status.equals("pending", ignoreCase = true) || status.equals("running", ignoreCase = true)

    fun getEffectiveLogs(): List<TaskLogEntry> {
        if (logs.isNotEmpty()) return logs

        val synthetic = mutableListOf<TaskLogEntry>()
        val start = startedAt
        synthetic.add(
            TaskLogEntry(
                timestamp = start,
                level = "INFO",
                message = "Task initialized and scheduled via $triggerType",
                details = "Model: $modelUsed • ID: $id"
            )
        )

        if (status.equals("pending", ignoreCase = true)) {
            synthetic.add(
                TaskLogEntry(
                    timestamp = start + 50,
                    level = "STEP",
                    message = "Queued in agent worker pipeline",
                    details = currentStep ?: "Awaiting available execution worker slot..."
                )
            )
        } else {
            synthetic.add(
                TaskLogEntry(
                    timestamp = start + 80,
                    level = "INFO",
                    message = "Agent execution context loaded",
                    details = "Attached prompt (${prompt.length} chars). LLM backend: $modelUsed"
                )
            )

            if (toolCallsCount > 0) {
                for (i in 1..toolCallsCount) {
                    val toolOffset = (start + (i * 450L)).coerceAtMost(finishedAt ?: (start + 1000L))
                    synthetic.add(
                        TaskLogEntry(
                            timestamp = toolOffset,
                            level = "TOOL",
                            message = "Agent invoked autonomous tool (#$i)",
                            details = if (i == 1) "WebSearchEngine: executed web search & source retrieval" else "Subsystem operation & structured response parsing"
                        )
                    )
                }
            }

            if (currentStep != null && !status.equals("pending", ignoreCase = true)) {
                val stepOffset = (start + 600L).coerceAtMost(finishedAt ?: (start + 1200L))
                synthetic.add(
                    TaskLogEntry(
                        timestamp = stepOffset,
                        level = "STEP",
                        message = "Execution progress step",
                        details = currentStep
                    )
                )
            }

            when (status.lowercase()) {
                "completed", "success" -> {
                    val end = finishedAt ?: (start + durationMs)
                    synthetic.add(
                        TaskLogEntry(
                            timestamp = end,
                            level = "INFO",
                            message = "Task finished successfully in ${durationMs}ms",
                            details = outputSummary?.take(200) ?: "Output generated successfully."
                        )
                    )
                }
                "failed", "error" -> {
                    val end = finishedAt ?: (start + durationMs)
                    synthetic.add(
                        TaskLogEntry(
                            timestamp = end,
                            level = "ERROR",
                            message = "Task execution failed with error",
                            details = errorMessage ?: "Unexpected runtime exception occurred."
                        )
                    )
                }
                "cancelled" -> {
                    val end = finishedAt ?: (start + durationMs)
                    synthetic.add(
                        TaskLogEntry(
                            timestamp = end,
                            level = "WARN",
                            message = "Task was cancelled",
                            details = cancellationReason ?: "Cancelled by user action."
                        )
                    )
                }
                "running" -> {
                    synthetic.add(
                        TaskLogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = "STATUS",
                            message = "Task currently running",
                            details = currentStep ?: "Processing agent loop..."
                        )
                    )
                }
            }
        }
        return synthetic
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        if (taskId != null) put("taskId", taskId)
        put("title", title)
        put("prompt", prompt)
        put("status", status)
        put("startedAt", startedAt)
        if (finishedAt != null) put("finishedAt", finishedAt)
        put("durationMs", durationMs)
        put("modelUsed", modelUsed)
        put("triggerType", triggerType)
        put("toolCallsCount", toolCallsCount)
        if (outputSummary != null) put("outputSummary", outputSummary)
        if (errorMessage != null) put("errorMessage", errorMessage)
        put("progressPercent", progressPercent.toDouble())
        if (currentStep != null) put("currentStep", currentStep)
        if (cancellationReason != null) put("cancellationReason", cancellationReason)
        if (logs.isNotEmpty()) {
            val logsArray = JSONArray()
            logs.forEach { logsArray.put(it.toJson()) }
            put("logs", logsArray)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AgentTaskExecution {
            val logsList = mutableListOf<TaskLogEntry>()
            if (json.has("logs")) {
                val array = json.getJSONArray("logs")
                for (i in 0 until array.length()) {
                    logsList.add(TaskLogEntry.fromJson(array.getJSONObject(i)))
                }
            }

            return AgentTaskExecution(
                id = json.getString("id"),
                taskId = if (json.has("taskId")) json.getString("taskId") else null,
                title = json.optString("title", "Automated Task"),
                prompt = json.optString("prompt", ""),
                status = json.optString("status", "pending"),
                startedAt = json.optLong("startedAt", System.currentTimeMillis()),
                finishedAt = if (json.has("finishedAt")) json.getLong("finishedAt") else null,
                durationMs = json.optLong("durationMs", 0L),
                modelUsed = json.optString("modelUsed", "gemini-3.5-flash"),
                triggerType = json.optString("triggerType", "Cron Schedule"),
                toolCallsCount = json.optInt("toolCallsCount", 0),
                outputSummary = if (json.has("outputSummary")) json.getString("outputSummary") else null,
                errorMessage = if (json.has("errorMessage")) json.getString("errorMessage") else null,
                progressPercent = json.optDouble("progressPercent", 0.0).toFloat(),
                currentStep = if (json.has("currentStep")) json.getString("currentStep") else null,
                cancellationReason = if (json.has("cancellationReason")) json.getString("cancellationReason") else null,
                logs = logsList
            )
        }
    }
}

data class LlmProviderConfig(
    val id: String,
    val name: String,
    val type: String, // gemini, openai, claude, azure, bedrock, groq, together, perplexity, mistral, deepseek, openrouter, cerebras, fireworks, xai, ollama, lmstudio, jan, localai, vllm, koboldcpp, custom
    val baseUrl: String,
    val apiKey: String = "",
    val isEnabled: Boolean = true,
    val isLocal: Boolean = false,
    val defaultModel: String,
    val availableModels: List<String>,
    val customHeaders: Map<String, String> = emptyMap(),
    val latencyMs: Long = -1L,
    val isOnline: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("baseUrl", baseUrl)
        put("apiKey", apiKey)
        put("isEnabled", isEnabled)
        put("isLocal", isLocal)
        put("defaultModel", defaultModel)
        put("availableModels", JSONArray(availableModels))
        val headersObj = JSONObject()
        customHeaders.forEach { (k, v) -> headersObj.put(k, v) }
        put("customHeaders", headersObj)
        put("latencyMs", latencyMs)
        put("isOnline", isOnline)
    }

    companion object {
        fun fromJson(json: JSONObject): LlmProviderConfig {
            val models = mutableListOf<String>()
            val arr = json.optJSONArray("availableModels")
            if (arr != null) {
                for (i in 0 until arr.length()) models.add(arr.getString(i))
            }
            val headers = mutableMapOf<String, String>()
            val hObj = json.optJSONObject("customHeaders")
            if (hObj != null) {
                hObj.keys().forEach { k -> headers[k] = hObj.getString(k) }
            }
            return LlmProviderConfig(
                id = json.getString("id"),
                name = json.optString("name", ""),
                type = json.optString("type", "openai"),
                baseUrl = json.optString("baseUrl", ""),
                apiKey = json.optString("apiKey", ""),
                isEnabled = json.optBoolean("isEnabled", true),
                isLocal = json.optBoolean("isLocal", false),
                defaultModel = json.optString("defaultModel", ""),
                availableModels = models,
                customHeaders = headers,
                latencyMs = json.optLong("latencyMs", -1L),
                isOnline = json.optBoolean("isOnline", true)
            )
        }
    }
}

data class LlmGenerationSettings(
    val activeProviderId: String = "gemini",
    val activeModelId: String = "gemini-3.5-flash",
    val fallbackProviderId: String = "ollama",
    val fallbackModelId: String = "llama3.2",
    val autoOfflineFallback: Boolean = true,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val maxTokens: Int = 4096,
    val stopSequences: List<String> = emptyList(),
    val streamingEnabled: Boolean = true,
    val jsonModeEnabled: Boolean = false,
    val systemPrompt: String = "You are OpenClaw (nanobot), an advanced autonomous AI agent with local tool calling, file operations, web research, and offline fallback capabilities.",
    val thinkingBudget: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("activeProviderId", activeProviderId)
        put("activeModelId", activeModelId)
        put("fallbackProviderId", fallbackProviderId)
        put("fallbackModelId", fallbackModelId)
        put("autoOfflineFallback", autoOfflineFallback)
        put("temperature", temperature.toDouble())
        put("topP", topP.toDouble())
        put("topK", topK)
        put("frequencyPenalty", frequencyPenalty.toDouble())
        put("presencePenalty", presencePenalty.toDouble())
        put("maxTokens", maxTokens)
        put("stopSequences", JSONArray(stopSequences))
        put("streamingEnabled", streamingEnabled)
        put("jsonModeEnabled", jsonModeEnabled)
        put("systemPrompt", systemPrompt)
        put("thinkingBudget", thinkingBudget)
    }

    companion object {
        fun fromJson(json: JSONObject): LlmGenerationSettings {
            val stops = mutableListOf<String>()
            val sArr = json.optJSONArray("stopSequences")
            if (sArr != null) {
                for (i in 0 until sArr.length()) stops.add(sArr.getString(i))
            }
            return LlmGenerationSettings(
                activeProviderId = json.optString("activeProviderId", "gemini"),
                activeModelId = json.optString("activeModelId", "gemini-3.5-flash"),
                fallbackProviderId = json.optString("fallbackProviderId", "ollama"),
                fallbackModelId = json.optString("fallbackModelId", "llama3.2"),
                autoOfflineFallback = json.optBoolean("autoOfflineFallback", true),
                temperature = json.optDouble("temperature", 0.7).toFloat(),
                topP = json.optDouble("topP", 0.95).toFloat(),
                topK = json.optInt("topK", 40),
                frequencyPenalty = json.optDouble("frequencyPenalty", 0.0).toFloat(),
                presencePenalty = json.optDouble("presencePenalty", 0.0).toFloat(),
                maxTokens = json.optInt("maxTokens", 4096),
                stopSequences = stops,
                streamingEnabled = json.optBoolean("streamingEnabled", json.optBoolean("streamEnabled", true)),
                jsonModeEnabled = json.optBoolean("jsonModeEnabled", false),
                systemPrompt = json.optString(
                    "systemPrompt",
                    "You are OpenClaw (nanobot), an advanced autonomous AI agent with local tool calling, file operations, web research, and offline fallback capabilities."
                ),
                thinkingBudget = json.optInt("thinkingBudget", 0)
            )
        }
    }
}

data class WorkspaceFileInfo(
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean
)
