package com.example.storage

import android.content.Context
import com.example.BuildConfig
import com.example.channels.ChannelTransmissionLog
import com.example.channels.CommunicationChannelsConfig
import com.example.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class EncryptedStorageManager(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "nanobot").apply { mkdirs() }
    private val conversationsDir: File = File(rootDir, "conversations").apply { mkdirs() }
    private val tasksDir: File = File(rootDir, "tasks").apply { mkdirs() }
    private val agentsDir: File = File(rootDir, "agents").apply { mkdirs() }
    private val llmDir: File = File(rootDir, "llm").apply { mkdirs() }
    private val channelsDir: File = File(rootDir, "channels").apply { mkdirs() }
    private val workspaceDir: File = File(rootDir, "workspace").apply { mkdirs() }
    private val backupsDir: File = File(rootDir, "backups").apply { mkdirs() }

    // Room Database with Encrypted SQLite Helper (SQLCipher)
    private val appDb by lazy { AppDatabase.getDatabase(context) }
    val conversationDao by lazy { appDb.conversationDao() }
    val messageDao by lazy { appDb.messageDao() }
    val agentTaskDao by lazy { appDb.agentTaskExecutionDao() }
    val voiceTaskDao by lazy { appDb.voiceTaskDao() }

    // Memory caches for <50ms operations
    private var conversationsCache: MutableList<ConversationSummary>? = null
    private val messageCache = ConcurrentHashMap<String, MutableList<Message>>() // convId -> messages
    private var tasksCache: MutableList<ScheduledTask>? = null
    private var taskExecutionsCache: MutableList<AgentTaskExecution>? = null
    private var agentsCache: MutableList<AgentEntity>? = null
    private var providersCache: MutableList<LlmProviderConfig>? = null
    private var settingsCache: LlmGenerationSettings? = null
    private var channelsConfigCache: CommunicationChannelsConfig? = null
    private var transmissionLogsCache: MutableList<ChannelTransmissionLog>? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Ensure workspace spec file exists
        val specFile = File(workspaceDir, "OPENCLAW_MOBILE_DESIGN_SPEC.md")
        if (!specFile.exists()) {
            specFile.writeText(DEFAULT_DESIGN_SPEC)
        }

        // Preload caches
        getConversations()
        getTasks()
        getTaskExecutions()
        getAgents()
        getProviders()
        getSettings()
        getChannelsConfig()
        getTransmissionLogs()
    }

    // -------------------------------------------------------------
    // Generic Encrypted File Operations
    // -------------------------------------------------------------

    fun writeEncryptedFile(file: File, content: String) {
        file.parentFile?.mkdirs()
        val encrypted = CryptoManager.encrypt(content)
        file.writeBytes(encrypted)
    }

    fun readEncryptedFile(file: File): String? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            CryptoManager.decrypt(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun getRawEncryptedBytes(file: File): ByteArray? {
        if (!file.exists()) return null
        return file.readBytes()
    }

    // -------------------------------------------------------------
    // Conversations & Monthly Messages
    // -------------------------------------------------------------

    suspend fun getConversations(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        conversationsCache?.let { return@withContext it.toList() }

        val roomEntities = conversationDao.getAllConversations()
        val list = roomEntities.map { it.toDomain() }.toMutableList()

        if (list.isEmpty()) {
            // Seed initial conversation into encrypted Room DB
            val welcomeConv = ConversationSummary(
                id = "conv_welcome",
                title = "🦉 Welcome to OpenClaw",
                lastMessage = "Universal AI Agent ready with 15+ providers and encrypted Zero-DB storage.",
                modelUsed = "gemini-3.5-flash",
                providerId = "gemini",
                messageCount = 2,
                isPinned = true,
                tags = listOf("system", "welcome")
            )
            list.add(welcomeConv)
            conversationDao.insertOrUpdateConversation(ConversationEntity.fromDomain(welcomeConv))
            saveConversationsIndex(list)

            // Seed initial welcome message
            val welcomeMsg1 = Message(
                id = "msg_welcome_1",
                conversationId = "conv_welcome",
                role = "assistant",
                content = """
# 🦉 OpenClaw Agent Initialized

Welcome to **OpenClaw (nanobot)** — your autonomous mobile AI agent architecture.

### ✨ Highlights:
- **Universal LLM Framework**: Preconfigured for 15+ providers (Gemini, Claude, OpenAI, Groq, Ollama, LM Studio, etc.) with automatic offline fallback.
- **Encrypted Room Database**: All chat history, task logs, and agent states are encrypted via **SQLCipher** & **AES-256-GCM** Android KeyStore.
- **Autonomous Tool Execution**: Web search, sandbox file operations, Python/JS interpreter, and task scheduling.
- **Monthly Message Partitioning**: Fast indexing with zero memory bloat.

Try asking me to run code, search the web, schedule reminders, or configure your local Ollama server!
                """.trimIndent()
            )
            saveMessages("conv_welcome", listOf(welcomeMsg1))
        }

        conversationsCache = list
        list
    }

    suspend fun saveConversation(conv: ConversationSummary) = withContext(Dispatchers.IO) {
        conversationDao.insertOrUpdateConversation(ConversationEntity.fromDomain(conv))
        val list = (conversationsCache ?: getConversations().toMutableList()).toMutableList()
        val index = list.indexOfFirst { it.id == conv.id }
        if (index >= 0) {
            list[index] = conv
        } else {
            list.add(0, conv)
        }
        conversationsCache = list
        saveConversationsIndex(list)
    }

    suspend fun deleteConversation(convId: String) = withContext(Dispatchers.IO) {
        conversationDao.deleteConversationById(convId)
        messageDao.deleteMessagesForConversation(convId)
        val list = (conversationsCache ?: getConversations().toMutableList()).toMutableList()
        list.removeAll { it.id == convId }
        conversationsCache = list
        saveConversationsIndex(list)

        val convDir = File(conversationsDir, convId)
        if (convDir.exists()) {
            convDir.deleteRecursively()
        }
        messageCache.remove(convId)
    }

    private fun saveConversationsIndex(list: List<ConversationSummary>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val indexFile = File(conversationsDir, "conversations_index.json.enc")
        writeEncryptedFile(indexFile, array.toString())
    }

    suspend fun getMessages(convId: String): List<Message> = withContext(Dispatchers.IO) {
        messageCache[convId]?.let { return@withContext it.toList() }

        val roomEntities = messageDao.getMessagesForConversation(convId)
        val sorted = if (roomEntities.isNotEmpty()) {
            roomEntities.map { it.toDomain() }.sortedBy { it.timestamp }.toMutableList()
        } else {
            val convDir = File(conversationsDir, convId)
            val allMessages = mutableListOf<Message>()
            if (convDir.exists()) {
                val files = convDir.listFiles { _, name -> name.endsWith(".json.enc") }?.sortedBy { it.name } ?: emptyList()

                for (file in files) {
                    val jsonStr = readEncryptedFile(file) ?: continue
                    try {
                        val array = JSONArray(jsonStr)
                        for (i in 0 until array.length()) {
                            allMessages.add(Message.fromJson(array.getJSONObject(i)))
                        }
                    } catch (_: Exception) {}
                }
                if (allMessages.isNotEmpty()) {
                    messageDao.insertMessages(allMessages.map { MessageEntity.fromDomain(it) })
                }
            }
            allMessages.sortedBy { it.timestamp }.toMutableList()
        }

        messageCache[convId] = sorted
        sorted
    }

    suspend fun addMessage(convId: String, message: Message) = withContext(Dispatchers.IO) {
        messageDao.insertOrUpdateMessage(MessageEntity.fromDomain(message))

        val current = (messageCache[convId] ?: getMessages(convId).toMutableList()).toMutableList()
        current.add(message)
        messageCache[convId] = current

        // Save to monthly partition file for file backup
        val partition = message.monthPartition
        val convDir = File(conversationsDir, convId).apply { mkdirs() }
        val partitionFile = File(convDir, "$partition.json.enc")

        val partitionMessages = current.filter { it.monthPartition == partition }
        val array = JSONArray()
        partitionMessages.forEach { array.put(it.toJson()) }
        writeEncryptedFile(partitionFile, array.toString())

        // Update conversation summary
        val convs = (conversationsCache ?: getConversations()).toMutableList()
        val existingIndex = convs.indexOfFirst { it.id == convId }
        val summary = if (existingIndex >= 0) {
            convs[existingIndex].copy(
                lastMessage = message.content.take(80),
                updatedAt = message.timestamp,
                messageCount = current.size
            )
        } else {
            ConversationSummary(
                id = convId,
                title = message.content.take(30).ifBlank { "New Chat" },
                lastMessage = message.content.take(80),
                updatedAt = message.timestamp,
                messageCount = 1
            )
        }
        saveConversation(summary)
    }

    suspend fun saveMessages(convId: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        messageDao.insertMessages(messages.map { MessageEntity.fromDomain(it) })

        val convDir = File(conversationsDir, convId).apply { mkdirs() }
        val partitions = messages.groupBy { it.monthPartition }

        for ((partition, pMessages) in partitions) {
            val partitionFile = File(convDir, "$partition.json.enc")
            val array = JSONArray()
            pMessages.forEach { array.put(it.toJson()) }
            writeEncryptedFile(partitionFile, array.toString())
        }
        messageCache[convId] = messages.toMutableList()
    }

    // -------------------------------------------------------------
    // Tasks & Scheduling
    // -------------------------------------------------------------

    suspend fun getTasks(): List<ScheduledTask> = withContext(Dispatchers.IO) {
        tasksCache?.let { return@withContext it.toList() }

        val taskFile = File(tasksDir, "tasks.json.enc")
        val jsonStr = readEncryptedFile(taskFile)
        val list = mutableListOf<ScheduledTask>()

        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(ScheduledTask.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        if (list.isEmpty()) {
            // Add default example tasks
            list.add(
                ScheduledTask(
                    id = "task_daily_summary",
                    title = "Daily Tech & AI Briefing",
                    prompt = "Search the web for top 3 AI and tech agent breakthroughs today and summarize them.",
                    cronExpression = "0 9 * * *",
                    intervalMinutes = 1440,
                    isRecurring = true,
                    isEnabled = true,
                    nextRunMillis = System.currentTimeMillis() + 3600000L
                )
            )
            list.add(
                ScheduledTask(
                    id = "task_workspace_cleanup",
                    title = "Workspace Maintenance",
                    prompt = "Analyze workspace logs and check storage health.",
                    cronExpression = "0 0 * * 0",
                    intervalMinutes = 10080,
                    isRecurring = true,
                    isEnabled = true,
                    nextRunMillis = System.currentTimeMillis() + 86400000L
                )
            )
            saveTasks(list)
        }

        tasksCache = list.toMutableList()
        list
    }

    suspend fun saveTasks(tasks: List<ScheduledTask>) = withContext(Dispatchers.IO) {
        tasksCache = tasks.toMutableList()
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        val file = File(tasksDir, "tasks.json.enc")
        writeEncryptedFile(file, array.toString())
    }

    suspend fun updateTask(task: ScheduledTask) = withContext(Dispatchers.IO) {
        val list = (tasksCache ?: getTasks().toMutableList()).toMutableList()
        val idx = list.indexOfFirst { it.id == task.id }
        if (idx >= 0) {
            list[idx] = task
        } else {
            list.add(task)
        }
        saveTasks(list)
    }

    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        val list = (tasksCache ?: getTasks().toMutableList()).toMutableList()
        list.removeAll { it.id == taskId }
        saveTasks(list)
    }

    // -------------------------------------------------------------
    // Task Execution History (Zero-DB Encrypted)
    // -------------------------------------------------------------

    suspend fun getTaskExecutions(): List<AgentTaskExecution> = withContext(Dispatchers.IO) {
        taskExecutionsCache?.let { return@withContext it.toList() }

        val roomEntities = agentTaskDao.getAllTaskExecutions()
        var list = roomEntities.map { it.toDomain() }.toMutableList()

        if (list.isEmpty()) {
            val now = System.currentTimeMillis()
            list = mutableListOf(
                AgentTaskExecution(
                    id = "exec_demo_1",
                    taskId = "task_daily_summary",
                    title = "Daily Tech & AI Briefing",
                    prompt = "Search the web for top 3 AI and tech agent breakthroughs today and summarize them.",
                    status = "completed",
                    startedAt = now - 1800000L,
                    finishedAt = now - 1797600L,
                    durationMs = 2400L,
                    modelUsed = "gemini-3.5-flash",
                    triggerType = "Cron Schedule",
                    toolCallsCount = 2,
                    outputSummary = "Top AI Breakthroughs:\n1. Gemini 3.5 autonomous long-context reasoning with native sub-agents.\n2. Zero-DB encrypted offline vectors with hardware key attestation.\n3. Multi-channel dispatch engine orchestrating WhatsApp, Telegram, SMS & Email alerts seamlessly.",
                    progressPercent = 1.0f,
                    currentStep = "Finished in 2.4s"
                ),
                AgentTaskExecution(
                    id = "exec_demo_2",
                    taskId = "task_hourly_health",
                    title = "Hourly Server Health & Telemetry Sweep",
                    prompt = "Poll server health endpoints, check ping latencies, and verify memory footprint.",
                    status = "pending",
                    startedAt = now - 60000L,
                    finishedAt = null,
                    durationMs = 0L,
                    modelUsed = "ollama • llama3.2",
                    triggerType = "Cron Schedule",
                    toolCallsCount = 0,
                    outputSummary = null,
                    progressPercent = 0.1f,
                    currentStep = "Queued in background worker pool (awaiting execution slot)"
                ),
                AgentTaskExecution(
                    id = "exec_demo_3",
                    taskId = null,
                    title = "Web Research: Local LLM Quantization",
                    prompt = "Search arXiv and benchmark reports for 4-bit GGUF vs EXL2 quantization speeds on mobile NPU.",
                    status = "running",
                    startedAt = now - 45000L,
                    finishedAt = null,
                    durationMs = 0L,
                    modelUsed = "gemini-3.5-flash",
                    triggerType = "Manual Run",
                    toolCallsCount = 1,
                    outputSummary = "Analyzing arXiv papers and mobile CPU/GPU performance graphs...",
                    progressPercent = 0.65f,
                    currentStep = "Step 2 of 3: Querying WebSearchEngine for 'GGUF Q4_K_M vs EXL2 mobile inference latency'..."
                ),
                AgentTaskExecution(
                    id = "exec_demo_4",
                    taskId = null,
                    title = "Remote PostgreSQL Cloud Sync",
                    prompt = "Attempt encrypted zero-knowledge mirror sync to remote enterprise server.",
                    status = "failed",
                    startedAt = now - 3600000L,
                    finishedAt = now - 3595000L,
                    durationMs = 5000L,
                    modelUsed = "openai • gpt-4o-mini",
                    triggerType = "Autonomous Agent",
                    toolCallsCount = 1,
                    errorMessage = "NetworkException: SocketTimeout - Connection refused at endpoint 192.168.1.105:5432 after 3 retry attempts.",
                    progressPercent = 1.0f,
                    currentStep = "Failed: Connection Timeout"
                ),
                AgentTaskExecution(
                    id = "exec_demo_5",
                    taskId = "task_workspace_cleanup",
                    title = "Workspace Maintenance & Deep Audit",
                    prompt = "Analyze workspace logs and check storage health.",
                    status = "cancelled",
                    startedAt = now - 7200000L,
                    finishedAt = now - 7199700L,
                    durationMs = 300L,
                    modelUsed = "claude-3-5-sonnet",
                    triggerType = "Manual Run",
                    toolCallsCount = 0,
                    cancellationReason = "Cancelled by user before agent execution loop started",
                    progressPercent = 0.0f,
                    currentStep = "Cancelled by User"
                )
            )
            agentTaskDao.insertTaskExecutions(list.map { AgentTaskExecutionEntity.fromDomain(it) })
            saveTaskExecutions(list)
        }

        taskExecutionsCache = list
        list
    }

    suspend fun saveTaskExecutions(executions: List<AgentTaskExecution>) = withContext(Dispatchers.IO) {
        taskExecutionsCache = executions.toMutableList()
        agentTaskDao.insertTaskExecutions(executions.map { AgentTaskExecutionEntity.fromDomain(it) })
        val array = JSONArray()
        executions.forEach { array.put(it.toJson()) }
        val file = File(tasksDir, "task_executions.json.enc")
        writeEncryptedFile(file, array.toString())
    }

    suspend fun addTaskExecution(execution: AgentTaskExecution) = withContext(Dispatchers.IO) {
        agentTaskDao.insertOrUpdateTaskExecution(AgentTaskExecutionEntity.fromDomain(execution))
        val list = (taskExecutionsCache ?: getTaskExecutions().toMutableList()).toMutableList()
        list.add(0, execution)
        saveTaskExecutions(list)
    }

    suspend fun updateTaskExecution(execution: AgentTaskExecution) = withContext(Dispatchers.IO) {
        agentTaskDao.insertOrUpdateTaskExecution(AgentTaskExecutionEntity.fromDomain(execution))
        val list = (taskExecutionsCache ?: getTaskExecutions().toMutableList()).toMutableList()
        val idx = list.indexOfFirst { it.id == execution.id }
        if (idx >= 0) {
            list[idx] = execution
        } else {
            list.add(0, execution)
        }
        saveTaskExecutions(list)
    }

    suspend fun deleteTaskExecution(executionId: String) = withContext(Dispatchers.IO) {
        agentTaskDao.deleteTaskExecutionById(executionId)
        val list = (taskExecutionsCache ?: getTaskExecutions().toMutableList()).toMutableList()
        list.removeAll { it.id == executionId }
        saveTaskExecutions(list)
    }

    suspend fun clearTaskHistory() = withContext(Dispatchers.IO) {
        agentTaskDao.clearAllTaskExecutions()
        saveTaskExecutions(emptyList())
    }

    // -------------------------------------------------------------
    // Autonomous Agents & Tool Capability Registry (Zero-DB Encrypted)
    // -------------------------------------------------------------

    suspend fun getAgents(): List<AgentEntity> = withContext(Dispatchers.IO) {
        agentsCache?.let { return@withContext it.toList() }

        val file = File(agentsDir, "agents_index.json.enc")
        val jsonStr = readEncryptedFile(file)
        val list = mutableListOf<AgentEntity>()

        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(AgentEntity.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // fall back to default agents on parse error
            }
        }

        if (list.isEmpty()) {
            val defaults = AgentEntity.getDefaultAgents()
            list.addAll(defaults)
            saveAgents(list)
        }

        agentsCache = list.toMutableList()
        list
    }

    suspend fun saveAgents(agents: List<AgentEntity>) = withContext(Dispatchers.IO) {
        agentsCache = agents.toMutableList()
        val array = JSONArray()
        agents.forEach { array.put(it.toJson()) }
        val file = File(agentsDir, "agents_index.json.enc")
        writeEncryptedFile(file, array.toString())
    }

    suspend fun saveAgent(agent: AgentEntity) = withContext(Dispatchers.IO) {
        val list = (agentsCache ?: getAgents().toMutableList()).toMutableList()
        val idx = list.indexOfFirst { it.id == agent.id }
        if (idx >= 0) {
            list[idx] = agent
        } else {
            list.add(agent)
        }
        saveAgents(list)
    }

    suspend fun deleteAgent(agentId: String) = withContext(Dispatchers.IO) {
        val list = (agentsCache ?: getAgents().toMutableList()).toMutableList()
        list.removeAll { it.id == agentId }
        saveAgents(list)
    }

    suspend fun resetDefaultAgents(): List<AgentEntity> = withContext(Dispatchers.IO) {
        val defaults = AgentEntity.getDefaultAgents()
        saveAgents(defaults)
        defaults
    }

    // -------------------------------------------------------------
    // LLM Providers & Settings
    // -------------------------------------------------------------

    suspend fun getProviders(): List<LlmProviderConfig> = withContext(Dispatchers.IO) {
        providersCache?.let { return@withContext it.toList() }

        val file = File(llmDir, "providers.json.enc")
        val jsonStr = readEncryptedFile(file)
        val list = mutableListOf<LlmProviderConfig>()

        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(LlmProviderConfig.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        if (list.isEmpty()) {
            list.addAll(getDefaultProviders())
            saveProviders(list)
        }

        providersCache = list.toMutableList()
        list
    }

    suspend fun saveProviders(providers: List<LlmProviderConfig>) = withContext(Dispatchers.IO) {
        providersCache = providers.toMutableList()
        val array = JSONArray()
        providers.forEach { array.put(it.toJson()) }
        val file = File(llmDir, "providers.json.enc")
        writeEncryptedFile(file, array.toString())
    }

    suspend fun updateProvider(provider: LlmProviderConfig) = withContext(Dispatchers.IO) {
        val list = (providersCache ?: getProviders().toMutableList()).toMutableList()
        val idx = list.indexOfFirst { it.id == provider.id }
        if (idx >= 0) {
            list[idx] = provider
        } else {
            list.add(provider)
        }
        saveProviders(list)
    }

    suspend fun deleteProvider(providerId: String) = withContext(Dispatchers.IO) {
        val list = (providersCache ?: getProviders().toMutableList()).toMutableList()
        list.removeAll { it.id == providerId }
        saveProviders(list)
    }

    suspend fun resetDefaultProviders(): List<LlmProviderConfig> = withContext(Dispatchers.IO) {
        val defaults = getDefaultProviders()
        saveProviders(defaults)
        defaults
    }

    suspend fun clearAllApiKeys() = withContext(Dispatchers.IO) {
        val list = (providersCache ?: getProviders().toMutableList()).toMutableList()
        val cleared = list.map { it.copy(apiKey = "") }
        saveProviders(cleared)
    }

    suspend fun getSettings(): LlmGenerationSettings = withContext(Dispatchers.IO) {
        settingsCache?.let { return@withContext it }

        val file = File(llmDir, "settings.json.enc")
        val jsonStr = readEncryptedFile(file)
        val settings = if (jsonStr != null) {
            try {
                LlmGenerationSettings.fromJson(JSONObject(jsonStr))
            } catch (e: Exception) {
                LlmGenerationSettings()
            }
        } else {
            val defaultSettings = LlmGenerationSettings()
            saveSettings(defaultSettings)
            defaultSettings
        }
        settingsCache = settings
        settings
    }

    suspend fun saveSettings(settings: LlmGenerationSettings) = withContext(Dispatchers.IO) {
        settingsCache = settings
        val file = File(llmDir, "settings.json.enc")
        writeEncryptedFile(file, settings.toJson().toString())
    }

    // -------------------------------------------------------------
    // Communication Channels (WhatsApp, Telegram, SMS, Email)
    // -------------------------------------------------------------

    suspend fun getChannelsConfig(): CommunicationChannelsConfig = withContext(Dispatchers.IO) {
        channelsConfigCache?.let { return@withContext it }

        val file = File(channelsDir, "channels_config.json.enc")
        val jsonStr = readEncryptedFile(file)
        val config = if (jsonStr != null) {
            try {
                CommunicationChannelsConfig.fromJson(JSONObject(jsonStr))
            } catch (e: Exception) {
                CommunicationChannelsConfig()
            }
        } else {
            val defaultConfig = CommunicationChannelsConfig()
            saveChannelsConfig(defaultConfig)
            defaultConfig
        }
        channelsConfigCache = config
        config
    }

    suspend fun saveChannelsConfig(config: CommunicationChannelsConfig) = withContext(Dispatchers.IO) {
        channelsConfigCache = config
        val file = File(channelsDir, "channels_config.json.enc")
        writeEncryptedFile(file, config.toJson().toString())
    }

    suspend fun getTransmissionLogs(): List<ChannelTransmissionLog> = withContext(Dispatchers.IO) {
        transmissionLogsCache?.let { return@withContext it.toList() }

        val file = File(channelsDir, "transmission_logs.json.enc")
        val jsonStr = readEncryptedFile(file)
        val list = mutableListOf<ChannelTransmissionLog>()

        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(ChannelTransmissionLog.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        transmissionLogsCache = list.toMutableList()
        list
    }

    suspend fun addTransmissionLog(log: ChannelTransmissionLog) = withContext(Dispatchers.IO) {
        val list = (transmissionLogsCache ?: getTransmissionLogs().toMutableList()).toMutableList()
        list.add(0, log) // Insert at top
        if (list.size > 100) {
            list.removeAt(list.lastIndex) // Keep last 100 logs
        }
        transmissionLogsCache = list
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val file = File(channelsDir, "transmission_logs.json.enc")
        writeEncryptedFile(file, array.toString())
    }

    suspend fun clearTransmissionLogs() = withContext(Dispatchers.IO) {
        transmissionLogsCache = mutableListOf()
        val file = File(channelsDir, "transmission_logs.json.enc")
        writeEncryptedFile(file, "[]")
    }

    // -------------------------------------------------------------
    // Sandboxed Workspace Files
    // -------------------------------------------------------------

    fun getWorkspaceDirectory(): File = workspaceDir

    suspend fun listWorkspaceFiles(): List<WorkspaceFileInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<WorkspaceFileInfo>()
        workspaceDir.walkTopDown().forEach { file ->
            if (file != workspaceDir) {
                list.add(
                    WorkspaceFileInfo(
                        name = file.name,
                        relativePath = file.relativeTo(workspaceDir).path,
                        sizeBytes = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = file.isDirectory,
                        isEncrypted = false
                    )
                )
            }
        }
        list.sortedWith(compareByDescending<WorkspaceFileInfo> { it.isDirectory }.thenBy { it.name })
    }

    suspend fun listAllInternalFiles(): List<WorkspaceFileInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<WorkspaceFileInfo>()
        rootDir.walkTopDown().forEach { file ->
            if (file != rootDir) {
                list.add(
                    WorkspaceFileInfo(
                        name = file.name,
                        relativePath = file.relativeTo(rootDir).path,
                        sizeBytes = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = file.isDirectory,
                        isEncrypted = file.name.endsWith(".enc")
                    )
                )
            }
        }
        list.sortedWith(compareByDescending<WorkspaceFileInfo> { it.isDirectory }.thenBy { it.relativePath })
    }

    suspend fun readWorkspaceFile(relativePath: String): String? = withContext(Dispatchers.IO) {
        val file = File(workspaceDir, relativePath)
        if (file.exists() && file.isFile) file.readText() else null
    }

    suspend fun writeWorkspaceFile(relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(workspaceDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    suspend fun deleteWorkspaceFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(workspaceDir, relativePath)
        if (file.exists()) file.deleteRecursively() else false
    }

    // -------------------------------------------------------------
    // Backup and Restore (.clawpkg)
    // -------------------------------------------------------------

    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val backupFile = File(backupsDir, "openclaw_backup_$timestampStr.clawpkg")

        val zipOut = ZipOutputStream(FileOutputStream(backupFile))
        val filesToBackup = rootDir.walkTopDown().filter { it.isFile && !it.path.contains("backups") }

        for (file in filesToBackup) {
            val relPath = file.relativeTo(rootDir).path
            zipOut.putNextEntry(ZipEntry(relPath))
            file.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }
        zipOut.close()
        backupFile
    }

    suspend fun restoreBackup(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val zipIn = ZipInputStream(FileInputStream(backupFile))
            var entry = zipIn.nextEntry
            while (entry != null) {
                val targetFile = File(rootDir, entry.name)
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { out ->
                        zipIn.copyTo(out)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            // Invalidate all caches
            conversationsCache = null
            messageCache.clear()
            tasksCache = null
            providersCache = null
            settingsCache = null

            getConversations()
            getTasks()
            getProviders()
            getSettings()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------
    // Default 15+ Providers Configuration
    // -------------------------------------------------------------

    private fun getDefaultProviders(): List<LlmProviderConfig> = listOf(
        LlmProviderConfig(
            id = "gemini",
            name = "Google Gemini",
            type = "gemini",
            baseUrl = "https://generativelanguage.googleapis.com",
            apiKey = BuildConfig.GEMINI_API_KEY,
            isEnabled = true,
            isLocal = false,
            defaultModel = "gemini-3.5-flash",
            availableModels = listOf(
                "gemini-3.5-flash",
                "gemini-3.1-pro-preview",
                "gemini-3.1-flash-lite-preview",
                "gemini-flash-latest"
            )
        ),
        LlmProviderConfig(
            id = "openai",
            name = "OpenAI",
            type = "openai",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "gpt-4o",
            availableModels = listOf("gpt-4o", "gpt-4o-mini", "o1", "o3-mini", "gpt-4-turbo")
        ),
        LlmProviderConfig(
            id = "claude",
            name = "Anthropic Claude",
            type = "claude",
            baseUrl = "https://api.anthropic.com/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "claude-3-7-sonnet-20250219",
            availableModels = listOf(
                "claude-3-7-sonnet-20250219",
                "claude-3-5-sonnet-20241022",
                "claude-3-5-haiku-20241022",
                "claude-3-opus-20240229"
            )
        ),
        LlmProviderConfig(
            id = "groq",
            name = "Groq Ultra-Fast",
            type = "groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "llama-3.3-70b-versatile",
            availableModels = listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "mixtral-8x7b-32768",
                "gemma2-9b-it",
                "deepseek-r1-distill-llama-70b"
            )
        ),
        LlmProviderConfig(
            id = "ollama",
            name = "Ollama (Local / LAN)",
            type = "ollama",
            baseUrl = "http://10.0.2.2:11434",
            apiKey = "",
            isEnabled = true,
            isLocal = true,
            defaultModel = "llama3.2",
            availableModels = listOf("llama3.2", "mistral", "phi3", "deepseek-r1:8b", "qwen2.5-coder", "gemma2")
        ),
        LlmProviderConfig(
            id = "lmstudio",
            name = "LM Studio (Local)",
            type = "lmstudio",
            baseUrl = "http://10.0.2.2:1234/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = true,
            defaultModel = "local-model",
            availableModels = listOf("local-model", "qwen2.5-7b", "hermes-3-llama-3.1-8b")
        ),
        LlmProviderConfig(
            id = "together",
            name = "Together AI",
            type = "together",
            baseUrl = "https://api.together.xyz/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            availableModels = listOf(
                "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                "Qwen/Qwen2.5-72B-Instruct-Turbo",
                "deepseek-ai/DeepSeek-V3"
            )
        ),
        LlmProviderConfig(
            id = "perplexity",
            name = "Perplexity Online",
            type = "perplexity",
            baseUrl = "https://api.perplexity.ai",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "sonar",
            availableModels = listOf("sonar", "sonar-pro", "sonar-reasoning")
        ),
        LlmProviderConfig(
            id = "mistral",
            name = "Mistral AI",
            type = "mistral",
            baseUrl = "https://api.mistral.ai/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "mistral-large-latest",
            availableModels = listOf("mistral-large-latest", "codestral-latest", "mistral-small-latest")
        ),
        LlmProviderConfig(
            id = "deepseek",
            name = "DeepSeek AI",
            type = "deepseek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "deepseek-chat",
            availableModels = listOf("deepseek-chat", "deepseek-reasoner")
        ),
        LlmProviderConfig(
            id = "openrouter",
            name = "OpenRouter",
            type = "openrouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "openrouter/auto",
            availableModels = listOf("openrouter/auto", "anthropic/claude-3.5-sonnet", "meta-llama/llama-3.3-70b-instruct")
        ),
        LlmProviderConfig(
            id = "cerebras",
            name = "Cerebras Fast",
            type = "cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "llama3.1-70b",
            availableModels = listOf("llama3.1-70b", "llama3.1-8b")
        ),
        LlmProviderConfig(
            id = "fireworks",
            name = "Fireworks AI",
            type = "fireworks",
            baseUrl = "https://api.fireworks.ai/inference/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct",
            availableModels = listOf("accounts/fireworks/models/llama-v3p3-70b-instruct", "accounts/fireworks/models/qwen2p5-72b-instruct")
        ),
        LlmProviderConfig(
            id = "xai",
            name = "xAI (Grok)",
            type = "xai",
            baseUrl = "https://api.x.ai/v1",
            apiKey = "",
            isEnabled = true,
            isLocal = false,
            defaultModel = "grok-2-latest",
            availableModels = listOf("grok-2-latest", "grok-beta")
        ),
        LlmProviderConfig(
            id = "azure",
            name = "Azure OpenAI",
            type = "azure",
            baseUrl = "https://{resource}.openai.azure.com/openai/deployments/{deployment}",
            apiKey = "",
            isEnabled = false,
            isLocal = false,
            defaultModel = "gpt-4o",
            availableModels = listOf("gpt-4o", "gpt-4o-mini")
        ),
        LlmProviderConfig(
            id = "jan",
            name = "Jan.ai (Local)",
            type = "jan",
            baseUrl = "http://10.0.2.2:1337/v1",
            apiKey = "",
            isEnabled = false,
            isLocal = true,
            defaultModel = "trinity-v1",
            availableModels = listOf("trinity-v1", "mistral-ins-7b")
        ),
        LlmProviderConfig(
            id = "localai",
            name = "LocalAI (Local)",
            type = "localai",
            baseUrl = "http://10.0.2.2:8080/v1",
            apiKey = "",
            isEnabled = false,
            isLocal = true,
            defaultModel = "gpt-4",
            availableModels = listOf("gpt-4", "llama-3")
        ),
        LlmProviderConfig(
            id = "vllm",
            name = "vLLM Server",
            type = "vllm",
            baseUrl = "http://10.0.2.2:8000/v1",
            apiKey = "",
            isEnabled = false,
            isLocal = true,
            defaultModel = "hosted-vllm-model",
            availableModels = listOf("hosted-vllm-model")
        ),
        LlmProviderConfig(
            id = "custom",
            name = "Custom OpenAI Endpoint",
            type = "custom",
            baseUrl = "https://api.my-custom-llm.com/v1",
            apiKey = "",
            isEnabled = false,
            isLocal = false,
            defaultModel = "custom-model",
            availableModels = listOf("custom-model")
        )
    )

    // --- VOICE PARSED TASKS ROOM DB ACCESSORS ---
    fun getVoiceTasksFlow() = voiceTaskDao.getAllVoiceTasksFlow()
    suspend fun getVoiceTasks(): List<VoiceTaskEntity> = withContext(Dispatchers.IO) { voiceTaskDao.getAllVoiceTasks() }
    suspend fun insertVoiceTask(task: VoiceTaskEntity) = withContext(Dispatchers.IO) { voiceTaskDao.insertTask(task) }
    suspend fun updateVoiceTask(task: VoiceTaskEntity) = withContext(Dispatchers.IO) { voiceTaskDao.updateTask(task) }
    suspend fun deleteVoiceTask(id: String) = withContext(Dispatchers.IO) { voiceTaskDao.deleteTaskById(id) }

    companion object {
        const val DEFAULT_DESIGN_SPEC = """# OpenClaw / nanobot Mobile Architecture Design Spec
**Version**: 1.0.0-PROD
**Storage**: Zero Database, AES-256-GCM Encrypted JSON Files
**Cold Start**: <3s target
**Disk IO**: <50ms file operations target

### Directory Structure:
- `/nanobot/conversations/conversations_index.json.enc`
- `/nanobot/conversations/{conv_id}/{yyyy-MM}.json.enc`
- `/nanobot/tasks/tasks.json.enc`
- `/nanobot/llm/providers.json.enc`
- `/nanobot/llm/settings.json.enc`
- `/nanobot/workspace/`
- `/nanobot/backups/`
"""
    }
}
