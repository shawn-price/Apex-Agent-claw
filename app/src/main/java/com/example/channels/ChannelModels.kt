package com.example.channels

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChannelType(val displayName: String, val id: String) {
    WHATSAPP("WhatsApp", "whatsapp"),
    TELEGRAM("Telegram", "telegram"),
    SMS("SMS", "sms"),
    EMAIL("Email", "email")
}

data class WhatsAppConfig(
    val isEnabled: Boolean = false,
    val providerType: String = "meta_cloud_api", // meta_cloud_api, twilio, custom_webhook
    val phoneNumberId: String = "",
    val businessAccountId: String = "",
    val apiToken: String = "",
    val webhookVerificationToken: String = "",
    val webhookCallbackUrl: String = "",
    val defaultRecipient: String = "",
    val templateName: String = "agent_notification",
    val languageCode: String = "en_US",
    val autoReplyEnabled: Boolean = false,
    val autoReplyPrompt: String = "You are an autonomous OpenClaw AI assistant responding via WhatsApp. Keep responses concise and formatted for mobile messaging.",
    val lastPingLatencyMs: Long = -1L,
    val status: String = "Not Configured"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("providerType", providerType)
        put("phoneNumberId", phoneNumberId)
        put("businessAccountId", businessAccountId)
        put("apiToken", apiToken)
        put("webhookVerificationToken", webhookVerificationToken)
        put("webhookCallbackUrl", webhookCallbackUrl)
        put("defaultRecipient", defaultRecipient)
        put("templateName", templateName)
        put("languageCode", languageCode)
        put("autoReplyEnabled", autoReplyEnabled)
        put("autoReplyPrompt", autoReplyPrompt)
        put("lastPingLatencyMs", lastPingLatencyMs)
        put("status", status)
    }

    companion object {
        fun fromJson(json: JSONObject): WhatsAppConfig = WhatsAppConfig(
            isEnabled = json.optBoolean("isEnabled", false),
            providerType = json.optString("providerType", "meta_cloud_api"),
            phoneNumberId = json.optString("phoneNumberId", ""),
            businessAccountId = json.optString("businessAccountId", ""),
            apiToken = json.optString("apiToken", ""),
            webhookVerificationToken = json.optString("webhookVerificationToken", ""),
            webhookCallbackUrl = json.optString("webhookCallbackUrl", ""),
            defaultRecipient = json.optString("defaultRecipient", ""),
            templateName = json.optString("templateName", "agent_notification"),
            languageCode = json.optString("languageCode", "en_US"),
            autoReplyEnabled = json.optBoolean("autoReplyEnabled", false),
            autoReplyPrompt = json.optString(
                "autoReplyPrompt",
                "You are an autonomous OpenClaw AI assistant responding via WhatsApp. Keep responses concise and formatted for mobile messaging."
            ),
            lastPingLatencyMs = json.optLong("lastPingLatencyMs", -1L),
            status = json.optString("status", "Not Configured")
        )
    }
}

data class TelegramConfig(
    val isEnabled: Boolean = false,
    val botToken: String = "",
    val botUsername: String = "",
    val defaultChatId: String = "",
    val allowedUserIds: List<String> = emptyList(),
    val parseMode: String = "MarkdownV2", // MarkdownV2, HTML, Markdown, None
    val pollingMode: String = "long_polling", // long_polling, webhook
    val webhookUrl: String = "",
    val webhookSecretToken: String = "",
    val disableWebPagePreview: Boolean = false,
    val silentNotifications: Boolean = false,
    val autoReplyEnabled: Boolean = false,
    val autoReplyPrompt: String = "You are an autonomous OpenClaw AI agent interacting over Telegram. Format code blocks cleanly and support commands.",
    val lastPingLatencyMs: Long = -1L,
    val status: String = "Not Configured"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("botToken", botToken)
        put("botUsername", botUsername)
        put("defaultChatId", defaultChatId)
        put("allowedUserIds", JSONArray(allowedUserIds))
        put("parseMode", parseMode)
        put("pollingMode", pollingMode)
        put("webhookUrl", webhookUrl)
        put("webhookSecretToken", webhookSecretToken)
        put("disableWebPagePreview", disableWebPagePreview)
        put("silentNotifications", silentNotifications)
        put("autoReplyEnabled", autoReplyEnabled)
        put("autoReplyPrompt", autoReplyPrompt)
        put("lastPingLatencyMs", lastPingLatencyMs)
        put("status", status)
    }

    companion object {
        fun fromJson(json: JSONObject): TelegramConfig {
            val users = mutableListOf<String>()
            val uArr = json.optJSONArray("allowedUserIds")
            if (uArr != null) {
                for (i in 0 until uArr.length()) users.add(uArr.getString(i))
            }
            return TelegramConfig(
                isEnabled = json.optBoolean("isEnabled", false),
                botToken = json.optString("botToken", ""),
                botUsername = json.optString("botUsername", ""),
                defaultChatId = json.optString("defaultChatId", ""),
                allowedUserIds = users,
                parseMode = json.optString("parseMode", "MarkdownV2"),
                pollingMode = json.optString("pollingMode", "long_polling"),
                webhookUrl = json.optString("webhookUrl", ""),
                webhookSecretToken = json.optString("webhookSecretToken", ""),
                disableWebPagePreview = json.optBoolean("disableWebPagePreview", false),
                silentNotifications = json.optBoolean("silentNotifications", false),
                autoReplyEnabled = json.optBoolean("autoReplyEnabled", false),
                autoReplyPrompt = json.optString(
                    "autoReplyPrompt",
                    "You are an autonomous OpenClaw AI agent interacting over Telegram. Format code blocks cleanly and support commands."
                ),
                lastPingLatencyMs = json.optLong("lastPingLatencyMs", -1L),
                status = json.optString("status", "Not Configured")
            )
        }
    }
}

