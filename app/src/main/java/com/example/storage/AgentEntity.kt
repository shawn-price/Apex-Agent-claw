package com.example.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Definition of an Agent Tool / Capability in the system
 */
data class AgentToolDefinition(
    val key: String,
    val displayName: String,
    val category: String,
    val description: String,
    val sampleUsage: String,
    val iconName: String,
    val colorHex: Long
)

object AgentCapabilitiesRegistry {
    val TOOL_SEARCH = AgentToolDefinition(
        key = "Search",
        displayName = "Search (Web Intelligence)",
        category = "Research & Discovery",
        description = "Live web search, page snippet extraction, source verification, and citation synthesis.",
        sampleUsage = "web_search(query = \"latest quantum computing breakthroughs\")",
        iconName = "Search",
        colorHex = 0xFF06B6D4 // CyanAccent
    )

    val TOOL_CODE_INTERPRETER = AgentToolDefinition(
        key = "CodeInterpreter",
        displayName = "Code Interpreter & Sandbox",
        category = "Compute & Development",
        description = "In-memory sandboxed Python & JavaScript script execution with variable inspection.",
        sampleUsage = "execute_code(code = \"import math; res = math.sqrt(144)\", language = \"python\")",
        iconName = "Code",
        colorHex = 0xFFA855F7 // PurpleAccent
    )

    val TOOL_VISUAL_GENERATOR = AgentToolDefinition(
        key = "VisualGenerator",
        displayName = "Visual Generator (AI Images)",
        category = "Creative & Multimodal",
        description = "Generative visual synthesis, UI diagram creation, and multimodal asset rendering.",
        sampleUsage = "generate_image(prompt = \"high-tech holographic control dashboard\")",
        iconName = "Palette",
        colorHex = 0xFFF59E0B // AmberGold
    )

    val TOOL_FILE_OPERATIONS = AgentToolDefinition(
        key = "FileOperations",
        displayName = "File Operations (Zero-DB)",
        category = "Storage & Security",
        description = "Read, write, list, and delete files inside the hardware-encrypted AES-256 workspace.",
        sampleUsage = "file_write(path = \"audit.json\", content = \"{ ... }\")",
        iconName = "FolderOpen",
        colorHex = 0xFF10B981 // EmeraldPrimary
    )

    val TOOL_CHANNEL_DISPATCH = AgentToolDefinition(
        key = "ChannelDispatch",
        displayName = "Omnichannel Dispatcher",
        category = "Communication",
        description = "Real-time automated message dispatching across WhatsApp, Telegram, SMS, and Email.",
        sampleUsage = "broadcast_alert(title = \"System Alert\", message = \"Backup completed.\")",
        iconName = "Send",
        colorHex = 0xFF34D399 // EmeraldLight
    )

    val TOOL_TASK_SCHEDULER = AgentToolDefinition(
        key = "TaskScheduler",
        displayName = "Task Scheduler & Cron",
        category = "Automation",
        description = "Automated background cron triggers, interval routines, and event-driven task loops.",
        sampleUsage = "schedule_task(title = \"Daily Sync\", intervalMinutes = 1440, isRecurring = true)",
        iconName = "Schedule",
        colorHex = 0xFFEF4444 // RubyRed
    )

    val TOOL_VOICE_SYNTHESIZER = AgentToolDefinition(
        key = "VoiceSynthesizer",
        displayName = "Voice Synthesizer (Neural I/O)",
        category = "Audio & Speech",
        description = "Speech recognition input stream processing and neural audio acoustic feedback.",
        sampleUsage = "voice_synthesize(text = \"Executing analysis protocol.\")",
        iconName = "Mic",
        colorHex = 0xFF22D3EE // CyanGlow
    )

    val ALL_AVAILABLE_TOOLS = listOf(
        TOOL_SEARCH,
        TOOL_CODE_INTERPRETER,
        TOOL_VISUAL_GENERATOR,
        TOOL_FILE_OPERATIONS,
        TOOL_CHANNEL_DISPATCH,
        TOOL_TASK_SCHEDULER,
        TOOL_VOICE_SYNTHESIZER
    )

