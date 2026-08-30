package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.channels.*
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.*

// Custom Channel Accent Colors
val WhatsAppGreen = Color(0xFF25D366)
val TelegramSkyBlue = Color(0xFF229ED9)
val SmsAmber = Color(0xFFF59E0B)
val EmailIndigo = Color(0xFF818CF8)

@Composable
fun ChannelsSettingsView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    var showSmsDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var showQuickTestChannel by remember { mutableStateOf<ChannelType?>(null) }

    val config = uiState.channelsConfig
    val activeChannelsCount = listOf(
        config.whatsapp.isEnabled,
        config.telegram.isEnabled,
        config.sms.isEnabled,
        config.email.isEnabled
    ).count { it }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Multi-Channel Status Header
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldPrimary.copy(alpha = 0.35f))),
                modifier = Modifier.fillMaxWidth().testTag("channels_summary_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (activeChannelsCount > 0) EmeraldPrimary else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Autonomous Dispatch Matrix",
                                    color = SlateTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "$activeChannelsCount / 4 Communication Channels Active",
                                color = SlateTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = { showBroadcastDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = EmeraldDark.copy(alpha = 0.4f),
                                contentColor = EmeraldLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_broadcast_alert")
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Broadcast Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = ObsidianBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ChannelMiniBadge("WhatsApp", config.whatsapp.isEnabled, WhatsAppGreen)
                        ChannelMiniBadge("Telegram", config.telegram.isEnabled, TelegramSkyBlue)
                        ChannelMiniBadge("SMS", config.sms.isEnabled, SmsAmber)
                        ChannelMiniBadge("Email", config.email.isEnabled, EmailIndigo)
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "COMMUNICATION CHANNELS",
                color = SlateTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        // 1. WhatsApp Card
        item {
            ChannelCard(
                title = "WhatsApp",
                subtitle = when (config.whatsapp.providerType) {
                    "twilio" -> "Twilio WhatsApp API • ${config.whatsapp.phoneNumberId.ifBlank { "Unconfigured" }}"
                    "custom_webhook" -> "Custom Webhook Relay • ${config.whatsapp.webhookCallbackUrl.ifBlank { "No URL" }}"
                    else -> "Meta Cloud API v20.0 • ${config.whatsapp.phoneNumberId.ifBlank { "ID not set" }}"
                },
                accentColor = WhatsAppGreen,
                icon = Icons.Default.ChatBubble,
                isEnabled = config.whatsapp.isEnabled,
                status = config.whatsapp.status,
                latencyMs = config.whatsapp.lastPingLatencyMs,
                onToggle = { enabled -> viewModel.updateWhatsAppConfig(config.whatsapp.copy(isEnabled = enabled)) },
                onConfigure = { showWhatsAppDialog = true },
                onQuickTest = { showQuickTestChannel = ChannelType.WHATSAPP },
                tagPrefix = "whatsapp"
            )
        }

        // 2. Telegram Card
        item {
            ChannelCard(
                title = "Telegram",
                subtitle = "Bot ${config.telegram.botUsername.ifBlank { "Token not configured" }} • ${config.telegram.parseMode} • Chat: ${config.telegram.defaultChatId.ifBlank { "None" }}",
                accentColor = TelegramSkyBlue,
                icon = Icons.Default.Send,
                isEnabled = config.telegram.isEnabled,
                status = config.telegram.status,
                latencyMs = config.telegram.lastPingLatencyMs,
                onToggle = { enabled -> viewModel.updateTelegramConfig(config.telegram.copy(isEnabled = enabled)) },
                onConfigure = { showTelegramDialog = true },
                onQuickTest = { showQuickTestChannel = ChannelType.TELEGRAM },
                tagPrefix = "telegram"
            )
        }

        // 3. SMS Card
        item {
            ChannelCard(
                title = "SMS Text Messaging",
                subtitle = when (config.sms.providerType) {
                    "android_telephony" -> "Android Native Telephony (SmsManager)"
                    "custom_http" -> "Custom SMS Gateway HTTP Relay"
                    else -> "Twilio SMS REST API • From: ${config.sms.fromPhoneNumber.ifBlank { "Default" }}"
                },
                accentColor = SmsAmber,
                icon = Icons.Default.Sms,
                isEnabled = config.sms.isEnabled,
                status = config.sms.status,
                latencyMs = config.sms.lastPingLatencyMs,
                onToggle = { enabled -> viewModel.updateSmsConfig(config.sms.copy(isEnabled = enabled)) },
                onConfigure = { showSmsDialog = true },
                onQuickTest = { showQuickTestChannel = ChannelType.SMS },
                tagPrefix = "sms"
            )
        }

        // 4. Email Card
        item {
            ChannelCard(
                title = "Email (SMTP / API)",
                subtitle = when (config.email.providerType) {
                    "resend" -> "Resend REST API • ${config.email.fromEmail}"
                    "sendgrid" -> "SendGrid v3 API • ${config.email.fromEmail}"
                    else -> "SMTP (${config.email.smtpHost}:${config.email.smtpPort}) • ${config.email.fromEmail}"
                },
                accentColor = EmailIndigo,
                icon = Icons.Default.Email,
                isEnabled = config.email.isEnabled,
                status = config.email.status,
                latencyMs = config.email.lastPingLatencyMs,
                onToggle = { enabled -> viewModel.updateEmailConfig(config.email.copy(isEnabled = enabled)) },
                onConfigure = { showEmailDialog = true },
                onQuickTest = { showQuickTestChannel = ChannelType.EMAIL },
                tagPrefix = "email"
            )
        }

        // Transmission Logs Section
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSMISSION DISPATCH LOGS",
                    color = SlateTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )

                if (uiState.transmissionLogs.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearTransmissionLogs() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SlateTextTertiary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", color = SlateTextTertiary, fontSize = 11.sp)
                    }
                }
            }
        }

        if (uiState.transmissionLogs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.HistoryToggleOff, contentDescription = null, tint = SlateTextTertiary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No transmissions logged yet", color = SlateTextSecondary, fontSize = 12.sp)
                        Text("Test a channel above or trigger alerts via OpenClaw agent", color = SlateTextTertiary, fontSize = 10.sp)
                    }
                }
            }
        } else {
            items(uiState.transmissionLogs) { log ->
                TransmissionLogItem(log)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialogs & Modal Sheets
    if (showWhatsAppDialog) {
        WhatsAppConfigDialog(
            config = config.whatsapp,
            onDismiss = { showWhatsAppDialog = false },
            onSave = { updated ->
                viewModel.updateWhatsAppConfig(updated)
                showWhatsAppDialog = false
            },
            onTest = { rec, msg -> viewModel.testWhatsAppChannel(rec, msg) }
        )
    }

    if (showTelegramDialog) {
        TelegramConfigDialog(
            config = config.telegram,
            onDismiss = { showTelegramDialog = false },
            onSave = { updated ->
                viewModel.updateTelegramConfig(updated)
                showTelegramDialog = false
            },
            onVerify = { viewModel.verifyTelegramBot() },
            onTest = { chat, msg -> viewModel.testTelegramChannel(chat, msg) }
        )
    }

    if (showSmsDialog) {
        SmsConfigDialog(
            config = config.sms,
            onDismiss = { showSmsDialog = false },
            onSave = { updated ->
                viewModel.updateSmsConfig(updated)
                showSmsDialog = false
            },
            onTest = { rec, msg -> viewModel.testSmsChannel(rec, msg) }
        )
    }

    if (showEmailDialog) {
        EmailConfigDialog(
            config = config.email,
            onDismiss = { showEmailDialog = false },
            onSave = { updated ->
                viewModel.updateEmailConfig(updated)
                showEmailDialog = false
            },
            onTest = { to, subj, body -> viewModel.testEmailChannel(to, subj, body) }
        )
    }

    if (showBroadcastDialog) {
        BroadcastAlertDialog(
            config = config,
            onDismiss = { showBroadcastDialog = false },
            onBroadcast = { title, body ->
                viewModel.broadcastChannelAlert(title, body)
                showBroadcastDialog = false
            }
        )
    }

    if (showQuickTestChannel != null) {
        QuickTestChannelDialog(
            channelType = showQuickTestChannel!!,
            config = config,
            onDismiss = { showQuickTestChannel = null },
            onSendTest = { recipient, message, subject ->
                when (showQuickTestChannel) {
                    ChannelType.WHATSAPP -> viewModel.testWhatsAppChannel(recipient, message)
                    ChannelType.TELEGRAM -> viewModel.testTelegramChannel(recipient, message)
                    ChannelType.SMS -> viewModel.testSmsChannel(recipient, message)
                    ChannelType.EMAIL -> viewModel.testEmailChannel(recipient, subject ?: "Test Alert", message)
                    null -> {}
                }
                showQuickTestChannel = null
            }
        )
    }
}

@Composable
fun ChannelMiniBadge(name: String, isEnabled: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isEnabled) color else SlateTextTertiary.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = name,
            color = if (isEnabled) SlateTextPrimary else SlateTextTertiary,
            fontSize = 11.sp,
            fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun ChannelCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    isEnabled: Boolean,
    status: String,
    latencyMs: Long,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit,
    onQuickTest: () -> Unit,
    tagPrefix: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isEnabled) accentColor.copy(alpha = 0.4f) else ObsidianBorder
            )
        ),
        modifier = Modifier.fillMaxWidth().testTag("card_$tagPrefix")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                color = SlateTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Status Pill
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (status) {
                                    "Connected", "Ready" -> EmeraldPrimary.copy(alpha = 0.15f)
                                    "Error" -> CrimsonError.copy(alpha = 0.2f)
                                    else -> ObsidianBorder
                                }
                            ) {
                                Text(
                                    text = status,
                                    color = when (status) {
                                        "Connected", "Ready" -> EmeraldLight
                                        "Error" -> CrimsonError
                                        else -> SlateTextTertiary
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (latencyMs > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${latencyMs}ms",
                                    color = CyanGlow,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = subtitle,
                            color = SlateTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedTrackColor = ObsidianBorder
                    ),
                    modifier = Modifier.testTag("switch_$tagPrefix")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onQuickTest,
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("test_btn_$tagPrefix")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send Test", color = SlateTextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onConfigure,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ObsidianCardElevated,
                        contentColor = SlateTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("config_btn_$tagPrefix")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Configure & Advanced", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun TransmissionLogItem(log: ChannelTransmissionLog) {
    val accentColor = when (log.channel) {
        ChannelType.WHATSAPP -> WhatsAppGreen
        ChannelType.TELEGRAM -> TelegramSkyBlue
        ChannelType.SMS -> SmsAmber
        ChannelType.EMAIL -> EmailIndigo
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = log.channel.displayName,
                            color = SlateTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "→ ${log.recipient}",
                            color = SlateTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = log.summary,
                        color = SlateTextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    if (log.errorDetails != null) {
                        Text(
                            text = "Error: ${log.errorDetails}",
                            color = CrimsonError,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = if (log.status == "SENT") EmeraldPrimary.copy(alpha = 0.15f) else CrimsonError.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = log.status,
                        color = if (log.status == "SENT") EmeraldLight else CrimsonError,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "${log.formattedTime} • ${log.latencyMs}ms",
                    color = SlateTextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