data class SmsConfig(
    val isEnabled: Boolean = false,
    val providerType: String = "twilio", // twilio, android_telephony, vonage, custom_http
    val accountSid: String = "",
    val authToken: String = "",
    val fromPhoneNumber: String = "",
    val defaultRecipient: String = "",
    val customGatewayUrl: String = "",
    val customGatewayHeaders: Map<String, String> = emptyMap(),
    val maxPartsPerMessage: Int = 3,
    val enableDeliveryReports: Boolean = true,
    val status: String = "Not Configured",
    val lastPingLatencyMs: Long = -1L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("providerType", providerType)
        put("accountSid", accountSid)
        put("authToken", authToken)
        put("fromPhoneNumber", fromPhoneNumber)
        put("defaultRecipient", defaultRecipient)
        put("customGatewayUrl", customGatewayUrl)
        val hObj = JSONObject()
        customGatewayHeaders.forEach { (k, v) -> hObj.put(k, v) }
        put("customGatewayHeaders", hObj)
        put("maxPartsPerMessage", maxPartsPerMessage)
        put("enableDeliveryReports", enableDeliveryReports)
        put("status", status)
        put("lastPingLatencyMs", lastPingLatencyMs)
    }

    companion object {
        fun fromJson(json: JSONObject): SmsConfig {
            val headers = mutableMapOf<String, String>()
            val hObj = json.optJSONObject("customGatewayHeaders")
            if (hObj != null) {
                hObj.keys().forEach { k -> headers[k] = hObj.getString(k) }
            }
            return SmsConfig(
                isEnabled = json.optBoolean("isEnabled", false),
                providerType = json.optString("providerType", "twilio"),
                accountSid = json.optString("accountSid", ""),
                authToken = json.optString("authToken", ""),
                fromPhoneNumber = json.optString("fromPhoneNumber", ""),
                defaultRecipient = json.optString("defaultRecipient", ""),
                customGatewayUrl = json.optString("customGatewayUrl", ""),
                customGatewayHeaders = headers,
                maxPartsPerMessage = json.optInt("maxPartsPerMessage", 3),
                enableDeliveryReports = json.optBoolean("enableDeliveryReports", true),
                status = json.optString("status", "Not Configured"),
                lastPingLatencyMs = json.optLong("lastPingLatencyMs", -1L)
            )
        }
    }
}

