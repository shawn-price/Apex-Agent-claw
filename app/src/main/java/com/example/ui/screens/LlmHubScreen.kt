package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.storage.LlmGenerationSettings
import com.example.storage.LlmProviderConfig
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmHubScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedProviderForEdit by remember { mutableStateOf<LlmProviderConfig?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Agents Hub & Tool Manager, 1: Providers (15+), 2: Hyperparameters, 3: Channels (WhatsApp, Telegram, SMS, Email)

    val activeChannelsCount = listOf(
        uiState.channelsConfig.whatsapp.isEnabled,
        uiState.channelsConfig.telegram.isEnabled,
        uiState.channelsConfig.sms.isEnabled,
        uiState.channelsConfig.email.isEnabled
    ).count { it }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (selectedTab) {
                                0 -> "Autonomous Agent Hub"
                                1 -> "Universal LLM Hub"
                                2 -> "Advanced Hyperparameters"
                                else -> "Communication Channels & Dispatch"
                            },
                            color = SlateTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (selectedTab) {
                                0 -> "Dynamic Capabilities, Tool Assignment & Fleet Registry"
                                1 -> "15+ Cloud & Local AI Engines • Latency Optimizer"
                                2 -> "Temperature, Top-P, JSON Mode & Penalties"
                                else -> "WhatsApp • Telegram • SMS • Email • Multi-Channel"
                            },
                            color = CyanGlow,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        FilledTonalButton(
                            onClick = { viewModel.pingAllProviders() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ObsidianCardElevated,
                                contentColor = EmeraldLight
                            ),
                            modifier = Modifier.padding(end = 8.dp).testTag("ping_all_btn")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ping All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Switcher
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianSurface,
                contentColor = EmeraldPrimary,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (selectedTab == 0) EmeraldPrimary else SlateTextSecondary)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Agents & Tools Hub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (selectedTab == 0) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianBorder
                            ) {
                                Text(
                                    text = "${uiState.agents.size}",
                                    color = if (selectedTab == 0) EmeraldLight else SlateTextTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Providers & Models (15+)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Hyperparameters", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Communication Channels", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (activeChannelsCount > 0) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianBorder
                            ) {
                                Text(
                                    text = "$activeChannelsCount/4",
                                    color = if (activeChannelsCount > 0) EmeraldLight else SlateTextTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Autonomous Agent Fleet & Dynamic Tool Manager
                    AgentHubView(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
                1 -> {
                    // Providers List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.providers) { provider ->
                            ProviderCard(
                                provider = provider,
                                isActive = provider.id == uiState.settings.activeProviderId,
                                onSelectActive = {
                                    viewModel.selectModel(provider.id, provider.defaultModel)
                                },
                                onPing = { viewModel.pingProvider(provider.id) },
                                onEdit = { selectedProviderForEdit = provider },
                                onToggleEnable = { enabled ->
                                    viewModel.updateProvider(provider.copy(isEnabled = enabled))
                                }
                            )
                        }
                    }
                }
                2 -> {
                    // Advanced Hyperparameters & Settings
                    AdvancedSettingsView(
                        settings = uiState.settings,
                        providers = uiState.providers,
                        onSave = { updated -> viewModel.updateSettings(updated) }
                    )
                }
                3 -> {
                    // Communication Channels Settings & Dispatch Matrix
                    ChannelsSettingsView(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (selectedProviderForEdit != null) {
        EditProviderDialog(
            provider = selectedProviderForEdit!!,
            onDismiss = { selectedProviderForEdit = null },
            onSave = { updated ->
                viewModel.updateProvider(updated)
                selectedProviderForEdit = null
            }
        )
    }
}

@Composable
fun ProviderCard(
    provider: LlmProviderConfig,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onPing: () -> Unit,
    onEdit: () -> Unit,
    onToggleEnable: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) ObsidianCardElevated else ObsidianCard
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isActive) EmeraldPrimary else ObsidianBorder)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (provider.isLocal) Icons.Default.Computer else Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = if (provider.isLocal) AmberGold else CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                color = SlateTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = EmeraldDark,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = EmeraldLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = provider.defaultModel,
                            color = SlateTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = onToggleEnable,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldPrimary,
                            checkedTrackColor = EmeraldDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Endpoint and Latency row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = provider.baseUrl.ifBlank { "Cloud Gateway" },
                    color = SlateTextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (provider.latencyMs > 0) {
                        Surface(
                            color = EmeraldDark.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${provider.latencyMs} ms",
                                color = EmeraldLight,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Ping button
                    IconButton(
                        onClick = onPing,
                        modifier = Modifier.size(28.dp).testTag("ping_btn_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Ping",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Edit button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp).testTag("edit_btn_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (!isActive) {
                        FilledTonalButton(
                            onClick = onSelectActive,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Select", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsView(
    settings: LlmGenerationSettings,
    providers: List<LlmProviderConfig>,
    onSave: (LlmGenerationSettings) -> Unit
) {
    var temp by remember { mutableFloatStateOf(settings.temperature) }
    var topP by remember { mutableFloatStateOf(settings.topP) }
    var topK by remember { mutableFloatStateOf(settings.topK.toFloat()) }
    var freqPenalty by remember { mutableFloatStateOf(settings.frequencyPenalty) }
    var presPenalty by remember { mutableFloatStateOf(settings.presencePenalty) }
    var maxTokens by remember { mutableStateOf(settings.maxTokens.toString()) }
    var jsonMode by remember { mutableStateOf(settings.jsonModeEnabled) }
    var streaming by remember { mutableStateOf(settings.streamingEnabled) }
    var autoFallback by remember { mutableStateOf(settings.autoOfflineFallback) }
    var fallbackProvId by remember { mutableStateOf(settings.fallbackProviderId) }
    var systemPrompt by remember { mutableStateOf(settings.systemPrompt) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Autonomous System Prompt",
                        color = SlateTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sampling Hyperparameters", color = SlateTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Temperature
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Temperature", color = SlateTextSecondary, fontSize = 12.sp)
                        Text(String.format(Locale.US, "%.2f", temp), color = CyanGlow, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = temp,
                        onValueChange = { temp = it },
                        valueRange = 0.0f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary)
                    )

                    // Top P
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Top P (Nucleus)", color = SlateTextSecondary, fontSize = 12.sp)
                        Text(String.format(Locale.US, "%.2f", topP), color = CyanGlow, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = topP,
                        onValueChange = { topP = it },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                    )

                    // Top K
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Top K", color = SlateTextSecondary, fontSize = 12.sp)
                        Text("${topK.toInt()}", color = CyanGlow, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = topK,
                        onValueChange = { topK = it },
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Penalties & Output Length", color = SlateTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Frequency Penalty
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Frequency Penalty", color = SlateTextSecondary, fontSize = 12.sp)
                        Text(String.format(Locale.US, "%.2f", freqPenalty), color = CyanGlow, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = freqPenalty,
                        onValueChange = { freqPenalty = it },
                        valueRange = -2.0f..2.0f
                    )

                    // Presence Penalty
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Presence Penalty", color = SlateTextSecondary, fontSize = 12.sp)
                        Text(String.format(Locale.US, "%.2f", presPenalty), color = CyanGlow, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = presPenalty,
                        onValueChange = { presPenalty = it },
                        valueRange = -2.0f..2.0f
                    )

                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("Max Output Tokens") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Modes & Fallback Automation", color = SlateTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Streaming Output (SSE)", color = SlateTextPrimary, fontSize = 13.sp)
                        Switch(checked = streaming, onCheckedChange = { streaming = it })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Strict JSON Mode", color = SlateTextPrimary, fontSize = 13.sp)
                        Switch(checked = jsonMode, onCheckedChange = { jsonMode = it })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Auto Offline / Error Fallback", color = SlateTextPrimary, fontSize = 13.sp)
                            Text("Switches to local Ollama or embedded engine", color = SlateTextTertiary, fontSize = 10.sp)
                        }
                        Switch(checked = autoFallback, onCheckedChange = { autoFallback = it })
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val updated = settings.copy(
                        systemPrompt = systemPrompt,
                        temperature = temp,
                        topP = topP,
                        topK = topK.toInt(),
                        frequencyPenalty = freqPenalty,
                        presencePenalty = presPenalty,
                        maxTokens = maxTokens.toIntOrNull() ?: 4096,
                        jsonModeEnabled = jsonMode,
                        streamingEnabled = streaming,
                        autoOfflineFallback = autoFallback,
                        fallbackProviderId = fallbackProvId
                    )
                    onSave(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianDark)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Generation Hyperparameters", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EditProviderDialog(
    provider: LlmProviderConfig,
    onDismiss: () -> Unit,
    onSave: (LlmProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(provider.name) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var defaultModel by remember { mutableStateOf(provider.defaultModel) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Configure ${provider.name}",
                    color = SlateTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (Encrypted in AES-256-GCM)") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("Default Model ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = provider.copy(
                                name = name,
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                defaultModel = defaultModel
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianDark)
                    ) {
                        Text("Save Config")
                    }
                }
            }
        }
    }
}
