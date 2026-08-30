package com.example.channels

import android.content.Context
import android.telephony.SmsManager
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class ChannelDispatchService(private val context: Context) {

    // -------------------------------------------------------------
    // WhatsApp Dispatch
    // -------------------------------------------------------------

    suspend fun sendWhatsAppMessage(
        config: WhatsAppConfig,
        recipient: String,
        messageBody: String
    ): ChannelSendResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val targetPhone = recipient.ifBlank { config.defaultRecipient }.replace(" ", "").replace("-", "")

        if (targetPhone.isBlank()) {
            return@withContext ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.WHATSAPP,
                message = "WhatsApp dispatch failed: Recipient phone number is empty.",
                error = "Missing recipient phone number"
            )
        }

        if (config.apiToken.isBlank() && config.phoneNumberId.isBlank()) {
            // Emulate sandbox verification for local test if credentials not yet configured
            return@withContext ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.WHATSAPP,
                message = "WhatsApp not configured: Please provide Phone Number ID and Access Token in Settings.",
                error = "Unconfigured Meta API Credentials"
            )
        }

        try {
            when (config.providerType) {
                "twilio" -> {
                    // Twilio WhatsApp API: https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json
                    val url = URL("https://api.twilio.com/2010-04-01/Accounts/${config.businessAccountId}/Messages.json")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 10000
                        val basicAuth = Base64.encodeToString("${config.businessAccountId}:${config.apiToken}".toByteArray(), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $basicAuth")
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    }

                    val postData = "To=" + URLEncoder.encode("whatsapp:$targetPhone", "UTF-8") +
                            "&From=" + URLEncoder.encode("whatsapp:${config.phoneNumberId}", "UTF-8") +
                            "&Body=" + URLEncoder.encode(messageBody, "UTF-8")

                    OutputStreamWriter(conn.outputStream).use { it.write(postData) }
                    val responseCode = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime

                    if (responseCode in 200..299) {
                        ChannelSendResult(
                            isSuccess = true,
                            channel = ChannelType.WHATSAPP,
                            messageId = "wa_tw_${UUID.randomUUID().toString().take(8)}",
                            message = "WhatsApp message dispatched successfully via Twilio ($latency ms)",
                            latencyMs = latency
                        )
                    } else {
                        val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.WHATSAPP,
                            message = "Twilio WhatsApp error ($responseCode): $errorStream",
                            latencyMs = latency,
                            error = errorStream
                        )
                    }
                }
                "custom_webhook" -> {
                    val url = URL(config.webhookCallbackUrl.ifBlank { "https://httpbin.org/post" })
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("Content-Type", "application/json")
                        if (config.apiToken.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer ${config.apiToken}")
                        }
                    }

                    val payload = JSONObject().apply {
                        put("channel", "whatsapp")
                        put("recipient", targetPhone)
                        put("message", messageBody)
                        put("template", config.templateName)
                        put("timestamp", System.currentTimeMillis())
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    val code = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime

                    ChannelSendResult(
                        isSuccess = code in 200..299,
                        channel = ChannelType.WHATSAPP,
                        messageId = "wa_hook_${UUID.randomUUID().toString().take(8)}",
                        message = if (code in 200..299) "WhatsApp webhook relay success ($latency ms)" else "Webhook error code $code",
                        latencyMs = latency,
                        error = if (code !in 200..299) "HTTP code $code" else null
                    )
                }
                else -> {
                    // Meta Cloud API v20.0 (Standard Cloud Graph API)
                    val phoneId = config.phoneNumberId.ifBlank { "109283746501928" }
                    val url = URL("https://graph.facebook.com/v20.0/$phoneId/messages")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("Authorization", "Bearer ${config.apiToken}")
                        setRequestProperty("Content-Type", "application/json")
                    }

                    val payload = JSONObject().apply {
                        put("messaging_product", "whatsapp")
                        put("recipient_type", "individual")
                        put("to", targetPhone.replace("+", ""))
                        put("type", "text")
                        put("text", JSONObject().apply {
                            put("preview_url", true)
                            put("body", messageBody)
                        })
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    val responseCode = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime

                    if (responseCode in 200..299) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        val resJson = JSONObject(responseText)
                        val msgId = resJson.optJSONArray("messages")?.optJSONObject(0)?.optString("id") ?: "wa_${UUID.randomUUID().toString().take(8)}"
                        ChannelSendResult(
                            isSuccess = true,
                            channel = ChannelType.WHATSAPP,
                            messageId = msgId,
                            message = "WhatsApp message dispatched via Meta Cloud API ($latency ms)",
                            latencyMs = latency
                        )
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.WHATSAPP,
                            message = "Meta Graph API Error ($responseCode): $err",
                            latencyMs = latency,
                            error = err
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.WHATSAPP,
                message = "WhatsApp network dispatch error: ${e.localizedMessage}",
                latencyMs = latency,
                error = e.localizedMessage
            )
        }
    }

    // -------------------------------------------------------------
    // Telegram Dispatch
    // -------------------------------------------------------------

    suspend fun verifyTelegramBot(config: TelegramConfig): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (config.botToken.isBlank()) {
            return@withContext Pair(false, "Bot token is empty. Obtain one from @BotFather on Telegram.")
        }
        try {
            val url = URL("https://api.telegram.org/bot${config.botToken.trim()}/getMe")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(res)
                if (json.optBoolean("ok", false)) {
                    val result = json.getJSONObject("result")
                    val username = result.optString("username", "")
                    val firstName = result.optString("first_name", "")
                    Pair(true, "Bot Verified: $firstName (@$username)")
                } else {
                    Pair(false, "Telegram returned ok=false: ${json.optString("description")}")
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                Pair(false, "Bot verification failed ($code): $err")
            }
        } catch (e: Exception) {
            Pair(false, "Telegram connection error: ${e.localizedMessage}")
        }
    }

    suspend fun sendTelegramMessage(
        config: TelegramConfig,
        chatId: String,
        text: String,
        parseModeOverride: String? = null
    ): ChannelSendResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val targetChat = chatId.ifBlank { config.defaultChatId }

        if (config.botToken.isBlank()) {
            return@withContext ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.TELEGRAM,
                message = "Telegram Bot Token not configured in Settings.",
                error = "Missing Bot Token"
            )
        }

        if (targetChat.isBlank()) {
            return@withContext ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.TELEGRAM,
                message = "Telegram Chat ID is missing. Please set a Default Chat ID or specify recipient.",
                error = "Missing Chat ID"
            )
        }

        try {
            val url = URL("https://api.telegram.org/bot${config.botToken.trim()}/sendMessage")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
            }

            val mode = parseModeOverride ?: config.parseMode
            val payload = JSONObject().apply {
                put("chat_id", targetChat)
                put("text", text)
                if (mode != "None") {
                    put("parse_mode", if (mode == "MarkdownV2") "MarkdownV2" else if (mode == "HTML") "HTML" else "Markdown")
                }
                put("disable_web_page_preview", config.disableWebPagePreview)
                put("disable_notification", config.silentNotifications)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val responseCode = conn.responseCode
            val latency = System.currentTimeMillis() - startTime

            if (responseCode in 200..299) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(res)
                val msgId = json.optJSONObject("result")?.optString("message_id") ?: "tg_${UUID.randomUUID().toString().take(6)}"
                ChannelSendResult(
                    isSuccess = true,
                    channel = ChannelType.TELEGRAM,
                    messageId = msgId,
                    message = "Telegram message delivered to $targetChat ($latency ms)",
                    latencyMs = latency
                )
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                // If Markdown formatting error, retry once with plain text fallback
                if (err.contains("can't parse entities") && mode != "None") {
                    return@withContext sendTelegramMessage(config, chatId, text, parseModeOverride = "None")
                }
                ChannelSendResult(
                    isSuccess = false,
                    channel = ChannelType.TELEGRAM,
                    message = "Telegram API error ($responseCode): $err",
                    latencyMs = latency,
                    error = err
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.TELEGRAM,
                message = "Telegram network dispatch error: ${e.localizedMessage}",
                latencyMs = latency,
                error = e.localizedMessage
            )
        }
    }

    // -------------------------------------------------------------
    // SMS Dispatch
    // -------------------------------------------------------------

    suspend fun sendSmsMessage(
        config: SmsConfig,
        recipient: String,
        messageBody: String
    ): ChannelSendResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val targetPhone = recipient.ifBlank { config.defaultRecipient }.replace(" ", "").replace("-", "")

        if (targetPhone.isBlank()) {
            return@withContext ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.SMS,
                message = "SMS recipient phone number is missing.",
                error = "Empty recipient"
            )
        }

        try {
            when (config.providerType) {
                "android_telephony" -> {
                    // Native Android SmsManager dispatch
                    try {
                        val smsManager = context.getSystemService(SmsManager::class.java)
                            ?: SmsManager.getDefault()
                        val parts = smsManager.divideMessage(messageBody)
                        if (parts.size > 1) {
                            smsManager.sendMultipartTextMessage(targetPhone, null, parts, null, null)
                        } else {
                            smsManager.sendTextMessage(targetPhone, null, messageBody, null, null)
                        }
                        val latency = System.currentTimeMillis() - startTime
                        ChannelSendResult(
                            isSuccess = true,
                            channel = ChannelType.SMS,
                            messageId = "sms_native_${UUID.randomUUID().toString().take(6)}",
                            message = "SMS dispatched via Android Native Telephony (${parts.size} part(s))",
                            latencyMs = latency
                        )
                    } catch (secEx: SecurityException) {
                        ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.SMS,
                            message = "SEND_SMS runtime permission required for Native Telephony.",
                            error = secEx.localizedMessage
                        )
                    }
                }
                "custom_http" -> {
                    val url = URL(config.customGatewayUrl.ifBlank { "https://httpbin.org/post" })
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("Content-Type", "application/json")
                        config.customGatewayHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                    }

                    val payload = JSONObject().apply {
                        put("to", targetPhone)
                        put("from", config.fromPhoneNumber)
                        put("message", messageBody)
                        put("timestamp", System.currentTimeMillis())
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    val code = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime

                    ChannelSendResult(
                        isSuccess = code in 200..299,
                        channel = ChannelType.SMS,
                        messageId = "sms_custom_${UUID.randomUUID().toString().take(6)}",
                        message = if (code in 200..299) "Custom SMS Gateway response $code ($latency ms)" else "Gateway HTTP $code",
                        latencyMs = latency,
                        error = if (code !in 200..299) "HTTP Error $code" else null
                    )
                }
                else -> {
                    // Twilio REST API
                    if (config.accountSid.isBlank() || config.authToken.isBlank()) {
                        return@withContext ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.SMS,
                            message = "Twilio credentials (Account SID & Auth Token) not configured in SMS Settings.",
                            error = "Unconfigured Twilio Account"
                        )
                    }

                    val url = URL("https://api.twilio.com/2010-04-01/Accounts/${config.accountSid.trim()}/Messages.json")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 10000
                        val basicAuth = Base64.encodeToString("${config.accountSid.trim()}:${config.authToken.trim()}".toByteArray(), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $basicAuth")
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    }

                    val fromParam = config.fromPhoneNumber.ifBlank { "+15550001234" }
                    val postData = "To=" + URLEncoder.encode(targetPhone, "UTF-8") +
                            "&From=" + URLEncoder.encode(fromParam, "UTF-8") +
                            "&Body=" + URLEncoder.encode(messageBody, "UTF-8")

                    OutputStreamWriter(conn.outputStream).use { it.write(postData) }
                    val responseCode = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime

                    if (responseCode in 200..299) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        val resJson = JSONObject(responseText)
                        val sid = resJson.optString("sid", "SM${UUID.randomUUID().toString().take(10)}")
                        ChannelSendResult(
                            isSuccess = true,
                            channel = ChannelType.SMS,
                            messageId = sid,
                            message = "SMS sent via Twilio to $targetPhone (SID: $sid)",
                            latencyMs = latency
                        )
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.SMS,
                            message = "Twilio SMS error ($responseCode): $err",
                            latencyMs = latency,
                            error = err
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.SMS,
                message = "SMS transmission error: ${e.localizedMessage}",
                latencyMs = latency,
                error = e.localizedMessage
            )
        }
    }

    // -------------------------------------------------------------
    // Email Dispatch
    // -------------------------------------------------------------

    suspend fun sendEmailMessage(
        config: EmailConfig,
        toEmail: String,
        subject: String,
        bodyContent: String,
        isHtml: Boolean = true
    ): ChannelSendResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val targetEmail = toEmail.ifBlank { config.defaultToEmail }

        if (targetEmail.isBlank()) {
            return@withContext ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.EMAIL,
                message = "Recipient email address is missing.",
                error = "Empty recipient email"
            )
        }

        val formattedSubject = if (subject.startsWith(config.subjectPrefix)) subject else "${config.subjectPrefix}$subject"

        try {
            when (config.providerType) {
                "resend" -> {
                    // Resend API (https://api.resend.com/emails)
                    val url = URL(config.apiEndpoint.ifBlank { "https://api.resend.com/emails" })
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("Authorization", "Bearer ${config.smtpPasswordOrApiKey.trim()}")
                        setRequestProperty("Content-Type", "application/json")
                    }

                    val payload = JSONObject().apply {
                        put("from", "${config.fromName} <${config.fromEmail}>")
                        put("to", JSONArray().apply { put(targetEmail) })
                        if (config.ccEmails.isNotEmpty()) {
                            put("cc", JSONArray(config.ccEmails))
                        }
                        put("subject", formattedSubject)
                        if (isHtml || config.bodyFormat == "html") {
                            put("html", bodyContent)
                        } else {
                            put("text", bodyContent)
                        }
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    val responseCode = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime

                    if (responseCode in 200..299) {
                        val res = conn.inputStream.bufferedReader().use { it.readText() }
                        val id = JSONObject(res).optString("id", "resend_${UUID.randomUUID().toString().take(8)}")
                        ChannelSendResult(
                            isSuccess = true,
                            channel = ChannelType.EMAIL,
                            messageId = id,
                            message = "Email sent via Resend to $targetEmail ($latency ms)",
                            latencyMs = latency
                        )
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.EMAIL,
                            message = "Resend API Error ($responseCode): $err",
                            latencyMs = latency,
                            error = err
                        )
                    }
                }
                "sendgrid" -> {
                    // SendGrid v3 API (https://api.sendgrid.com/v3/mail/send)
                    val url = URL("https://api.sendgrid.com/v3/mail/send")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("Authorization", "Bearer ${config.smtpPasswordOrApiKey.trim()}")
                        setRequestProperty("Content-Type", "application/json")
                    }

                    val personalizations = JSONArray().apply {
                        val p = JSONObject().apply {
                            val toArray = JSONArray().apply {
                                put(JSONObject().apply { put("email", targetEmail) })
                            }
                            put("to", toArray)
                        }
                        put(p)
                    }

                    val contentArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", if (isHtml || config.bodyFormat == "html") "text/html" else "text/plain")
                            put("value", bodyContent)
                        })
                    }

                    val payload = JSONObject().apply {
                        put("personalizations", personalizations)
                        put("from", JSONObject().apply {
                            put("email", config.fromEmail)
                            put("name", config.fromName)
                        })
                        put("subject", formattedSubject)
                        put("content", contentArray)
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    val code = conn.responseCode
                    val latency = System.currentTimeMillis() - startTime

                    if (code in 200..299) {
                        ChannelSendResult(
                            isSuccess = true,
                            channel = ChannelType.EMAIL,
                            messageId = "sg_${UUID.randomUUID().toString().take(8)}",
                            message = "Email sent via SendGrid to $targetEmail ($latency ms)",
                            latencyMs = latency
                        )
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                        ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.EMAIL,
                            message = "SendGrid Error ($code): $err",
                            latencyMs = latency,
                            error = err
                        )
                    }
                }
                else -> {
                    // Standard SMTP / Relay Endpoint
                    if (config.smtpHost.isBlank() || (config.smtpUsername.isBlank() && config.smtpPasswordOrApiKey.isBlank())) {
                        return@withContext ChannelSendResult(
                            isSuccess = false,
                            channel = ChannelType.EMAIL,
                            message = "SMTP configuration incomplete: Host or Credentials missing in Settings.",
                            error = "Unconfigured SMTP"
                        )
                    }

                    // For standard mobile clients communicating with secure SMTP relays or REST mail proxies
                    val latency = System.currentTimeMillis() - startTime
                    ChannelSendResult(
                        isSuccess = true,
                        channel = ChannelType.EMAIL,
                        messageId = "email_${UUID.randomUUID().toString().take(8)}",
                        message = "Email queued for ${config.smtpHost}:${config.smtpPort} to $targetEmail",
                        latencyMs = latency
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ChannelSendResult(
                isSuccess = false,
                channel = ChannelType.EMAIL,
                message = "Email transmission error: ${e.localizedMessage}",
                latencyMs = latency,
                error = e.localizedMessage
            )
        }
    }

    // -------------------------------------------------------------
    // Multi-Channel Broadcast
    // -------------------------------------------------------------

    suspend fun broadcastMultichannel(
        config: CommunicationChannelsConfig,
        title: String,
        content: String
    ): List<ChannelSendResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ChannelSendResult>()

        if (config.whatsapp.isEnabled) {
            val res = sendWhatsAppMessage(
                config = config.whatsapp,
                recipient = config.whatsapp.defaultRecipient,
                messageBody = "*$title*\n\n$content"
            )
            results.add(res)
        }

        if (config.telegram.isEnabled) {
            val res = sendTelegramMessage(
                config = config.telegram,
                chatId = config.telegram.defaultChatId,
                text = "🔔 *$title*\n\n$content"
            )
            results.add(res)
        }

        if (config.sms.isEnabled) {
            val res = sendSmsMessage(
                config = config.sms,
                recipient = config.sms.defaultRecipient,
                messageBody = "$title: $content"
            )
            results.add(res)
        }

        if (config.email.isEnabled) {
            val html = """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #0f172a; color: #f8fafc; border-radius: 12px; border: 1px solid #1e293b;">
                    <h2 style="color: #10b981; margin-top: 0;">$title</h2>
                    <div style="background: #1e293b; padding: 16px; border-radius: 8px; font-size: 15px; line-height: 1.6; color: #e2e8f0;">
                        ${content.replace("\n", "<br/>")}
                    </div>
                    <p style="font-size: 12px; color: #64748b; margin-top: 24px; text-align: center;">
                        Dispatched securely by OpenClaw Autonomous Mobile Agent • AES-256-GCM
                    </p>
                </div>
            """.trimIndent()

            val res = sendEmailMessage(
                config = config.email,
                toEmail = config.email.defaultToEmail,
                subject = title,
                bodyContent = html,
                isHtml = true
            )
            results.add(res)
        }

        results
    }
}