data class EmailConfig(
    val isEnabled: Boolean = false,
    val providerType: String = "smtp", // smtp, resend, sendgrid, mailgun, aws_ses
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 587,
    val useTls: Boolean = true,
    val useSsl: Boolean = false,
    val smtpUsername: String = "",
    val smtpPasswordOrApiKey: String = "",
    val fromEmail: String = "agent@openclaw.ai",
    val fromName: String = "OpenClaw Autonomous Agent",
    val defaultToEmail: String = "",
    val ccEmails: List<String> = emptyList(),
    val subjectPrefix: String = "[OpenClaw AI] ",
    val bodyFormat: String = "html", // html, plain_text
    val apiEndpoint: String = "https://api.resend.com/emails",
    val status: String = "Not Configured",
    val lastPingLatencyMs: Long = -1L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("providerType", providerType)
        put("smtpHost", smtpHost)
        put("smtpPort", smtpPort)
        put("useTls", useTls)
        put("useSsl", useSsl)
        put("smtpUsername", smtpUsername)
        put("smtpPasswordOrApiKey", smtpPasswordOrApiKey)
        put("fromEmail", fromEmail)
        put("fromName", fromName)
        put("defaultToEmail", defaultToEmail)
        put("ccEmails", JSONArray(ccEmails))
        put("subjectPrefix", subjectPrefix)
        put("bodyFormat", bodyFormat)
        put("apiEndpoint", apiEndpoint)
        put("status", status)
        put("lastPingLatencyMs", lastPingLatencyMs)
    }

    companion object {
        fun fromJson(json: JSONObject): EmailConfig {
            val ccs = mutableListOf<String>()
            val arr = json.optJSONArray("ccEmails")
            if (arr != null) {
                for (i in 0 until arr.length()) ccs.add(arr.getString(i))
            }
            return EmailConfig(
                isEnabled = json.optBoolean("isEnabled", false),
                providerType = json.optString("providerType", "smtp"),
                smtpHost = json.optString("smtpHost", "smtp.gmail.com"),
                smtpPort = json.optInt("smtpPort", 587),
                useTls = json.optBoolean("useTls", true),
                useSsl = json.optBoolean("useSsl", false),
                smtpUsername = json.optString("smtpUsername", ""),
                smtpPasswordOrApiKey = json.optString("smtpPasswordOrApiKey", ""),
                fromEmail = json.optString("fromEmail", "agent@openclaw.ai"),
                fromName = json.optString("fromName", "OpenClaw Autonomous Agent"),
                defaultToEmail = json.optString("defaultToEmail", ""),
                ccEmails = ccs,
                subjectPrefix = json.optString("subjectPrefix", "[OpenClaw AI] "),
                bodyFormat = json.optString("bodyFormat", "html"),
                apiEndpoint = json.optString("apiEndpoint", "https://api.resend.com/emails"),
                status = json.optString("status", "Not Configured"),
                lastPingLatencyMs = json.optLong("lastPingLatencyMs", -1L)
            )
        }
    }
}

data class CommunicationChannelsConfig(
    val whatsapp: WhatsAppConfig = WhatsAppConfig(),
    val telegram: TelegramConfig = TelegramConfig(),
    val sms: SmsConfig = SmsConfig(),
    val email: EmailConfig = EmailConfig(),
    val multiChannelBroadcastEnabled: Boolean = true,
    val alertSeverityThreshold: String = "all", // all, warning, critical
    val rateLimitPerHour: Int = 120
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("whatsapp", whatsapp.toJson())
        put("telegram", telegram.toJson())
        put("sms", sms.toJson())
        put("email", email.toJson())
        put("multiChannelBroadcastEnabled", multiChannelBroadcastEnabled)
        put("alertSeverityThreshold", alertSeverityThreshold)
        put("rateLimitPerHour", rateLimitPerHour)
    }

    companion object {
        fun fromJson(json: JSONObject): CommunicationChannelsConfig = CommunicationChannelsConfig(
            whatsapp = json.optJSONObject("whatsapp")?.let { WhatsAppConfig.fromJson(it) } ?: WhatsAppConfig(),
            telegram = json.optJSONObject("telegram")?.let { TelegramConfig.fromJson(it) } ?: TelegramConfig(),
            sms = json.optJSONObject("sms")?.let { SmsConfig.fromJson(it) } ?: SmsConfig(),
            email = json.optJSONObject("email")?.let { EmailConfig.fromJson(it) } ?: EmailConfig(),
            multiChannelBroadcastEnabled = json.optBoolean("multiChannelBroadcastEnabled", true),
            alertSeverityThreshold = json.optString("alertSeverityThreshold", "all"),
            rateLimitPerHour = json.optInt("rateLimitPerHour", 120)
        )
    }
}

data class ChannelTransmissionLog(
    val id: String,
    val channel: ChannelType,
    val recipient: String,
    val summary: String,
    val status: String, // "SENT", "FAILED", "QUEUED"
    val timestamp: Long = System.currentTimeMillis(),
    val latencyMs: Long = 0L,
    val errorDetails: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("channel", channel.id)
        put("recipient", recipient)
        put("summary", summary)
        put("status", status)
        put("timestamp", timestamp)
        put("latencyMs", latencyMs)
        if (errorDetails != null) put("errorDetails", errorDetails)
    }

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    companion object {
        fun fromJson(json: JSONObject): ChannelTransmissionLog = ChannelTransmissionLog(
            id = json.getString("id"),
            channel = when (json.optString("channel", "telegram")) {
                "whatsapp" -> ChannelType.WHATSAPP
                "sms" -> ChannelType.SMS
                "email" -> ChannelType.EMAIL
                else -> ChannelType.TELEGRAM
            },
            recipient = json.optString("recipient", ""),
            summary = json.optString("summary", ""),
            status = json.optString("status", "SENT"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            latencyMs = json.optLong("latencyMs", 0L),
            errorDetails = if (json.has("errorDetails")) json.getString("errorDetails") else null
        )
    }
}

data class ChannelSendResult(
    val isSuccess: Boolean,
    val channel: ChannelType,
    val messageId: String? = null,
    val message: String,
    val latencyMs: Long = 0L,
    val error: String? = null
)
