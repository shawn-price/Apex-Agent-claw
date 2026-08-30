package com.example.agent

import com.example.channels.ChannelDispatchService
import com.example.channels.ChannelTransmissionLog
import com.example.channels.ChannelType
import com.example.storage.EncryptedStorageManager
import com.example.storage.ScheduledTask
import com.example.storage.ToolCall
import org.json.JSONObject
import java.util.UUID

class AgentTools(
    private val storageManager: EncryptedStorageManager,
    private val webSearchEngine: WebSearchEngine,
    private val codeExecutionEngine: CodeExecutionEngine,
    private val channelDispatchService: ChannelDispatchService
) {

    suspend fun executeTool(toolCall: ToolCall): Pair<String, Boolean> {
        val args = try {
            JSONObject(toolCall.arguments)
        } catch (e: Exception) {
            JSONObject()
        }

        return when (toolCall.name) {
            "web_search" -> {
                val query = args.optString("query", "")
                if (query.isBlank()) {
                    Pair("Error: missing 'query' argument", true)
                } else {
                    val results = webSearchEngine.search(query)
                    val text = buildString {
                        appendLine("Web Search Results for: \"$query\"")
                        results.forEachIndexed { i, r ->
                            appendLine("${i + 1}. ${r.title}")
                            appendLine("   Snippet: ${r.snippet}")
                            appendLine("   Source: ${r.url}")
                        }
                    }
                    Pair(text, false)
                }
            }

            "file_read" -> {
                val path = args.optString("path", "")
                val content = storageManager.readWorkspaceFile(path)
                if (content != null) {
                    Pair(content, false)
                } else {
                    Pair("File not found: $path in /nanobot/workspace/", true)
                }
            }

            "file_write" -> {
                val path = args.optString("path", "")
                val content = args.optString("content", "")
                if (path.isBlank()) {
                    Pair("Error: 'path' cannot be empty", true)
                } else {
                    storageManager.writeWorkspaceFile(path, content)
                    Pair("Successfully wrote ${content.length} characters to workspace/$path", false)
                }
            }

            "file_list" -> {
                val files = storageManager.listWorkspaceFiles()
                val text = buildString {
                    appendLine("Files in /nanobot/workspace/ (${files.size} items):")
                    files.forEach { f ->
                        val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                        appendLine("• $type ${f.relativePath} (${f.sizeBytes} bytes)")
                    }
                }
                Pair(text, false)
            }

            "file_delete" -> {
                val path = args.optString("path", "")
                val success = storageManager.deleteWorkspaceFile(path)
                if (success) {
                    Pair("Deleted workspace/$path", false)
                } else {
                    Pair("File not found or deletion failed: $path", true)
                }
            }

            "code_execution", "execute_code" -> {
                val code = args.optString("code", "")
                val lang = args.optString("language", "python")
                val res = codeExecutionEngine.execute(code, lang)
                if (res.isSuccess) {
                    val out = buildString {
                        appendLine("[Engine: ${res.language} | Execution: ${res.executionTimeMs}ms]")
                        appendLine(res.output)
                        if (res.returnValue != null) {
                            appendLine("Variables: ${res.returnValue}")
                        }
                    }
                    Pair(out, false)
                } else {
                    Pair(res.error ?: "Execution failed", true)
                }
            }

            "schedule_task" -> {
                val title = args.optString("title", "Agent Task")
                val prompt = args.optString("prompt", "")
                val intervalMin = args.optInt("intervalMinutes", 60)
                val isRecurring = args.optBoolean("isRecurring", false)

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
                Pair("Scheduled task '${task.title}' successfully. Next execution in $intervalMin minutes.", false)
            }

            // ---------------------------------------------------------
            // Communication Channel Agent Tools
            // ---------------------------------------------------------

            "send_whatsapp", "send_whatsapp_message" -> {
                val recipient = args.optString("recipient", args.optString("phone", ""))
                val message = args.optString("message", args.optString("text", ""))
                val channelsConfig = storageManager.getChannelsConfig()
                val result = channelDispatchService.sendWhatsAppMessage(
                    config = channelsConfig.whatsapp,
                    recipient = recipient,
                    messageBody = message
                )
                storageManager.addTransmissionLog(
                    ChannelTransmissionLog(
                        id = UUID.randomUUID().toString(),
                        channel = ChannelType.WHATSAPP,
                        recipient = recipient.ifBlank { channelsConfig.whatsapp.defaultRecipient },
                        summary = message.take(80),
                        status = if (result.isSuccess) "SENT" else "FAILED",
                        latencyMs = result.latencyMs,
                        errorDetails = result.error
                    )
                )
                Pair(result.message, !result.isSuccess)
            }

            "send_telegram", "send_telegram_message" -> {
                val chatId = args.optString("chat_id", args.optString("chatId", ""))
                val message = args.optString("message", args.optString("text", ""))
                val channelsConfig = storageManager.getChannelsConfig()
                val result = channelDispatchService.sendTelegramMessage(
                    config = channelsConfig.telegram,
                    chatId = chatId,
                    text = message
                )
                storageManager.addTransmissionLog(
                    ChannelTransmissionLog(
                        id = UUID.randomUUID().toString(),
                        channel = ChannelType.TELEGRAM,
                        recipient = chatId.ifBlank { channelsConfig.telegram.defaultChatId },
                        summary = message.take(80),
                        status = if (result.isSuccess) "SENT" else "FAILED",
                        latencyMs = result.latencyMs,
                        errorDetails = result.error
                    )
                )
                Pair(result.message, !result.isSuccess)
            }

            "send_sms", "send_sms_message" -> {
                val recipient = args.optString("recipient", args.optString("phone", ""))
                val message = args.optString("message", args.optString("text", ""))
                val channelsConfig = storageManager.getChannelsConfig()
                val result = channelDispatchService.sendSmsMessage(
                    config = channelsConfig.sms,
                    recipient = recipient,
                    messageBody = message
                )
                storageManager.addTransmissionLog(
                    ChannelTransmissionLog(
                        id = UUID.randomUUID().toString(),
                        channel = ChannelType.SMS,
                        recipient = recipient.ifBlank { channelsConfig.sms.defaultRecipient },
                        summary = message.take(80),
                        status = if (result.isSuccess) "SENT" else "FAILED",
                        latencyMs = result.latencyMs,
                        errorDetails = result.error
                    )
                )
                Pair(result.message, !result.isSuccess)
            }

            "send_email", "send_email_message" -> {
                val to = args.optString("to", args.optString("email", ""))
                val subject = args.optString("subject", "OpenClaw Autonomous Notification")
                val body = args.optString("body", args.optString("message", ""))
                val isHtml = args.optBoolean("is_html", true)
                val channelsConfig = storageManager.getChannelsConfig()
                val result = channelDispatchService.sendEmailMessage(
                    config = channelsConfig.email,
                    toEmail = to,
                    subject = subject,
                    bodyContent = body,
                    isHtml = isHtml
                )
                storageManager.addTransmissionLog(
                    ChannelTransmissionLog(
                        id = UUID.randomUUID().toString(),
                        channel = ChannelType.EMAIL,
                        recipient = to.ifBlank { channelsConfig.email.defaultToEmail },
                        summary = "$subject: ${body.take(60)}",
                        status = if (result.isSuccess) "SENT" else "FAILED",
                        latencyMs = result.latencyMs,
                        errorDetails = result.error
                    )
                )
                Pair(result.message, !result.isSuccess)
            }

            "broadcast_alert", "broadcast_multichannel" -> {
                val title = args.optString("title", "OpenClaw Multi-Channel Alert")
                val message = args.optString("message", args.optString("content", ""))
                val channelsConfig = storageManager.getChannelsConfig()
                val results = channelDispatchService.broadcastMultichannel(
                    config = channelsConfig,
                    title = title,
                    content = message
                )
                val summary = buildString {
                    appendLine("Multi-Channel Broadcast Result (${results.size} dispatched):")
                    results.forEach { r ->
                        val status = if (r.isSuccess) "✅ SUCCESS" else "❌ FAILED"
                        appendLine("• ${r.channel.displayName}: $status - ${r.message}")
                    }
                }
                Pair(summary, results.none { it.isSuccess })
            }

            "get_storage_stats" -> {
                val convs = storageManager.getConversations()
                val tasks = storageManager.getTasks()
                val allFiles = storageManager.listAllInternalFiles()
                val totalEncryptedSize = allFiles.filter { it.isEncrypted }.sumOf { it.sizeBytes }
                val channels = storageManager.getChannelsConfig()
                val stats = """
Storage & Channel Stats:
• Zero-DB Architecture: 100% Encrypted JSON (AES-256-GCM)
• Total Encrypted Size: ${totalEncryptedSize / 1024} KB
• Active Conversations: ${convs.size}
• Active Tasks: ${tasks.size}
• Communication Channels Configured:
  - WhatsApp: ${if (channels.whatsapp.isEnabled) "ACTIVE" else "DISABLED"} (${channels.whatsapp.providerType})
  - Telegram: ${if (channels.telegram.isEnabled) "ACTIVE" else "DISABLED"}
  - SMS: ${if (channels.sms.isEnabled) "ACTIVE" else "DISABLED"} (${channels.sms.providerType})
  - Email: ${if (channels.email.isEnabled) "ACTIVE" else "DISABLED"} (${channels.email.providerType})
• KeyStore Alias: openclaw_master_key_v1 (Hardware Keystore)
                """.trimIndent()
                Pair(stats, false)
            }

            else -> {
                Pair("Unknown tool: ${toolCall.name}", true)
            }
        }
    }
}