    fun getToolByKey(key: String): AgentToolDefinition? {
        return ALL_AVAILABLE_TOOLS.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/**
 * Persistent Agent Entity maintaining the registry of assigned capabilities (tools).
 */
@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val description: String,
    val systemPrompt: String,
    val modelId: String = "gemini-3.5-flash",
    val providerId: String = "gemini",
    val tools: List<String> = emptyList(), // Persistent registry of assigned capabilities
    val avatarEmoji: String = "🤖",
    val avatarColorHex: Long = 0xFF10B981,
    val temperature: Float = 0.7f,
    val isAutonomous: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun hasTool(toolKey: String): Boolean {
        return tools.any { it.equals(toolKey, ignoreCase = true) }
    }

    fun withToggledTool(toolKey: String, enabled: Boolean): AgentEntity {
        val current = tools.toMutableList()
        val normalized = AgentCapabilitiesRegistry.getToolByKey(toolKey)?.key ?: toolKey
        if (enabled) {
            if (!current.any { it.equals(normalized, ignoreCase = true) }) {
                current.add(normalized)
            }
        } else {
            current.removeAll { it.equals(normalized, ignoreCase = true) }
        }
        return copy(tools = current, updatedAt = System.currentTimeMillis())
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("role", role)
        put("description", description)
        put("systemPrompt", systemPrompt)
        put("modelId", modelId)
        put("providerId", providerId)
        val toolsArray = JSONArray()
        tools.forEach { toolsArray.put(it) }
        put("tools", toolsArray)
        put("avatarEmoji", avatarEmoji)
        put("avatarColorHex", avatarColorHex)
        put("temperature", temperature.toDouble())
        put("isAutonomous", isAutonomous)
        put("isEnabled", isEnabled)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): AgentEntity {
            val toolsList = mutableListOf<String>()
            val toolsArray = json.optJSONArray("tools")
            if (toolsArray != null) {
                for (i in 0 until toolsArray.length()) {
                    toolsList.add(toolsArray.getString(i))
                }
            }

            return AgentEntity(
                id = json.getString("id"),
                name = json.optString("name", "Custom Agent"),
                role = json.optString("role", "Autonomous Assistant"),
                description = json.optString("description", ""),
                systemPrompt = json.optString("systemPrompt", ""),
                modelId = json.optString("modelId", "gemini-3.5-flash"),
                providerId = json.optString("providerId", "gemini"),
                tools = toolsList,
                avatarEmoji = json.optString("avatarEmoji", "🤖"),
                avatarColorHex = json.optLong("avatarColorHex", 0xFF10B981),
                temperature = json.optDouble("temperature", 0.7).toFloat(),
                isAutonomous = json.optBoolean("isAutonomous", true),
                isEnabled = json.optBoolean("isEnabled", true),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        fun getDefaultAgents(): List<AgentEntity> {
            val now = System.currentTimeMillis()
            return listOf(
                AgentEntity(
                    id = "agent_openclaw_prime",
                    name = "OpenClaw Prime",
                    role = "Master Autonomous Hub Orchestrator",
                    description = "Full-spectrum autonomous executive agent equipped with dynamic tool invocation, multi-channel broadcast, and zero-DB file operations.",
                    systemPrompt = "You are OpenClaw Prime, the master autonomous orchestrator. Coordinate tasks, synthesize research, execute code, and dispatch critical alerts via your assigned tools.",
                    modelId = "gemini-3.5-flash",
                    providerId = "gemini",
                    tools = listOf("Search", "CodeInterpreter", "VisualGenerator", "FileOperations", "ChannelDispatch", "TaskScheduler", "VoiceSynthesizer"),
                    avatarEmoji = "🦉",
                    avatarColorHex = 0xFF10B981, // EmeraldPrimary
                    temperature = 0.7f,
                    isAutonomous = true,
                    isEnabled = true,
                    createdAt = now,
                    updatedAt = now
                ),
                AgentEntity(
                    id = "agent_researcher",
                    name = "Research Navigator",
                    role = "Deep Web & Academic Researcher",
                    description = "Specialized in high-speed web queries, source extraction, multi-domain fact validation, and zero-DB encrypted knowledge logs.",
                    systemPrompt = "You are the Research Navigator. Use live web search and file operations to gather, verify, summarize, and archive actionable intelligence.",
                    modelId = "gemini-3.5-flash",
                    providerId = "gemini",
                    tools = listOf("Search", "FileOperations", "VoiceSynthesizer"),
                    avatarEmoji = "🔍",
                    avatarColorHex = 0xFF06B6D4, // CyanAccent
                    temperature = 0.5f,
                    isAutonomous = true,
                    isEnabled = true,
                    createdAt = now,
                    updatedAt = now
                ),
                AgentEntity(
                    id = "agent_coder",
                    name = "Code Architect",
                    role = "Full-Stack Engineer & Sandbox Runner",
                    description = "Specialized in Python and JavaScript script generation, algorithmic solutions, automated unit testing, and sandbox execution.",
                    systemPrompt = "You are the Code Architect. Write clean, idiomatic code, execute sandboxed scripts to verify results, and persist code artifacts to workspace.",
                    modelId = "gemini-3.5-flash",
                    providerId = "gemini",
                    tools = listOf("CodeInterpreter", "FileOperations", "Search"),
                    avatarEmoji = "💻",
                    avatarColorHex = 0xFFA855F7, // PurpleAccent
                    temperature = 0.2f,
                    isAutonomous = true,
                    isEnabled = true,
                    createdAt = now,
                    updatedAt = now
                ),
                AgentEntity(
                    id = "agent_designer",
                    name = "Visual Creator",
                    role = "Generative Multimodal Designer",
                    description = "Creates visual concept art, UI diagrams, and rich multimodal design prompts with precise style control.",
                    systemPrompt = "You are Visual Creator. Craft rich visual aesthetics, generate AI artwork, and formulate UI asset design specifications.",
                    modelId = "gemini-3.5-flash",
                    providerId = "gemini",
                    tools = listOf("VisualGenerator", "FileOperations", "Search"),
                    avatarEmoji = "🎨",
                    avatarColorHex = 0xFFF59E0B, // AmberGold
                    temperature = 0.85f,
                    isAutonomous = true,
                    isEnabled = true,
                    createdAt = now,
                    updatedAt = now
                ),
                AgentEntity(
                    id = "agent_dispatcher",
                    name = "Omnichannel Dispatcher",
                    role = "Communications & Broadcast Officer",
                    description = "Relays mission-critical notifications, emergency broadcasts, and scheduled summaries across WhatsApp, Telegram, SMS, and Email.",
                    systemPrompt = "You are the Omnichannel Dispatcher. Format professional status summaries and dispatch multichannel broadcasts to configured recipients.",
                    modelId = "gemini-3.5-flash",
                    providerId = "gemini",
                    tools = listOf("ChannelDispatch", "TaskScheduler", "FileOperations", "Search"),
                    avatarEmoji = "📡",
                    avatarColorHex = 0xFF34D399, // EmeraldLight
                    temperature = 0.4f,
                    isAutonomous = true,
                    isEnabled = true,
                    createdAt = now,
                    updatedAt = now
                ),
                AgentEntity(
                    id = "agent_cron_master",
                    name = "Cron Task Master",
                    role = "Autonomous Workflow Automation Runner",
                    description = "Supervises background scheduling, cron jobs, routine health-checks, and periodic data synchronization.",
                    systemPrompt = "You are the Cron Task Master. Manage autonomous scheduling pipelines, monitor interval triggers, and automate routine workflows.",
                    modelId = "llama3.2",
                    providerId = "ollama",
                    tools = listOf("TaskScheduler", "CodeInterpreter", "ChannelDispatch", "FileOperations"),
                    avatarEmoji = "⏱️",
                    avatarColorHex = 0xFFEF4444, // RubyRed
                    temperature = 0.3f,
                    isAutonomous = true,
                    isEnabled = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }
}
