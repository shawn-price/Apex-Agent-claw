package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.channels.*
import com.example.ui.theme.*

// -------------------------------------------------------------
// WhatsApp Configuration Dialog
// -------------------------------------------------------------
@Composable
fun WhatsAppConfigDialog(
    config: WhatsAppConfig,
    onDismiss: () -> Unit,
    onSave: (WhatsAppConfig) -> Unit,
    onTest: (recipient: String, message: String) -> Unit
) {
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    var providerType by remember { mutableStateOf(config.providerType) }
    var phoneNumberId by remember { mutableStateOf(config.phoneNumberId) }
    var businessAccountId by remember { mutableStateOf(config.businessAccountId) }
    var apiToken by remember { mutableStateOf(config.apiToken) }
    var defaultRecipient by remember { mutableStateOf(config.defaultRecipient) }
    var webhookCallbackUrl by remember { mutableStateOf(config.webhookCallbackUrl) }
    var webhookVerificationToken by remember { mutableStateOf(config.webhookVerificationToken) }
    var templateName by remember { mutableStateOf(config.templateName) }
    var languageCode by remember { mutableStateOf(config.languageCode) }
    var autoReplyEnabled by remember { mutableStateOf(config.autoReplyEnabled) }
    var autoReplyPrompt by remember { mutableStateOf(config.autoReplyPrompt) }

    var showToken by remember { mutableStateOf(false) }
    var testRecipient by remember { mutableStateOf(config.defaultRecipient) }
    var testMessage by remember { mutableStateOf("Hello from OpenClaw Autonomous Agent on WhatsApp!") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubble, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WhatsApp Advanced Settings",
                            color = SlateTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateTextSecondary)
                    }
                }

                Text(
                    text = "Configure Meta Cloud API, Twilio WhatsApp, or custom webhook relays.",
                    color = SlateTextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                Divider(color = ObsidianBorder)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Enable switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable WhatsApp Channel", color = SlateTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Allow agent to send automated alerts via WhatsApp", color = SlateTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = WhatsAppGreen)
                        )
                    }

                    // Provider Type Selector
                    Text("PROVIDER BACKEND", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderChip("meta_cloud_api", "Meta Cloud API", providerType == "meta_cloud_api", WhatsAppGreen) { providerType = "meta_cloud_api" }
                        ProviderChip("twilio", "Twilio API", providerType == "twilio", WhatsAppGreen) { providerType = "twilio" }
                        ProviderChip("custom_webhook", "Custom Relay", providerType == "custom_webhook", WhatsAppGreen) { providerType = "custom_webhook" }
                    }

                    // Credentials
                    Text("API CREDENTIALS (AES-256 ENCRYPTED)", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    OutlinedTextField(
                        value = phoneNumberId,
                        onValueChange = { phoneNumberId = it },
                        label = { Text("Phone Number ID / Twilio SID") },
                        placeholder = { Text("e.g. 104829482910482") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("wa_phone_id_input")
                    )

                    OutlinedTextField(
                        value = businessAccountId,
                        onValueChange = { businessAccountId = it },
                        label = { Text("WhatsApp Business Account (WABA) ID") },
                        placeholder = { Text("e.g. 948291048291048") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("wa_waba_id_input")
                    )

                    OutlinedTextField(
                        value = apiToken,
                        onValueChange = { apiToken = it },
                        label = { Text("Permanent Access Token / Auth Token") },
                        placeholder = { Text("EAAG...") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle token visibility",
                                    tint = SlateTextTertiary
                                )
                            }
                        },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("wa_token_input")
                    )

                    // Routing Defaults
                    Text("DISPATCH & ROUTING DEFAULTS", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    OutlinedTextField(
                        value = defaultRecipient,
                        onValueChange = { defaultRecipient = it },
                        label = { Text("Default Recipient Phone Number (E.164)") },
                        placeholder = { Text("+1234567890 or +447...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("wa_default_recipient_input")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            label = { Text("Template Name") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = languageCode,
                            onValueChange = { languageCode = it },
                            label = { Text("Language Code") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.weight(0.6f)
                        )
                    }

                    // Inbound Webhooks & Auto-Reply
                    Text("WEBHOOKS & AUTONOMOUS INBOUND HANDLING", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    OutlinedTextField(
                        value = webhookCallbackUrl,
                        onValueChange = { webhookCallbackUrl = it },
                        label = { Text("Webhook Callback URL") },
                        placeholder = { Text("https://your-domain.com/api/wa/webhook") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = webhookVerificationToken,
                        onValueChange = { webhookVerificationToken = it },
                        label = { Text("Webhook Verification Token") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inbound Auto-Reply Agent", color = SlateTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("OpenClaw LLM processes incoming WhatsApp messages autonomously", color = SlateTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = autoReplyEnabled,
                            onCheckedChange = { autoReplyEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = WhatsAppGreen)
                        )
                    }

                    if (autoReplyEnabled) {
                        OutlinedTextField(
                            value = autoReplyPrompt,
                            onValueChange = { autoReplyPrompt = it },
                            label = { Text("Custom Inbound Agent System Prompt") },
                            minLines = 2,
                            maxLines = 4,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live Test Dispatch Section
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("LIVE TEST DISPATCH", color = WhatsAppGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = testRecipient,
                                onValueChange = { testRecipient = it },
                                label = { Text("Test Recipient") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = testMessage,
                                onValueChange = { testMessage = it },
                                label = { Text("Test Message") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onTest(testRecipient, testMessage) },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.End).testTag("wa_send_test_btn")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Test Message", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Divider(color = ObsidianBorder)

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Button(
                        onClick = {
                            onSave(
                                config.copy(
                                    isEnabled = isEnabled,
                                    providerType = providerType,
                                    phoneNumberId = phoneNumberId.trim(),
                                    businessAccountId = businessAccountId.trim(),
                                    apiToken = apiToken.trim(),
                                    defaultRecipient = defaultRecipient.trim(),
                                    webhookCallbackUrl = webhookCallbackUrl.trim(),
                                    webhookVerificationToken = webhookVerificationToken.trim(),
                                    templateName = templateName.trim(),
                                    languageCode = languageCode.trim(),
                                    autoReplyEnabled = autoReplyEnabled,
                                    autoReplyPrompt = autoReplyPrompt.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        modifier = Modifier.testTag("wa_save_config_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Telegram Configuration Dialog
// -------------------------------------------------------------
@Composable
fun TelegramConfigDialog(
    config: TelegramConfig,
    onDismiss: () -> Unit,
    onSave: (TelegramConfig) -> Unit,
    onVerify: () -> Unit,
    onTest: (chatId: String, message: String) -> Unit
) {
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    var botToken by remember { mutableStateOf(config.botToken) }
    var botUsername by remember { mutableStateOf(config.botUsername) }
    var defaultChatId by remember { mutableStateOf(config.defaultChatId) }
    var allowedUserIdsText by remember { mutableStateOf(config.allowedUserIds.joinToString(", ")) }
    var parseMode by remember { mutableStateOf(config.parseMode) }
    var pollingMode by remember { mutableStateOf(config.pollingMode) }
    var disableWebPagePreview by remember { mutableStateOf(config.disableWebPagePreview) }
    var silentNotifications by remember { mutableStateOf(config.silentNotifications) }
    var webhookUrl by remember { mutableStateOf(config.webhookUrl) }
    var webhookSecretToken by remember { mutableStateOf(config.webhookSecretToken) }
    var autoReplyEnabled by remember { mutableStateOf(config.autoReplyEnabled) }
    var autoReplyPrompt by remember { mutableStateOf(config.autoReplyPrompt) }

    var showToken by remember { mutableStateOf(false) }
    var testChatId by remember { mutableStateOf(config.defaultChatId) }
    var testMessage by remember { mutableStateOf("🚀 *OpenClaw Agent Alert*\n\nAutonomous agent is online and operational!") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, TelegramSkyBlue.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = TelegramSkyBlue, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Telegram Bot Advanced Settings",
                            color = SlateTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateTextSecondary)
                    }
                }

                Text(
                    text = "Configure official Telegram Bot API, Chat IDs, MarkdownV2 rendering, and Webhooks.",
                    color = SlateTextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                Divider(color = ObsidianBorder)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Enable switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Telegram Channel", color = SlateTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Receive alerts and autonomous agent dispatches on Telegram", color = SlateTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = TelegramSkyBlue)
                        )
                    }

                    // Bot Credentials
                    Text("TELEGRAM BOT CREDENTIALS", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        label = { Text("Bot API Token (from @BotFather)") },
                        placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRstuVWXyz") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle token visibility",
                                    tint = SlateTextTertiary
                                )
                            }
                        },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("tg_token_input")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = botUsername,
                            onValueChange = { botUsername = it },
                            label = { Text("Bot Username") },
                            placeholder = { Text("@OpenClawBot") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalButton(
                            onClick = onVerify,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = TelegramSkyBlue.copy(alpha = 0.2f), contentColor = TelegramSkyBlue),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verify Bot", fontSize = 11.sp)
                        }
                    }

                    // Default Chat ID & Security
                    Text("CHAT TARGETS & SECURITY WHITELIST", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    OutlinedTextField(
                        value = defaultChatId,
                        onValueChange = { defaultChatId = it },
                        label = { Text("Default Chat ID / Channel ID") },
                        placeholder = { Text("e.g. 123456789 or -1001234567890") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("tg_chat_id_input")
                    )

                    OutlinedTextField(
                        value = allowedUserIdsText,
                        onValueChange = { allowedUserIdsText = it },
                        label = { Text("Allowed User IDs Whitelist (comma-separated)") },
                        placeholder = { Text("e.g. 12345678, 98765432 (or * for all)") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Formatting & Parse Mode
                    Text("RENDERING & NOTIFICATION BEHAVIOR", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderChip("MarkdownV2", "MarkdownV2", parseMode == "MarkdownV2", TelegramSkyBlue) { parseMode = "MarkdownV2" }
                        ProviderChip("HTML", "HTML", parseMode == "HTML", TelegramSkyBlue) { parseMode = "HTML" }
                        ProviderChip("Markdown", "Markdown", parseMode == "Markdown", TelegramSkyBlue) { parseMode = "Markdown" }
                        ProviderChip("None", "Plain Text", parseMode == "None", TelegramSkyBlue) { parseMode = "None" }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Disable Web Page Previews", color = SlateTextPrimary, fontSize = 12.sp)
                        Switch(checked = disableWebPagePreview, onCheckedChange = { disableWebPagePreview = it }, colors = SwitchDefaults.colors(checkedTrackColor = TelegramSkyBlue))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Silent Notifications (no audio ping)", color = SlateTextPrimary, fontSize = 12.sp)
                        Switch(checked = silentNotifications, onCheckedChange = { silentNotifications = it }, colors = SwitchDefaults.colors(checkedTrackColor = TelegramSkyBlue))
                    }

                    // Webhook Configuration
                    Text("WEBHOOK INBOUND LISTENER", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("Telegram Webhook URL") },
                        placeholder = { Text("https://your-server.com/tg/webhook") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = webhookSecretToken,
                        onValueChange = { webhookSecretToken = it },
                        label = { Text("Webhook Secret Token (X-Telegram-Bot-Api-Secret-Token)") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inbound Auto-Reply Agent", color = SlateTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Autonomous OpenClaw agent responds to Telegram messages in real-time", color = SlateTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = autoReplyEnabled,
                            onCheckedChange = { autoReplyEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = TelegramSkyBlue)
                        )
                    }

                    if (autoReplyEnabled) {
                        OutlinedTextField(
                            value = autoReplyPrompt,
                            onValueChange = { autoReplyPrompt = it },
                            label = { Text("Inbound Telegram Agent Prompt") },
                            minLines = 2,
                            maxLines = 4,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live Test Dispatch Section
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("LIVE TEST TELEGRAM DISPATCH", color = TelegramSkyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = testChatId,
                                onValueChange = { testChatId = it },
                                label = { Text("Test Target Chat ID") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = testMessage,
                                onValueChange = { testMessage = it },
                                label = { Text("Test Message") },
                                minLines = 2,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onTest(testChatId, testMessage) },
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramSkyBlue),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.End).testTag("tg_send_test_btn")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Test to Telegram", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Divider(color = ObsidianBorder)

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Button(
                        onClick = {
                            val userList = allowedUserIdsText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(
                                config.copy(
                                    isEnabled = isEnabled,
                                    botToken = botToken.trim(),
                                    botUsername = botUsername.trim(),
                                    defaultChatId = defaultChatId.trim(),
                                    allowedUserIds = userList,
                                    parseMode = parseMode,
                                    pollingMode = pollingMode,
                                    disableWebPagePreview = disableWebPagePreview,
                                    silentNotifications = silentNotifications,
                                    webhookUrl = webhookUrl.trim(),
                                    webhookSecretToken = webhookSecretToken.trim(),
                                    autoReplyEnabled = autoReplyEnabled,
                                    autoReplyPrompt = autoReplyPrompt.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramSkyBlue),
                        modifier = Modifier.testTag("tg_save_config_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SMS Configuration Dialog
// -------------------------------------------------------------
@Composable
fun SmsConfigDialog(
    config: SmsConfig,
    onDismiss: () -> Unit,
    onSave: (SmsConfig) -> Unit,
    onTest: (recipient: String, message: String) -> Unit
) {
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    var providerType by remember { mutableStateOf(config.providerType) }
    var accountSid by remember { mutableStateOf(config.accountSid) }
    var authToken by remember { mutableStateOf(config.authToken) }
    var fromPhoneNumber by remember { mutableStateOf(config.fromPhoneNumber) }
    var defaultRecipient by remember { mutableStateOf(config.defaultRecipient) }
    var customGatewayUrl by remember { mutableStateOf(config.customGatewayUrl) }
    var maxPartsPerMessage by remember { mutableStateOf(config.maxPartsPerMessage.toString()) }
    var enableDeliveryReports by remember { mutableStateOf(config.enableDeliveryReports) }

    var showToken by remember { mutableStateOf(false) }
    var testRecipient by remember { mutableStateOf(config.defaultRecipient) }
    var testMessage by remember { mutableStateOf("OpenClaw SMS Alert: Autonomous agent system check.") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, SmsAmber.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = SmsAmber, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMS Advanced Settings",
                            color = SlateTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateTextSecondary)
                    }
                }

                Text(
                    text = "Configure Twilio SMS REST API, Native Android Telephony, or Custom HTTP SMS Gateways.",
                    color = SlateTextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                Divider(color = ObsidianBorder)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Enable switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable SMS Channel", color = SlateTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Dispatch automated cellular text messages", color = SlateTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = SmsAmber)
                        )
                    }

                    // Provider Selector
                    Text("SMS ENGINE PROVIDER", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderChip("twilio", "Twilio SMS", providerType == "twilio", SmsAmber) { providerType = "twilio" }
                        ProviderChip("android_telephony", "Android Native", providerType == "android_telephony", SmsAmber) { providerType = "android_telephony" }
                        ProviderChip("custom_http", "HTTP Gateway", providerType == "custom_http", SmsAmber) { providerType = "custom_http" }
                    }

                    // Credentials
                    Text("CREDENTIALS & SENDER IDENTITY", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    if (providerType == "twilio") {
                        OutlinedTextField(
                            value = accountSid,
                            onValueChange = { accountSid = it },
                            label = { Text("Twilio Account SID") },
                            placeholder = { Text("ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("sms_sid_input")
                        )

                        OutlinedTextField(
                            value = authToken,
                            onValueChange = { authToken = it },
                            label = { Text("Twilio Auth Token") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle token visibility",
                                        tint = SlateTextTertiary
                                    )
                                }
                            },
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("sms_token_input")
                        )
                    } else if (providerType == "custom_http") {
                        OutlinedTextField(
                            value = customGatewayUrl,
                            onValueChange = { customGatewayUrl = it },
                            label = { Text("Gateway HTTP Endpoint URL") },
                            placeholder = { Text("https://api.sms-gateway.com/send") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = fromPhoneNumber,
                        onValueChange = { fromPhoneNumber = it },
                        label = { Text("From Phone Number / Sender ID") },
                        placeholder = { Text("+18005550199 or OPENCLAW") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("sms_from_input")
                    )

                    OutlinedTextField(
                        value = defaultRecipient,
                        onValueChange = { defaultRecipient = it },
                        label = { Text("Default Recipient Phone (E.164)") },
                        placeholder = { Text("+1234567890") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("sms_default_rec_input")
                    )

                    // Limits & Callbacks
                    Text("CONCATENATION & DELIVERY REPORTS", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxPartsPerMessage,
                            onValueChange = { maxPartsPerMessage = it },
                            label = { Text("Max Multipart SMS Limit") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Request Carrier Delivery Reports", color = SlateTextPrimary, fontSize = 12.sp)
                        Switch(checked = enableDeliveryReports, onCheckedChange = { enableDeliveryReports = it }, colors = SwitchDefaults.colors(checkedTrackColor = SmsAmber))
                    }

                    // Live Test SMS Section
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("LIVE TEST SMS DISPATCH", color = SmsAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = testRecipient,
                                onValueChange = { testRecipient = it },
                                label = { Text("Test Recipient Phone") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = testMessage,
                                onValueChange = { testMessage = it },
                                label = { Text("Test Message Body") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onTest(testRecipient, testMessage) },
                                colors = ButtonDefaults.buttonColors(containerColor = SmsAmber),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.End).testTag("sms_send_test_btn")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Test SMS", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Divider(color = ObsidianBorder)

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Button(
                        onClick = {
                            onSave(
                                config.copy(
                                    isEnabled = isEnabled,
                                    providerType = providerType,
                                    accountSid = accountSid.trim(),
                                    authToken = authToken.trim(),
                                    fromPhoneNumber = fromPhoneNumber.trim(),
                                    defaultRecipient = defaultRecipient.trim(),
                                    customGatewayUrl = customGatewayUrl.trim(),
                                    maxPartsPerMessage = maxPartsPerMessage.toIntOrNull() ?: 3,
                                    enableDeliveryReports = enableDeliveryReports
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SmsAmber),
                        modifier = Modifier.testTag("sms_save_config_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Email Configuration Dialog
// -------------------------------------------------------------
@Composable
fun EmailConfigDialog(
    config: EmailConfig,
    onDismiss: () -> Unit,
    onSave: (EmailConfig) -> Unit,
    onTest: (to: String, subject: String, body: String) -> Unit
) {
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    var providerType by remember { mutableStateOf(config.providerType) }
    var smtpHost by remember { mutableStateOf(config.smtpHost) }
    var smtpPort by remember { mutableStateOf(config.smtpPort.toString()) }
    var useTls by remember { mutableStateOf(config.useTls) }
    var useSsl by remember { mutableStateOf(config.useSsl) }
    var smtpUsername by remember { mutableStateOf(config.smtpUsername) }
    var smtpPasswordOrApiKey by remember { mutableStateOf(config.smtpPasswordOrApiKey) }
    var fromEmail by remember { mutableStateOf(config.fromEmail) }
    var fromName by remember { mutableStateOf(config.fromName) }
    var defaultToEmail by remember { mutableStateOf(config.defaultToEmail) }
    var ccEmailsText by remember { mutableStateOf(config.ccEmails.joinToString(", ")) }
    var subjectPrefix by remember { mutableStateOf(config.subjectPrefix) }
    var bodyFormat by remember { mutableStateOf(config.bodyFormat) }
    var apiEndpoint by remember { mutableStateOf(config.apiEndpoint) }

    var showPassword by remember { mutableStateOf(false) }
    var testTo by remember { mutableStateOf(config.defaultToEmail.ifBlank { "user@example.com" }) }
    var testSubject by remember { mutableStateOf("OpenClaw Autonomous Email Alert") }
    var testBody by remember { mutableStateOf("<h2>Autonomous Agent Notice</h2><p>This is a test transmission sent from OpenClaw.</p>") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, EmailIndigo.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = EmailIndigo, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Email Advanced Settings",
                            color = SlateTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateTextSecondary)
                    }
                }

                Text(
                    text = "Configure SMTP (TLS/SSL), Resend REST API, SendGrid, or AWS SES email delivery.",
                    color = SlateTextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                Divider(color = ObsidianBorder)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Enable switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Email Channel", color = SlateTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Dispatch automated reports and structured email alerts", color = SlateTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = EmailIndigo)
                        )
                    }

                    // Provider Selector
                    Text("DELIVERY ENGINE", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderChip("smtp", "Standard SMTP", providerType == "smtp", EmailIndigo) { providerType = "smtp" }
                        ProviderChip("resend", "Resend API", providerType == "resend", EmailIndigo) { providerType = "resend" }
                        ProviderChip("sendgrid", "SendGrid v3", providerType == "sendgrid", EmailIndigo) { providerType = "sendgrid" }
                    }

                    // SMTP / API Configuration
                    Text("SERVER & AUTHENTICATION (ENCRYPTED)", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    if (providerType == "smtp") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = smtpHost,
                                onValueChange = { smtpHost = it },
                                label = { Text("SMTP Host") },
                                placeholder = { Text("smtp.gmail.com") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.weight(1.6f).testTag("email_host_input")
                            )
                            OutlinedTextField(
                                value = smtpPort,
                                onValueChange = { smtpPort = it },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors(),
                                modifier = Modifier.weight(0.7f).testTag("email_port_input")
                            )
                        }

                        // Encryption protocol
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProviderChip("TLS", "STARTTLS (587)", useTls, EmailIndigo) { useTls = true; useSsl = false }
                            ProviderChip("SSL", "SSL/TLS (465)", useSsl, EmailIndigo) { useSsl = true; useTls = false }
                            ProviderChip("PLAIN", "Plain (25)", !useTls && !useSsl, EmailIndigo) { useTls = false; useSsl = false }
                        }
                    } else {
                        OutlinedTextField(
                            value = apiEndpoint,
                            onValueChange = { apiEndpoint = it },
                            label = { Text("REST API Endpoint") },
                            placeholder = { Text("https://api.resend.com/emails") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = smtpUsername,
                        onValueChange = { smtpUsername = it },
                        label = { Text(if (providerType == "smtp") "SMTP Username / Account Email" else "API Key ID") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("email_user_input")
                    )

                    OutlinedTextField(
                        value = smtpPasswordOrApiKey,
                        onValueChange = { smtpPasswordOrApiKey = it },
                        label = { Text(if (providerType == "smtp") "SMTP App Password / Secret" else "API Secret Token / Bearer Key") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = SlateTextTertiary
                                )
                            }
                        },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("email_pass_input")
                    )

                    // Sender & Recipient Headers
                    Text("SENDER & RECIPIENT HEADERS", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromEmail,
                            onValueChange = { fromEmail = it },
                            label = { Text("From Email") },
                            placeholder = { Text("agent@openclaw.ai") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.weight(1.2f).testTag("email_from_input")
                        )
                        OutlinedTextField(
                            value = fromName,
                            onValueChange = { fromName = it },
                            label = { Text("Display Name") },
                            placeholder = { Text("OpenClaw Agent") },
                            singleLine = true,
                            colors = fieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = defaultToEmail,
                        onValueChange = { defaultToEmail = it },
                        label = { Text("Default Recipient (To:)") },
                        placeholder = { Text("admin@example.com") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("email_to_input")
                    )

                    OutlinedTextField(
                        value = ccEmailsText,
                        onValueChange = { ccEmailsText = it },
                        label = { Text("CC Recipients (comma-separated)") },
                        placeholder = { Text("ops@example.com, alert@example.com") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = subjectPrefix,
                        onValueChange = { subjectPrefix = it },
                        label = { Text("Subject Line Tag / Prefix") },
                        placeholder = { Text("[OpenClaw Alert] ") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Body Format: Rich HTML", color = SlateTextPrimary, fontSize = 12.sp)
                        Switch(
                            checked = bodyFormat == "html",
                            onCheckedChange = { bodyFormat = if (it) "html" else "plain_text" },
                            colors = SwitchDefaults.colors(checkedTrackColor = EmailIndigo)
                        )
                    }

                    // Live Test Email Section
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("LIVE TEST EMAIL DISPATCH", color = EmailIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = testTo,
                                onValueChange = { testTo = it },
                                label = { Text("Test Recipient Email") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = testSubject,
                                onValueChange = { testSubject = it },
                                label = { Text("Test Subject") },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = testBody,
                                onValueChange = { testBody = it },
                                label = { Text("Test Body Content") },
                                minLines = 2,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onTest(testTo, testSubject, testBody) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmailIndigo),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.End).testTag("email_send_test_btn")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Test Email", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Divider(color = ObsidianBorder)

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Button(
                        onClick = {
                            val ccList = ccEmailsText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(
                                config.copy(
                                    isEnabled = isEnabled,
                                    providerType = providerType,
                                    smtpHost = smtpHost.trim(),
                                    smtpPort = smtpPort.toIntOrNull() ?: 587,
                                    useTls = useTls,
                                    useSsl = useSsl,
                                    smtpUsername = smtpUsername.trim(),
                                    smtpPasswordOrApiKey = smtpPasswordOrApiKey.trim(),
                                    fromEmail = fromEmail.trim(),
                                    fromName = fromName.trim(),
                                    defaultToEmail = defaultToEmail.trim(),
                                    ccEmails = ccList,
                                    subjectPrefix = subjectPrefix.trim(),
                                    bodyFormat = bodyFormat,
                                    apiEndpoint = apiEndpoint.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmailIndigo),
                        modifier = Modifier.testTag("email_save_config_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Configuration", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Multi-Channel Broadcast Dialog
// -------------------------------------------------------------
@Composable
fun BroadcastAlertDialog(
    config: CommunicationChannelsConfig,
    onDismiss: () -> Unit,
    onBroadcast: (title: String, body: String) -> Unit
) {
    var title by remember { mutableStateOf("OpenClaw Autonomous Alert") }
    var message by remember { mutableStateOf("Critical task completed. Multi-channel synchronization active.") }

    val activeList = listOfNotNull(
        if (config.whatsapp.isEnabled) "WhatsApp (${config.whatsapp.defaultRecipient.ifBlank { "Configured" }})" else null,
        if (config.telegram.isEnabled) "Telegram (${config.telegram.defaultChatId.ifBlank { "Bot" }})" else null,
        if (config.sms.isEnabled) "SMS (${config.sms.defaultRecipient.ifBlank { "Configured" }})" else null,
        if (config.email.isEnabled) "Email (${config.email.defaultToEmail.ifBlank { "Configured" }})" else null
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcast Multi-Channel Alert", color = SlateTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Dispatches simultaneous alert payloads across all active channels.", color = SlateTextSecondary, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(12.dp))

                // Active channels overview
                Text("TARGET CHANNELS (${activeList.size} ENABLED):", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                if (activeList.isEmpty()) {
                    Text("No channels are currently enabled. Enable WhatsApp, Telegram, SMS, or Email in settings.", color = RubyRed, fontSize = 11.sp)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeList.forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ObsidianCardElevated
                            ) {
                                Text(name, color = EmeraldLight, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Alert Title / Subject") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("broadcast_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Broadcast Message Content") },
                    minLines = 3,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("broadcast_msg_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Button(
                        onClick = { onBroadcast(title, message) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        enabled = activeList.isNotEmpty() && message.isNotBlank(),
                        modifier = Modifier.testTag("broadcast_submit_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Broadcast Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Quick Test Dialog
// -------------------------------------------------------------
@Composable
fun QuickTestChannelDialog(
    channelType: ChannelType,
    config: CommunicationChannelsConfig,
    onDismiss: () -> Unit,
    onSendTest: (recipient: String, message: String, subject: String?) -> Unit
) {
    val accentColor = when (channelType) {
        ChannelType.WHATSAPP -> WhatsAppGreen
        ChannelType.TELEGRAM -> TelegramSkyBlue
        ChannelType.SMS -> SmsAmber
        ChannelType.EMAIL -> EmailIndigo
    }

    var recipient by remember {
        mutableStateOf(
            when (channelType) {
                ChannelType.WHATSAPP -> config.whatsapp.defaultRecipient
                ChannelType.TELEGRAM -> config.telegram.defaultChatId
                ChannelType.SMS -> config.sms.defaultRecipient
                ChannelType.EMAIL -> config.email.defaultToEmail
            }
        )
    }

    var subject by remember { mutableStateOf("OpenClaw Quick Test") }
    var message by remember { mutableStateOf("Quick test ping from OpenClaw ${channelType.displayName} channel.") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (channelType) {
                                ChannelType.WHATSAPP -> Icons.Default.ChatBubble
                                ChannelType.TELEGRAM -> Icons.Default.Send
                                ChannelType.SMS -> Icons.Default.Sms
                                ChannelType.EMAIL -> Icons.Default.Email
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Quick Test: ${channelType.displayName}", color = SlateTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text(if (channelType == ChannelType.TELEGRAM) "Target Chat ID" else if (channelType == ChannelType.EMAIL) "To Email" else "Phone Number") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (channelType == ChannelType.EMAIL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message Body") },
                    minLines = 2,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Button(
                        onClick = { onSendTest(recipient, message, if (channelType == ChannelType.EMAIL) subject else null) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        enabled = message.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send Test", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Helpers & Components
// -------------------------------------------------------------
@Composable
fun ProviderChip(id: String, label: String, isSelected: Boolean, accent: Color, onSelect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) accent.copy(alpha = 0.2f) else ObsidianCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) accent else ObsidianBorder),
        modifier = Modifier.clickable(onClick = onSelect)
    ) {
        Text(
            text = label,
            color = if (isSelected) SlateTextPrimary else SlateTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldPrimary,
    unfocusedBorderColor = ObsidianBorder,
    focusedLabelColor = EmeraldPrimary,
    unfocusedLabelColor = SlateTextTertiary,
    focusedTextColor = SlateTextPrimary,
    unfocusedTextColor = SlateTextPrimary,
    cursorColor = EmeraldPrimary
)
