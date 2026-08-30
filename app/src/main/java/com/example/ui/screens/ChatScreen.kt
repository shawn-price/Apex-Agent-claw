package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.storage.Message
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll when new messages or streaming tokens arrive
    LaunchedEffect(uiState.messages.size, uiState.streamingContent) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EverSyncBackground)
    ) {
        // Top App Bar for Chat
        Surface(
            color = EverSyncSurface,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("open_sessions_drawer_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Sessions",
                            tint = SlateTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚡ EverSync AI",
                                color = SlateTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Online/Offline Pill
                            Surface(
                                color = if (uiState.isNetworkOnline) EverSyncViolet.copy(alpha = 0.3f) else RubyRed.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isNetworkOnline) EverSyncCyan else RubyRed)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.isNetworkOnline) "ONLINE" else "OFFLINE",
                                        color = if (uiState.isNetworkOnline) EverSyncCyanGlow else RubyRedLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Active Model Switcher Pill
                        Row(
                            modifier = Modifier
                                .clickable { viewModel.toggleModelPicker(true) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.settings.activeProviderId.uppercase()} • ${uiState.settings.activeModelId}",
                                color = EverSyncCyanGlow,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Model",
                                tint = EverSyncCyanGlow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.createNewConversation() },
                        modifier = Modifier.testTag("new_chat_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = "New Chat",
                            tint = EmeraldPrimary
                        )
                    }
                }
            }
        }

        // Offline Fallback Alert Banner
        if (!uiState.isNetworkOnline) {
            Surface(
                color = AmberGold.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zero-DB File Mode • Local Ollama / Embedded Intelligence Active",
                        color = AmberGoldLight,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Messages Stream View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                // Empty Welcome State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "OpenClaw Autonomous Agent",
                        color = SlateTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "15+ LLM Providers • Zero Database • AES-256-GCM Encrypted Storage",
                        color = SlateTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Suggestion Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { viewModel.sendMessage("Run Python: calculate the first 10 Fibonacci numbers") },
                            label = { Text("⚡ Run Python Fibonacci", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = { viewModel.sendMessage("Search web for latest breakthroughs in AI agents 2026") },
                            label = { Text("🌐 Web Research Agents", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = { viewModel.sendMessage("Inspect zero-database storage stats and encryption keys") },
                            label = { Text("🔒 Zero-DB Health", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = { viewModel.sendMessage("Schedule a reminder task to check system logs every 2 hours") },
                            label = { Text("⏰ Schedule Task", fontSize = 11.sp) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.messages) { message ->
                        MessageBubble(
                            message = message,
                            onRunCode = { code, lang -> viewModel.executeCodeSnippet(code, lang) }
                        )
                    }

                    // Live Streaming assistant bubble
                    if (uiState.isGenerating && (uiState.streamingContent.isNotEmpty() || uiState.streamingThinking.isNotEmpty() || uiState.activeToolCalls.isNotEmpty())) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianCard)
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = uiState.currentAgentStatus,
                                        color = CyanGlow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (uiState.streamingThinking.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ThinkingCard(reasoning = uiState.streamingThinking)
                                }

                                for (tc in uiState.activeToolCalls) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ToolExecutionCard(
                                        name = tc.name,
                                        status = tc.status,
                                        arguments = tc.arguments,
                                        output = tc.output
                                    )
                                }

                                if (uiState.streamingContent.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    MarkdownText(
                                        text = uiState.streamingContent,
                                        onRunCode = { code, lang -> viewModel.executeCodeSnippet(code, lang) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Voice Visualizer Bar if active
        if (uiState.isVoiceActive) {
            VoiceWaveformVisualizer(
                rmsAmplitude = uiState.voiceRms,
                onStopListening = { viewModel.stopVoice() }
            )
        }

        // Bottom Input Area
        Surface(
            color = ObsidianSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Quick Tools Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { inputText = "Run Python: \nprint('Hello OpenClaw Sandbox')" },
                        label = { Text("Python", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Terminal, null, modifier = Modifier.size(12.dp), tint = EmeraldPrimary) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = ObsidianCardElevated)
                    )
                    AssistChip(
                        onClick = { inputText = "Search: " },
                        label = { Text("Search", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(12.dp), tint = CyanAccent) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = ObsidianCardElevated)
                    )
                    AssistChip(
                        onClick = { inputText = "Schedule task: " },
                        label = { Text("Schedule", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(12.dp), tint = AmberGold) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = ObsidianCardElevated)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Mic Button
                    IconButton(
                        onClick = {
                            if (uiState.isVoiceActive) viewModel.stopVoice() else viewModel.startVoice()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isVoiceActive) RubyRed.copy(alpha = 0.2f) else ObsidianCardElevated)
                            .testTag("voice_mic_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.isVoiceActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (uiState.isVoiceActive) RubyRedLight else EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Text Input
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask, run code, search...", color = SlateTextTertiary, fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    IconButton(
                        onClick = {
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendMessage(textToSend)
                        },
                        enabled = inputText.isNotBlank() && !uiState.isGenerating,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !uiState.isGenerating) EmeraldPrimary else ObsidianCardElevated)
                            .testTag("send_message_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !uiState.isGenerating) Color.Black else SlateTextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Model Picker Bottom Sheet
    if (uiState.showModelPicker) {
        QuickModelPickerSheet(
            providers = uiState.providers,
            activeProviderId = uiState.settings.activeProviderId,
            activeModelId = uiState.settings.activeModelId,
            onSelectModel = { provId, modId -> viewModel.selectModel(provId, modId) },
            onDismiss = { viewModel.toggleModelPicker(false) }
        )
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onRunCode: (code: String, language: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender / Model header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isUser) "You" else "⚡ EverSync AI",
                color = if (isUser) EverSyncCyanGlow else EverSyncLavender,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "• ${message.monthPartition}",
                color = SlateTextTertiary,
                fontSize = 10.sp
            )
        }

        // Bubble
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) EverSyncViolet.copy(alpha = 0.25f) else EverSyncCard
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(if (isUser) EverSyncViolet else EverSyncBorder)
            ),
            modifier = Modifier
                .widthIn(max = 340.dp)
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Thinking block
                if (!message.thinkingReasoning.isNullOrBlank()) {
                    ThinkingCard(reasoning = message.thinkingReasoning)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Tool calls
                for (tc in message.toolCalls) {
                    ToolExecutionCard(
                        name = tc.name,
                        status = tc.status,
                        arguments = tc.arguments,
                        output = tc.output
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Content
                if (message.content.isNotBlank()) {
                    MarkdownText(
                        text = message.content,
                        onRunCode = onRunCode
                    )
                }
            }
        }
    }
}
