package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentEngine
import com.example.agent.AgentTools
import com.example.agent.CodeExecutionEngine
import com.example.agent.WebSearchEngine
import com.example.channels.*
import com.example.llm.UniversalLlmManager
import com.example.storage.*
import com.example.voice.VoiceInputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

typealias HoloViewModel = MainViewModel

data class MainUiState(
    val currentConversationId: String = "conv_welcome",
    val conversations: List<ConversationSummary> = emptyList(),
    val messages: List<Message> = emptyList(),
    val tasks: List<ScheduledTask> = emptyList(),
    val voiceTasks: List<VoiceTaskEntity> = emptyList(),
    val isParsingVoiceTask: Boolean = false,
    val lastParsedVoiceTask: VoiceTaskEntity? = null,
    val taskHistory: List<AgentTaskExecution> = emptyList(),
    val taskHistoryFilter: String = "ALL", // ALL, PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    val agents: List<AgentEntity> = emptyList(),
    val selectedAgentId: String = "agent_openclaw_prime",
    val providers: List<LlmProviderConfig> = emptyList(),
    val settings: LlmGenerationSettings = LlmGenerationSettings(),
    val channelsConfig: CommunicationChannelsConfig = CommunicationChannelsConfig(),
    val transmissionLogs: List<ChannelTransmissionLog> = emptyList(),
    val isChannelSending: Boolean = false,
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val streamingThinking: String = "",
    val activeToolCalls: List<ToolCall> = emptyList(),
    val currentAgentStatus: String = "Agent Idle",
    val isNetworkOnline: Boolean = true,
    val isVoiceActive: Boolean = false,
    val voiceRms: Float = 0f,
    val workspaceFiles: List<WorkspaceFileInfo> = emptyList(),
    val internalEncryptedFiles: List<WorkspaceFileInfo> = emptyList(),
    val activeScreen: Int = 0, // 0: Chat, 1: Tasks, 2: LLM Hub & Channels, 3: Workspace/Files
    val showModelPicker: Boolean = false,
    val notificationMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = EncryptedStorageManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val agentDao = database.agentDao()
    private val channelDispatchService = ChannelDispatchService(application)
    private val llmManager = UniversalLlmManager(storageManager)
    private val webSearchEngine = WebSearchEngine()
    private val codeExecutionEngine = CodeExecutionEngine()
    private val agentTools = AgentTools(storageManager, webSearchEngine, codeExecutionEngine, channelDispatchService)
    private val agentEngine = AgentEngine(storageManager, llmManager, agentTools)
    private val voiceInputManager = VoiceInputManager(application)
    private val runningTaskJobs = ConcurrentHashMap<String, Job>()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    init {
        registerNetworkCallback()
        loadInitialData()
        observeVoiceState()
        observeRoomData()
    }

    private fun observeRoomData() {
        viewModelScope.launch {
            agentDao.getAllAgentsFlow().collect { agents ->
                if (agents.isNotEmpty()) {
                    _uiState.update { state -> state.copy(agents = agents) }
                }
            }
        }

        viewModelScope.launch {
            storageManager.conversationDao.getAllConversationsFlow().collect { convEntities ->
                val domainConvs = convEntities.map { it.toDomain() }
                if (domainConvs.isNotEmpty()) {
                    _uiState.update { state -> state.copy(conversations = domainConvs) }
                }
            }
        }

        viewModelScope.launch {
            storageManager.agentTaskDao.getAllTaskExecutionsFlow().collect { execEntities ->
                val domainExecs = execEntities.map { it.toDomain() }
                if (domainExecs.isNotEmpty()) {
                    _uiState.update { state -> state.copy(taskHistory = domainExecs) }
                }
            }
        }

        viewModelScope.launch {
            storageManager.getVoiceTasksFlow().collect { vTasks ->
                _uiState.update { state -> state.copy(voiceTasks = vTasks) }
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _uiState.update { it.copy(isNetworkOnline = true) }
            }

            override fun onLost(network: Network) {
                _uiState.update { it.copy(isNetworkOnline = false) }
            }
        })
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceInputManager.isListening.collect { listening ->
                _uiState.update { it.copy(isVoiceActive = listening) }
            }
        }
        viewModelScope.launch {
            voiceInputManager.audioRms.collect { rms ->
                _uiState.update { it.copy(voiceRms = rms) }
            }
        }
    }

    fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.initialize()
            val convs = storageManager.getConversations()
            val initialConvId = convs.firstOrNull()?.id ?: "conv_welcome"
            val initialMsgs = storageManager.getMessages(initialConvId)
            val tasks = storageManager.getTasks()
            val taskHistory = storageManager.getTaskExecutions()
            
            // Sync agents from Room Database or seed from Encrypted Storage
            var dbAgents = agentDao.getAllAgents()
            if (dbAgents.isEmpty()) {
                val initialAgents = storageManager.getAgents()
                agentDao.insertAgents(initialAgents)
                dbAgents = initialAgents
            }

            val providers = storageManager.getProviders()
            val settings = storageManager.getSettings()
            val channelsConfig = storageManager.getChannelsConfig()
            val transmissionLogs = storageManager.getTransmissionLogs()
            val workspaceFiles = storageManager.listWorkspaceFiles()
            val internalFiles = storageManager.listAllInternalFiles()

            _uiState.update {
                it.copy(
                    conversations = convs,
                    currentConversationId = initialConvId,
                    messages = initialMsgs,
                    tasks = tasks,
                    taskHistory = taskHistory,
                    agents = dbAgents,
                    selectedAgentId = dbAgents.firstOrNull()?.id ?: "agent_openclaw_prime",
                    providers = providers,
                    settings = settings,
                    channelsConfig = channelsConfig,
                    transmissionLogs = transmissionLogs,
                    workspaceFiles = workspaceFiles,
                    internalEncryptedFiles = internalFiles
                )
            }
        }
    }

    fun setScreen(index: Int) {
        _uiState.update { it.copy(activeScreen = index) }
        if (index == 3) refreshWorkspaceFiles()
    }

    fun toggleModelPicker(show: Boolean) {
        _uiState.update { it.copy(showModelPicker = show) }
    }

    fun selectConversation(convId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msgs = storageManager.getMessages(convId)
            _uiState.update {
                it.copy(
                    currentConversationId = convId,
                    messages = msgs,
                    streamingContent = "",
                    streamingThinking = "",
                    activeToolCalls = emptyList()
                )
            }
        }
    }

    fun createNewConversation(title: String = "New Chat") {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = "conv_${UUID.randomUUID().toString().take(8)}"
            val activeModel = _uiState.value.settings.activeModelId
            val activeProv = _uiState.value.settings.activeProviderId
            val summary = ConversationSummary(
                id = newId,
                title = title,
                modelUsed = activeModel,
                providerId = activeProv
            )
            storageManager.saveConversation(summary)
            val convs = storageManager.getConversations()
            _uiState.update {
                it.copy(
                    conversations = convs,
                    currentConversationId = newId,
                    messages = emptyList(),
                    streamingContent = "",
                    streamingThinking = "",
                    activeToolCalls = emptyList()
                )
            }
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteConversation(convId)
            val convs = storageManager.getConversations()
            val nextConvId = convs.firstOrNull()?.id ?: run {
                createNewConversation()
                return@launch
            }
            selectConversation(nextConvId)
            _uiState.update { it.copy(conversations = convs) }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _uiState.value.isGenerating) return

        val convId = _uiState.value.currentConversationId
        _uiState.update {
            it.copy(
                isGenerating = true,
                streamingContent = "",
                streamingThinking = "",
                activeToolCalls = emptyList(),
                currentAgentStatus = "OpenClaw processing..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                agentEngine.processUserMessage(
                    conversationId = convId,
                    userText = userText,
                    isNetworkAvailable = _uiState.value.isNetworkOnline,
                    onDelta = { deltaText, deltaThinking ->
                        _uiState.update { state ->
                            state.copy(
                                streamingContent = state.streamingContent + deltaText,
                                streamingThinking = if (deltaThinking != null) state.streamingThinking + deltaThinking else state.streamingThinking
                            )
                        }
                    },
                    onToolStarted = { toolCall ->
                        _uiState.update { state ->
                            val current = state.activeToolCalls.toMutableList()
                            current.add(toolCall)
                            state.copy(activeToolCalls = current)
                        }
                    },
                    onToolFinished = { toolCall ->
                        _uiState.update { state ->
                            val current = state.activeToolCalls.toMutableList()
                            val idx = current.indexOfFirst { it.id == toolCall.id }
                            if (idx >= 0) current[idx] = toolCall else current.add(toolCall)
                            state.copy(activeToolCalls = current)
                        }
                    },
                    onStatusUpdate = { status ->
                        _uiState.update { it.copy(currentAgentStatus = status) }
                    }
                )

                // Refresh conversation messages & summaries
                val updatedMsgs = storageManager.getMessages(convId)
                val convs = storageManager.getConversations()
                _uiState.update {
                    it.copy(
                        messages = updatedMsgs,
                        conversations = convs,
                        isGenerating = false,
                        streamingContent = "",
                        streamingThinking = "",
                        activeToolCalls = emptyList(),
                        currentAgentStatus = "Agent Idle"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        currentAgentStatus = "Error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = _uiState.value.settings.copy(
                activeProviderId = providerId,
                activeModelId = modelId
            )
            storageManager.saveSettings(updated)
            _uiState.update { it.copy(settings = updated, showModelPicker = false) }
        }
    }

    fun updateSettings(newSettings: LlmGenerationSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.saveSettings(newSettings)
            _uiState.update { it.copy(settings = newSettings) }
        }
    }

    fun updateProvider(config: LlmProviderConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.updateProvider(config)
            val list = storageManager.getProviders()
            _uiState.update { it.copy(providers = list) }
        }
    }

    fun toggleProvider(providerId: String, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return@launch
            val updated = provider.copy(isEnabled = isEnabled)
            storageManager.updateProvider(updated)
            val list = storageManager.getProviders()
            _uiState.update { it.copy(providers = list) }
        }
    }

    fun updateProviderApiKey(providerId: String, apiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return@launch
            val updated = provider.copy(apiKey = apiKey)
            storageManager.updateProvider(updated)
            val list = storageManager.getProviders()
            _uiState.update { 
                it.copy(
                    providers = list,
                    notificationMessage = "API Key updated and encrypted in hardware keystore for ${provider.name}"
                ) 
            }
        }
    }

    fun addCustomProvider(provider: LlmProviderConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.updateProvider(provider)
            val list = storageManager.getProviders()
            _uiState.update { 
                it.copy(
                    providers = list,
                    notificationMessage = "Custom provider '${provider.name}' added successfully."
                ) 
            }
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = _uiState.value.providers.firstOrNull { it.id == providerId }
            storageManager.deleteProvider(providerId)
            val list = storageManager.getProviders()
            _uiState.update { 
                it.copy(
                    providers = list,
                    notificationMessage = "Provider '${provider?.name ?: providerId}' removed."
                ) 
            }
        }
    }

    fun resetProvidersToDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            val defaults = storageManager.resetDefaultProviders()
            _uiState.update { 
                it.copy(
                    providers = defaults,
                    notificationMessage = "LLM providers reset to factory defaults."
                ) 
            }
        }
    }

    fun clearAllApiKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.clearAllApiKeys()
            val list = storageManager.getProviders()
            _uiState.update { 
                it.copy(
                    providers = list,
                    notificationMessage = "All stored API keys securely zeroized."
                ) 
            }
        }
    }

    fun pingProvider(providerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            llmManager.pingProvider(providerId)
            val list = storageManager.getProviders()
            _uiState.update { it.copy(providers = list) }
        }
    }

    fun pingAllProviders() {
        viewModelScope.launch(Dispatchers.IO) {
            llmManager.pingAllProviders()
            val list = storageManager.getProviders()
            _uiState.update { it.copy(providers = list) }
        }
    }

    fun addTask(title: String, prompt: String, intervalMin: Int, isRecurring: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = ScheduledTask(
                id = "task_${UUID.randomUUID().toString().take(8)}",
                title = title,
                prompt = prompt,
                intervalMinutes = intervalMin,
                isRecurring = isRecurring,
                isEnabled = true,
                nextRunMillis = System.currentTimeMillis() + (intervalMin * 60 * 1000L)
            )
            storageManager.updateTask(task)
            val list = storageManager.getTasks()
            _uiState.update { it.copy(tasks = list) }
        }
    }

    fun toggleTask(taskId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = _uiState.value.tasks.firstOrNull { it.id == taskId } ?: return@launch
            val updated = task.copy(isEnabled = enabled)
            storageManager.updateTask(updated)
            val list = storageManager.getTasks()
            _uiState.update { it.copy(tasks = list) }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteTask(taskId)
            val list = storageManager.getTasks()
            _uiState.update { it.copy(tasks = list) }
        }
    }

    fun setTaskHistoryFilter(filter: String) {
        _uiState.update { it.copy(taskHistoryFilter = filter) }
    }

    fun runTaskNow(taskId: String) {
        val task = _uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        val executionId = "exec_${UUID.randomUUID().toString().take(8)}"
        val execution = AgentTaskExecution(
            id = executionId,
            taskId = task.id,
            title = task.title,
            prompt = task.prompt,
            status = "running",
            startedAt = System.currentTimeMillis(),
            modelUsed = _uiState.value.settings.activeModelId,
            triggerType = "Manual Run",
            currentStep = "Initializing agent context..."
        )

        executeAgentTask(execution, scheduledTaskId = task.id)
    }

    fun enqueueAdHocTask(title: String, prompt: String, triggerType: String = "Manual Run", simulateDelayMs: Long = 0L) {
        val executionId = "exec_${UUID.randomUUID().toString().take(8)}"
        val isPending = simulateDelayMs > 0L
        val now = System.currentTimeMillis()
        val initialLogs = mutableListOf(
            TaskLogEntry(
                timestamp = now,
                level = "INFO",
                message = "Task initialized via $triggerType",
                details = "Model: ${_uiState.value.settings.activeModelId} • Prompt: ${prompt.take(120)}..."
            )
        )
        if (isPending) {
            initialLogs.add(
                TaskLogEntry(
                    timestamp = now + 10,
                    level = "STEP",
                    message = "Enqueued into pending buffer (${simulateDelayMs}ms queue delay)",
                    details = "Worker slot awaiting dispatch"
                )
            )
        }

        val execution = AgentTaskExecution(
            id = executionId,
            taskId = null,
            title = title.ifBlank { "Autonomous Ad-hoc Task" },
            prompt = prompt,
            status = if (isPending) "pending" else "running",
            startedAt = now,
            modelUsed = _uiState.value.settings.activeModelId,
            triggerType = triggerType,
            currentStep = if (isPending) "Queued in agent worker pipeline..." else "Initializing agent context...",
            logs = initialLogs
        )

        if (isPending) {
            viewModelScope.launch(Dispatchers.IO) {
                storageManager.addTaskExecution(execution)
                val hist = storageManager.getTaskExecutions()
                _uiState.update { it.copy(taskHistory = hist, notificationMessage = "Task '$title' queued as Pending") }

                val job = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        kotlinx.coroutines.delay(simulateDelayMs)
                        // Transition from pending to running
                        val dispatchTime = System.currentTimeMillis()
                        val updatedLogs = execution.logs.toMutableList().apply {
                            add(
                                TaskLogEntry(
                                    timestamp = dispatchTime,
                                    level = "INFO",
                                    message = "Dispatched from queue to active agent runtime",
                                    details = "Worker slot acquired"
                                )
                            )
                        }
                        val runningExec = execution.copy(
                            status = "running",
                            currentStep = "Processing agent prompt...",
                            logs = updatedLogs
                        )
                        storageManager.updateTaskExecution(runningExec)
                        _uiState.update { it.copy(taskHistory = storageManager.getTaskExecutions()) }
                        executeAgentTask(runningExec)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Was cancelled while pending
                    }
                }
                runningTaskJobs[execution.id] = job
            }
        } else {
            executeAgentTask(execution)
        }
    }

    private fun executeAgentTask(initialExecution: AgentTaskExecution, scheduledTaskId: String? = null) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val startLogs = initialExecution.logs.toMutableList().apply {
                if (isEmpty()) {
                    add(
                        TaskLogEntry(
                            timestamp = startTime,
                            level = "INFO",
                            message = "Agent execution loop initiated",
                            details = "Trigger: ${initialExecution.triggerType} • Model: ${initialExecution.modelUsed}"
                        )
                    )
                }
                add(
                    TaskLogEntry(
                        timestamp = startTime + 20,
                        level = "INFO",
                        message = "Agent execution context loaded",
                        details = "Attached prompt (${initialExecution.prompt.length} chars)"
                    )
                )
            }

            var exec = initialExecution.copy(status = "running", startedAt = startTime, logs = startLogs)
            storageManager.updateTaskExecution(exec)

            if (scheduledTaskId != null) {
                val task = _uiState.value.tasks.firstOrNull { it.id == scheduledTaskId }
                if (task != null) {
                    storageManager.updateTask(task.copy(lastStatus = "running"))
                }
            }

            _uiState.update {
                it.copy(
                    taskHistory = storageManager.getTaskExecutions(),
                    tasks = storageManager.getTasks()
                )
            }

            var toolCallsCount = 0

            try {
                val tempConvId = "task_exec_${exec.id}"
                val res = agentEngine.processUserMessage(
                    conversationId = tempConvId,
                    userText = exec.prompt,
                    isNetworkAvailable = _uiState.value.isNetworkOnline,
                    onDelta = { _, _ -> },
                    onToolStarted = { tool ->
                        toolCallsCount++
                        val toolLog = TaskLogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = "TOOL",
                            message = "Agent invoking tool: ${tool.name}",
                            details = "Autonomous tool call (#$toolCallsCount)"
                        )
                        val newLogs = exec.logs + toolLog
                        exec = exec.copy(
                            currentStep = "Invoking tool: ${tool.name}...",
                            toolCallsCount = toolCallsCount,
                            progressPercent = 0.5f,
                            logs = newLogs
                        )
                        viewModelScope.launch(Dispatchers.IO) {
                            storageManager.updateTaskExecution(exec)
                            _uiState.update { it.copy(taskHistory = storageManager.getTaskExecutions()) }
                        }
                    },
                    onToolFinished = { tool ->
                        val toolFinishedLog = TaskLogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = "TOOL",
                            message = "Completed tool: ${tool.name}",
                            details = "Result returned to agent reasoning engine"
                        )
                        val newLogs = exec.logs + toolFinishedLog
                        exec = exec.copy(
                            currentStep = "Completed tool: ${tool.name}",
                            progressPercent = 0.8f,
                            logs = newLogs
                        )
                        viewModelScope.launch(Dispatchers.IO) {
                            storageManager.updateTaskExecution(exec)
                            _uiState.update { it.copy(taskHistory = storageManager.getTaskExecutions()) }
                        }
                    },
                    onStatusUpdate = { status ->
                        val statusLog = TaskLogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = "STEP",
                            message = status,
                            details = null
                        )
                        val newLogs = exec.logs + statusLog
                        exec = exec.copy(currentStep = status, logs = newLogs)
                        viewModelScope.launch(Dispatchers.IO) {
                            storageManager.updateTaskExecution(exec)
                            _uiState.update { it.copy(taskHistory = storageManager.getTaskExecutions()) }
                        }
                    }
                )

                val endTime = System.currentTimeMillis()
                val isError = res.content.startsWith("Error:") || res.content.contains("Exception")

                val completionLog = TaskLogEntry(
                    timestamp = endTime,
                    level = if (isError) "ERROR" else "INFO",
                    message = if (isError) "Agent execution failed with error" else "Agent execution completed successfully (${endTime - startTime}ms)",
                    details = if (isError) res.content else "Output length: ${res.content.length} chars • Model: ${exec.modelUsed}"
                )
                val finalLogs = exec.logs + completionLog

                val finishedExec = exec.copy(
                    status = if (isError) "failed" else "completed",
                    finishedAt = endTime,
                    durationMs = (endTime - startTime).coerceAtLeast(100L),
                    outputSummary = if (!isError) res.content else null,
                    errorMessage = if (isError) res.content else null,
                    progressPercent = 1.0f,
                    currentStep = if (isError) "Failed during execution" else "Completed successfully in ${(endTime - startTime)}ms",
                    logs = finalLogs
                )

                storageManager.updateTaskExecution(finishedExec)

                if (scheduledTaskId != null) {
                    val task = _uiState.value.tasks.firstOrNull { it.id == scheduledTaskId }
                    if (task != null) {
                        val finishedTask = task.copy(
                            lastStatus = if (isError) "failed" else "success",
                            lastOutput = res.content.take(150),
                            lastRunMillis = endTime,
                            nextRunMillis = if (task.isRecurring && task.intervalMinutes > 0) {
                                endTime + (task.intervalMinutes * 60 * 1000L)
                            } else 0L
                        )
                        storageManager.updateTask(finishedTask)
                    }
                }

                _uiState.update {
                    it.copy(
                        taskHistory = storageManager.getTaskExecutions(),
                        tasks = storageManager.getTasks(),
                        notificationMessage = if (isError) "Task '${exec.title}' failed" else "Task '${exec.title}' completed successfully!"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                val endTime = System.currentTimeMillis()
                val cancelLog = TaskLogEntry(
                    timestamp = endTime,
                    level = "WARN",
                    message = "Execution cancelled by user",
                    details = "Job aborted while in flight (${endTime - startTime}ms elapsed)"
                )
                val cancelledExec = exec.copy(
                    status = "cancelled",
                    finishedAt = endTime,
                    durationMs = (endTime - startTime).coerceAtLeast(50L),
                    cancellationReason = "Cancelled by user during execution",
                    currentStep = "Execution cancelled by user",
                    logs = exec.logs + cancelLog
                )
                storageManager.updateTaskExecution(cancelledExec)
                if (scheduledTaskId != null) {
                    val task = _uiState.value.tasks.firstOrNull { it.id == scheduledTaskId }
                    if (task != null) {
                        storageManager.updateTask(task.copy(lastStatus = "cancelled"))
                    }
                }
                _uiState.update {
                    it.copy(
                        taskHistory = storageManager.getTaskExecutions(),
                        tasks = storageManager.getTasks(),
                        notificationMessage = "Task '${exec.title}' cancelled."
                    )
                }
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val errLog = TaskLogEntry(
                    timestamp = endTime,
                    level = "ERROR",
                    message = "Fatal execution exception: ${e.javaClass.simpleName}",
                    details = e.localizedMessage ?: "Unknown error"
                )
                val failedExec = exec.copy(
                    status = "failed",
                    finishedAt = endTime,
                    durationMs = (endTime - startTime).coerceAtLeast(100L),
                    errorMessage = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Unknown execution failure"}",
                    currentStep = "Encountered fatal exception",
                    logs = exec.logs + errLog
                )
                storageManager.updateTaskExecution(failedExec)
                if (scheduledTaskId != null) {
                    val task = _uiState.value.tasks.firstOrNull { it.id == scheduledTaskId }
                    if (task != null) {
                        storageManager.updateTask(task.copy(lastStatus = "failed", lastOutput = "Error: ${e.localizedMessage}"))
                    }
                }
                _uiState.update {
                    it.copy(
                        taskHistory = storageManager.getTaskExecutions(),
                        tasks = storageManager.getTasks(),
                        notificationMessage = "Task '${exec.title}' failed: ${e.message}"
                    )
                }
            } finally {
                runningTaskJobs.remove(initialExecution.id)
            }
        }

        runningTaskJobs[initialExecution.id] = job
    }

    fun cancelTaskExecution(executionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val job = runningTaskJobs.remove(executionId)
            job?.cancel()

            val existing = _uiState.value.taskHistory.firstOrNull { it.id == executionId }
            if (existing != null) {
                val endTime = System.currentTimeMillis()
                val cancelLog = TaskLogEntry(
                    timestamp = endTime,
                    level = "WARN",
                    message = "Execution cancelled by user",
                    details = "Aborted active background job"
                )
                val cancelled = existing.copy(
                    status = "cancelled",
                    finishedAt = endTime,
                    durationMs = if (existing.startedAt > 0) (endTime - existing.startedAt).coerceAtLeast(50L) else 0L,
                    cancellationReason = "Cancelled by user",
                    currentStep = "Cancelled by user",
                    logs = existing.logs + cancelLog
                )
                storageManager.updateTaskExecution(cancelled)

                if (existing.taskId != null) {
                    val task = _uiState.value.tasks.firstOrNull { it.id == existing.taskId }
                    if (task != null) {
                        storageManager.updateTask(task.copy(lastStatus = "cancelled"))
                    }
                }

                _uiState.update {
                    it.copy(
                        taskHistory = storageManager.getTaskExecutions(),
                        tasks = storageManager.getTasks(),
                        notificationMessage = "Task '${existing.title}' cancelled successfully."
                    )
                }
            }
        }
    }

    fun retryTaskExecution(executionId: String) {
        val existing = _uiState.value.taskHistory.firstOrNull { it.id == executionId } ?: return
        enqueueAdHocTask(title = existing.title, prompt = existing.prompt, triggerType = "Retry Run")
    }

    fun deleteTaskExecution(executionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runningTaskJobs.remove(executionId)?.cancel()
            storageManager.deleteTaskExecution(executionId)
            _uiState.update {
                it.copy(
                    taskHistory = storageManager.getTaskExecutions(),
                    notificationMessage = "Task record removed from history."
                )
            }
        }
    }

    fun clearTaskHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            runningTaskJobs.forEach { (_, job) -> job.cancel() }
            runningTaskJobs.clear()
            storageManager.clearTaskHistory()
            _uiState.update {
                it.copy(
                    taskHistory = emptyList(),
                    notificationMessage = "Task execution history cleared."
                )
            }
        }
    }

    fun executeCodeSnippet(code: String, language: String) {
        sendMessage("Run code:\n```$language\n$code\n```")
    }

    fun startVoice() {
        voiceInputManager.startListening(
            onResult = { text ->
                if (text.isNotBlank()) {
                    sendMessage(text)
                }
            },
            onError = { err ->
                _uiState.update { it.copy(notificationMessage = err) }
            }
        )
    }

    fun startVoiceForTaskParsing() {
        voiceInputManager.startListening(
            onResult = { text ->
                if (text.isNotBlank()) {
                    parseAndSaveVoiceTask(text)
                }
            },
            onError = { err ->
                _uiState.update { it.copy(notificationMessage = err) }
            }
        )
    }

    fun parseAndSaveVoiceTask(spokenText: String) {
        if (spokenText.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isParsingVoiceTask = true,
                    notificationMessage = "🤖 Gemini AI structuring voice task into Room DB..."
                )
            }

            var title = ""
            var prompt = spokenText
            var category = "Work"
            var urgency = "MEDIUM"
            var scheduledTime = "Today"
            var duration = "30 mins"
            var intervalMinutes = 0
            var isRecurring = false

            try {
                val parserPrompt = """
                    You are an AI task parsing assistant. The user spoke the following task description:
                    "$spokenText"

                    Extract and structure this task into a single valid JSON object with the following fields:
                    {
                      "title": "<Short concise task title, max 6 words>",
                      "prompt": "<Full detailed action prompt or description>",
                      "category": "<Must be one of: Work, Personal, Urgent, Automations>",
                      "urgency": "<Must be one of: HIGH, MEDIUM, LOW>",
                      "scheduledTime": "<Inferred execution time like '10:30 AM', 'Tomorrow 9:00 AM', or 'Today 02:00 PM'>",
                      "duration": "<Estimated duration like '15 mins', '30 mins', '1 hour'>",
                      "intervalMinutes": <Integer minutes if recurring, e.g. 60, otherwise 0>,
                      "isRecurring": <Boolean true or false>
                    }

                    Respond strictly with valid JSON only. No markdown formatting.
                """.trimIndent()

                val msg = Message(id = UUID.randomUUID().toString(), conversationId = "temp", role = "user", content = parserPrompt)
                val resultSb = StringBuilder()

                val res = llmManager.generateStream(
                    messages = listOf(msg),
                    isNetworkAvailable = _uiState.value.isNetworkOnline,
                    onChunk = { delta, _ -> resultSb.append(delta) },
                    onFallbackTriggered = { _, _ -> }
                )

                val rawJson = resultSb.toString().trim()
                val cleanJsonStr = rawJson.replace("```json", "").replace("```", "").trim()

                if (cleanJsonStr.startsWith("{") && cleanJsonStr.endsWith("}")) {
                    val json = org.json.JSONObject(cleanJsonStr)
                    title = json.optString("title", "").ifBlank { extractTitleFallback(spokenText) }
                    prompt = json.optString("prompt", spokenText)
                    category = json.optString("category", inferCategoryFallback(spokenText))
                    urgency = json.optString("urgency", inferUrgencyFallback(spokenText))
                    scheduledTime = json.optString("scheduledTime", inferTimeFallback(spokenText))
                    duration = json.optString("duration", "30 mins")
                    intervalMinutes = json.optInt("intervalMinutes", 0)
                    isRecurring = json.optBoolean("isRecurring", false)
                } else {
                    title = extractTitleFallback(spokenText)
                    category = inferCategoryFallback(spokenText)
                    urgency = inferUrgencyFallback(spokenText)
                    scheduledTime = inferTimeFallback(spokenText)
                }
            } catch (e: Exception) {
                title = extractTitleFallback(spokenText)
                category = inferCategoryFallback(spokenText)
                urgency = inferUrgencyFallback(spokenText)
                scheduledTime = inferTimeFallback(spokenText)
            }

            val newTask = VoiceTaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                prompt = prompt,
                category = category,
                urgency = urgency,
                scheduledTime = scheduledTime,
                duration = duration,
                intervalMinutes = intervalMinutes,
                isRecurring = isRecurring,
                isCompleted = false,
                isRunning = false,
                rawVoiceTranscript = spokenText,
                createdAt = System.currentTimeMillis()
            )

            // Insert into Room DB
            storageManager.insertVoiceTask(newTask)

            // Sync to ScheduledTask list
            val schedTask = newTask.toScheduledTask()
            val currentTasks = storageManager.getTasks().toMutableList()
            currentTasks.add(0, schedTask)
            storageManager.saveTasks(currentTasks)

            _uiState.update { state ->
                state.copy(
                    tasks = currentTasks,
                    isParsingVoiceTask = false,
                    lastParsedVoiceTask = newTask,
                    notificationMessage = "🔒 Saved to Room DB: \"$title\" ($category • $urgency)"
                )
            }
        }
    }

    fun deleteVoiceTask(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteVoiceTask(id)
            _uiState.update { it.copy(notificationMessage = "Voice moment removed from Room DB.") }
        }
    }

    fun toggleVoiceTaskComplete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = storageManager.getVoiceTasks()
            val task = tasks.firstOrNull { it.id == id }
            if (task != null) {
                val updated = task.copy(isCompleted = !task.isCompleted)
                storageManager.updateVoiceTask(updated)
            }
        }
    }

    private fun extractTitleFallback(text: String): String {
        val cleaned = text
            .replace(Regex("(?i)^(please|can you|schedule a|remind me to|create a task to|add a moment to|i need to)\\s*"), "")
            .replace(Regex("(?i)\\s*(tomorrow|today|at \\d+:\\d+|asap|high priority)$"), "")
            .trim()
        return if (cleaned.length > 35) cleaned.take(35) + "..." else cleaned.replaceFirstChar { it.uppercase() }
    }

    private fun inferCategoryFallback(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("urgent") || lower.contains("asap") || lower.contains("critical") -> "Urgent"
            lower.contains("work") || lower.contains("meeting") || lower.contains("code") || lower.contains("audit") || lower.contains("report") -> "Work"
            lower.contains("personal") || lower.contains("health") || lower.contains("walk") || lower.contains("gym") || lower.contains("home") -> "Personal"
            lower.contains("cron") || lower.contains("sync") || lower.contains("automate") || lower.contains("backup") -> "Automations"
            else -> "Work"
        }
    }

    private fun inferUrgencyFallback(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("high") || lower.contains("urgent") || lower.contains("asap") || lower.contains("critical") -> "HIGH"
            lower.contains("low") || lower.contains("someday") || lower.contains("casual") -> "LOW"
            else -> "MEDIUM"
        }
    }

    private fun inferTimeFallback(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("tomorrow") -> "Tomorrow 09:00 AM"
            lower.contains("tonight") -> "Tonight 08:00 PM"
            lower.contains("morning") -> "Today 09:00 AM"
            lower.contains("afternoon") -> "Today 02:00 PM"
            else -> "Today"
        }
    }

    fun stopVoice() {
        voiceInputManager.stopListening()
    }

    fun toggleVoiceInput() {
        if (_uiState.value.isVoiceActive) {
            stopVoice()
        } else {
            startVoice()
        }
    }

    fun refreshWorkspaceFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val ws = storageManager.listWorkspaceFiles()
            val internal = storageManager.listAllInternalFiles()
            _uiState.update {
                it.copy(
                    workspaceFiles = ws,
                    internalEncryptedFiles = internal
                )
            }
        }
    }

    fun writeWorkspaceFile(name: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.writeWorkspaceFile(name, content)
            refreshWorkspaceFiles()
        }
    }

    fun deleteWorkspaceFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteWorkspaceFile(name)
            refreshWorkspaceFiles()
        }
    }

    fun createBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = storageManager.createBackup()
            val checksum = storageManager.calculateSha256(file)
            _uiState.update {
                it.copy(notificationMessage = "Backup created: ${file.name}\nSHA-256: ${checksum.take(12)}...")
            }
            refreshWorkspaceFiles()
        }
    }

    fun restoreBackup(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = storageManager.restoreBackup(file)
            if (ok) {
                loadInitialData()
                _uiState.update { it.copy(notificationMessage = "Backup restored successfully!") }
            } else {
                _uiState.update { it.copy(notificationMessage = "Failed to restore backup.") }
            }
        }
    }

    suspend fun getRawEncryptedFile(fileInfo: WorkspaceFileInfo): ByteArray? {
        val root = File(getApplication<Application>().filesDir, "nanobot")
        val file = File(root, fileInfo.relativePath)
        return storageManager.getRawEncryptedBytes(file)
    }

    suspend fun getDecryptedFile(fileInfo: WorkspaceFileInfo): String? {
        val root = File(getApplication<Application>().filesDir, "nanobot")
        val file = File(root, fileInfo.relativePath)
        return if (fileInfo.isEncrypted) {
            storageManager.readEncryptedFile(file)
        } else {
            storageManager.readWorkspaceFile(fileInfo.relativePath.removePrefix("workspace/"))
        }
    }

    fun clearNotification() {
        _uiState.update { it.copy(notificationMessage = null) }
    }

    // -------------------------------------------------------------
    // Communication Channels Operations
    // -------------------------------------------------------------

    fun updateChannelsConfig(config: CommunicationChannelsConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.saveChannelsConfig(config)
            _uiState.update { it.copy(channelsConfig = config, notificationMessage = "Channel configurations saved securely.") }
        }
    }

    fun updateWhatsAppConfig(waConfig: WhatsAppConfig) {
        val current = _uiState.value.channelsConfig
        val updated = current.copy(whatsapp = waConfig)
        updateChannelsConfig(updated)
    }

    fun updateTelegramConfig(tgConfig: TelegramConfig) {
        val current = _uiState.value.channelsConfig
        val updated = current.copy(telegram = tgConfig)
        updateChannelsConfig(updated)
    }

    fun updateSmsConfig(smsConfig: SmsConfig) {
        val current = _uiState.value.channelsConfig
        val updated = current.copy(sms = smsConfig)
        updateChannelsConfig(updated)
    }

    fun updateEmailConfig(emailConfig: EmailConfig) {
        val current = _uiState.value.channelsConfig
        val updated = current.copy(email = emailConfig)
        updateChannelsConfig(updated)
    }

    fun testWhatsAppChannel(recipient: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChannelSending = true) }
            val config = _uiState.value.channelsConfig.whatsapp
            val result = channelDispatchService.sendWhatsAppMessage(config, recipient, message)
            val log = ChannelTransmissionLog(
                id = UUID.randomUUID().toString(),
                channel = ChannelType.WHATSAPP,
                recipient = recipient.ifBlank { config.defaultRecipient },
                summary = message.take(80),
                status = if (result.isSuccess) "SENT" else "FAILED",
                latencyMs = result.latencyMs,
                errorDetails = result.error
            )
            storageManager.addTransmissionLog(log)
            val updatedWa = config.copy(
                lastPingLatencyMs = result.latencyMs,
                status = if (result.isSuccess) "Connected" else "Error"
            )
            storageManager.saveChannelsConfig(_uiState.value.channelsConfig.copy(whatsapp = updatedWa))
            val logs = storageManager.getTransmissionLogs()

            _uiState.update {
                it.copy(
                    isChannelSending = false,
                    channelsConfig = it.channelsConfig.copy(whatsapp = updatedWa),
                    transmissionLogs = logs,
                    notificationMessage = if (result.isSuccess) "WhatsApp: Test sent successfully (${result.latencyMs}ms)" else "WhatsApp: ${result.error ?: result.message}"
                )
            }
        }
    }

    fun verifyTelegramBot() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChannelSending = true) }
            val config = _uiState.value.channelsConfig.telegram
            val (ok, details) = channelDispatchService.verifyTelegramBot(config)
            val updatedTg = config.copy(
                status = if (ok) "Connected" else "Error"
            )
            storageManager.saveChannelsConfig(_uiState.value.channelsConfig.copy(telegram = updatedTg))
            _uiState.update {
                it.copy(
                    isChannelSending = false,
                    channelsConfig = it.channelsConfig.copy(telegram = updatedTg),
                    notificationMessage = details
                )
            }
        }
    }

    fun testTelegramChannel(chatId: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChannelSending = true) }
            val config = _uiState.value.channelsConfig.telegram
            val result = channelDispatchService.sendTelegramMessage(config, chatId, message)
            val log = ChannelTransmissionLog(
                id = UUID.randomUUID().toString(),
                channel = ChannelType.TELEGRAM,
                recipient = chatId.ifBlank { config.defaultChatId },
                summary = message.take(80),
                status = if (result.isSuccess) "SENT" else "FAILED",
                latencyMs = result.latencyMs,
                errorDetails = result.error
            )
            storageManager.addTransmissionLog(log)
            val updatedTg = config.copy(
                lastPingLatencyMs = result.latencyMs,
                status = if (result.isSuccess) "Connected" else "Error"
            )
            storageManager.saveChannelsConfig(_uiState.value.channelsConfig.copy(telegram = updatedTg))
            val logs = storageManager.getTransmissionLogs()

            _uiState.update {
                it.copy(
                    isChannelSending = false,
                    channelsConfig = it.channelsConfig.copy(telegram = updatedTg),
                    transmissionLogs = logs,
                    notificationMessage = if (result.isSuccess) "Telegram: Delivered to $chatId (${result.latencyMs}ms)" else "Telegram: ${result.error ?: result.message}"
                )
            }
        }
    }

    fun testSmsChannel(recipient: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChannelSending = true) }
            val config = _uiState.value.channelsConfig.sms
            val result = channelDispatchService.sendSmsMessage(config, recipient, message)
            val log = ChannelTransmissionLog(
                id = UUID.randomUUID().toString(),
                channel = ChannelType.SMS,
                recipient = recipient.ifBlank { config.defaultRecipient },
                summary = message.take(80),
                status = if (result.isSuccess) "SENT" else "FAILED",
                latencyMs = result.latencyMs,
                errorDetails = result.error
            )
            storageManager.addTransmissionLog(log)
            val updatedSms = config.copy(
                lastPingLatencyMs = result.latencyMs,
                status = if (result.isSuccess) "Ready" else "Error"
            )
            storageManager.saveChannelsConfig(_uiState.value.channelsConfig.copy(sms = updatedSms))
            val logs = storageManager.getTransmissionLogs()

            _uiState.update {
                it.copy(
                    isChannelSending = false,
                    channelsConfig = it.channelsConfig.copy(sms = updatedSms),
                    transmissionLogs = logs,
                    notificationMessage = if (result.isSuccess) "SMS: Sent successfully (${result.latencyMs}ms)" else "SMS: ${result.error ?: result.message}"
                )
            }
        }
    }

    fun testEmailChannel(to: String, subject: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChannelSending = true) }
            val config = _uiState.value.channelsConfig.email
            val result = channelDispatchService.sendEmailMessage(config, to, subject, body, isHtml = true)
            val log = ChannelTransmissionLog(
                id = UUID.randomUUID().toString(),
                channel = ChannelType.EMAIL,
                recipient = to.ifBlank { config.defaultToEmail },
                summary = "$subject: ${body.take(60)}",
                status = if (result.isSuccess) "SENT" else "FAILED",
                latencyMs = result.latencyMs,
                errorDetails = result.error
            )
            storageManager.addTransmissionLog(log)
            val updatedEmail = config.copy(
                lastPingLatencyMs = result.latencyMs,
                status = if (result.isSuccess) "Connected" else "Error"
            )
            storageManager.saveChannelsConfig(_uiState.value.channelsConfig.copy(email = updatedEmail))
            val logs = storageManager.getTransmissionLogs()

            _uiState.update {
                it.copy(
                    isChannelSending = false,
                    channelsConfig = it.channelsConfig.copy(email = updatedEmail),
                    transmissionLogs = logs,
                    notificationMessage = if (result.isSuccess) "Email: Dispatched successfully (${result.latencyMs}ms)" else "Email: ${result.error ?: result.message}"
                )
            }
        }
    }

    fun broadcastChannelAlert(title: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChannelSending = true) }
            val config = _uiState.value.channelsConfig
            val results = channelDispatchService.broadcastMultichannel(config, title, message)
            results.forEach { res ->
                val log = ChannelTransmissionLog(
                    id = UUID.randomUUID().toString(),
                    channel = res.channel,
                    recipient = "Broadcast",
                    summary = "$title: ${message.take(60)}",
                    status = if (res.isSuccess) "SENT" else "FAILED",
                    latencyMs = res.latencyMs,
                    errorDetails = res.error
                )
                storageManager.addTransmissionLog(log)
            }
            val logs = storageManager.getTransmissionLogs()
            val successfulCount = results.count { it.isSuccess }
            _uiState.update {
                it.copy(
                    isChannelSending = false,
                    transmissionLogs = logs,
                    notificationMessage = "Broadcast complete: $successfulCount/${results.size} channels notified."
                )
            }
        }
    }

    fun clearTransmissionLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.clearTransmissionLogs()
            _uiState.update { it.copy(transmissionLogs = emptyList(), notificationMessage = "Transmission history cleared.") }
        }
    }

    // -------------------------------------------------------------
    // Autonomous Agent Fleet & Dynamic Tool Manager Persistence Bridge
    // -------------------------------------------------------------

    /**
     * High-performance updateAgent persistence bridge to synchronize tool selections in real-time.
     */
    fun updateAgent(agent: AgentEntity) {
        // 1. Instant local state update for zero-latency UI reactivity
        _uiState.update { current ->
            val updatedList = current.agents.map { if (it.id == agent.id) agent else it }.toMutableList()
            if (!updatedList.any { it.id == agent.id }) {
                updatedList.add(agent)
            }
            current.copy(agents = updatedList)
        }

        // 2. High-performance async persistence to Room Database & Encrypted Zero-DB file storage
        viewModelScope.launch(Dispatchers.IO) {
            try {
                agentDao.insertOrUpdateAgent(agent)
                storageManager.saveAgent(agent)
            } catch (e: Exception) {
                // Ignore or handle fallback
            }
        }
    }

    fun toggleAgentTool(agentId: String, toolKey: String, isEnabled: Boolean) {
        val agent = _uiState.value.agents.firstOrNull { it.id == agentId } ?: return
        val updated = agent.withToggledTool(toolKey, isEnabled)
        updateAgent(updated)
    }

    fun updateAgentTools(agentId: String, tools: List<String>) {
        val agent = _uiState.value.agents.firstOrNull { it.id == agentId } ?: return
        val updated = agent.copy(tools = tools, updatedAt = System.currentTimeMillis())
        updateAgent(updated)
    }

    fun selectActiveAgent(agentId: String) {
        _uiState.update { it.copy(selectedAgentId = agentId) }
        val agent = _uiState.value.agents.firstOrNull { it.id == agentId }
        if (agent != null) {
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(
                        activeModelId = agent.modelId,
                        activeProviderId = agent.providerId,
                        systemPrompt = agent.systemPrompt,
                        temperature = agent.temperature
                    ),
                    notificationMessage = "Switched active agent to '${agent.name}' (${agent.tools.size} capabilities active)."
                )
            }
        }
    }

    fun createAgent(agent: AgentEntity) {
        updateAgent(agent)
        _uiState.update { it.copy(notificationMessage = "Agent '${agent.name}' created with ${agent.tools.size} tools.") }
    }

    fun deleteAgent(agentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            agentDao.deleteAgentById(agentId)
            storageManager.deleteAgent(agentId)
            val remaining = agentDao.getAllAgents()
            _uiState.update {
                it.copy(
                    agents = remaining,
                    selectedAgentId = if (it.selectedAgentId == agentId) remaining.firstOrNull()?.id ?: "" else it.selectedAgentId,
                    notificationMessage = "Agent deleted from registry."
                )
            }
        }
    }

    fun resetDefaultAgents() {
        viewModelScope.launch(Dispatchers.IO) {
            val defaults = AgentEntity.getDefaultAgents()
            agentDao.clearAllAgents()
            agentDao.insertAgents(defaults)
            storageManager.resetDefaultAgents()
            _uiState.update {
                it.copy(
                    agents = defaults,
                    selectedAgentId = defaults.firstOrNull()?.id ?: "agent_openclaw_prime",
                    notificationMessage = "Restored default autonomous agent fleet (${defaults.size} agents)."
                )
            }
        }
    }
}
