package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.storage.AgentEntity
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditDialog(
    agent: AgentEntity?,
    onDismiss: () -> Unit,
    onSave: (AgentEntity) -> Unit
) {
    val isNew = agent == null
    val emojis = listOf("🦉", "🔍", "💻", "🎨", "📡", "⏱️", "🤖", "⚡", "🧠", "🛡️", "🚀", "📊")
    val colors = listOf(
        0xFF10B981, // Emerald
        0xFF06B6D4, // Cyan
        0xFFA855F7, // Purple
        0xFFF59E0B, // Amber
        0xFFEF4444, // Red
        0xFF3B82F6, // Blue
        0xFFEC4899  // Pink
    )

    var name by remember { mutableStateOf(agent?.name ?: "Custom Agent") }
    var role by remember { mutableStateOf(agent?.role ?: "Autonomous Specialist") }
    var description by remember { mutableStateOf(agent?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(agent?.systemPrompt ?: "You are an autonomous agent...") }
    var modelId by remember { mutableStateOf(agent?.modelId ?: "gemini-3.5-flash") }
    var providerId by remember { mutableStateOf(agent?.providerId ?: "gemini") }
    var avatarEmoji by remember { mutableStateOf(agent?.avatarEmoji ?: "🤖") }
    var avatarColorHex by remember { mutableStateOf(agent?.avatarColorHex ?: 0xFF10B981) }
    var temperature by remember { mutableFloatStateOf(agent?.temperature ?: 0.7f) }
    var isAutonomous by remember { mutableStateOf(agent?.isAutonomous ?: true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .testTag("agent_edit_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurface,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
            )
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
                    Text(
                        text = if (isNew) "Create Autonomous Agent" else "Edit Agent Profile",
                        color = SlateTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar & Color Picker
                    Text("Avatar & Visual Theme", color = SlateTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(avatarColorHex).copy(alpha = 0.2f))
                                .border(2.dp, Color(avatarColorHex), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = avatarEmoji, fontSize = 28.sp)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Emoji Row
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(emojis) { emoji ->
                                    Surface(
                                        color = if (avatarEmoji == emoji) EmeraldDark else ObsidianCard,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { avatarEmoji = emoji }
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }

                            // Color Row
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(colors) { cHex ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(cHex))
                                            .border(
                                                if (avatarColorHex == cHex) 2.dp else 0.dp,
                                                SlateTextPrimary,
                                                CircleShape
                                            )
                                            .clickable { avatarColorHex = cHex }
                                    )
                                }
                            }
                        }
                    }

                    // Agent Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Agent Name") },
                        modifier = Modifier.fillMaxWidth().testTag("agent_name_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary
                        ),
                        singleLine = true
                    )

                    // Agent Role
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("Agent Role / Specialization") },
                        modifier = Modifier.fillMaxWidth().testTag("agent_role_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary
                        ),
                        singleLine = true
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary
                        ),
                        maxLines = 3
                    )

                    // System Prompt
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("Agent System Prompt") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary
                        ),
                        maxLines = 6
                    )

                    // Model & Provider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = modelId,
                            onValueChange = { modelId = it },
                            label = { Text("Model ID") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianCard,
                                unfocusedContainerColor = ObsidianCard,
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = SlateTextPrimary,
                                unfocusedTextColor = SlateTextPrimary
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = providerId,
                            onValueChange = { providerId = it },
                            label = { Text("Provider ID") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianCard,
                                unfocusedContainerColor = ObsidianCard,
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = SlateTextPrimary,
                                unfocusedTextColor = SlateTextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    // Temperature Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temperature: ${String.format("%.2f", temperature)}", color = SlateTextSecondary, fontSize = 12.sp)
                            Text(if (temperature < 0.4f) "Precise/Deterministic" else "Creative/Exploratory", color = SlateTextTertiary, fontSize = 11.sp)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldPrimary,
                                activeTrackColor = EmeraldPrimary,
                                inactiveTrackColor = ObsidianBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Save
                Button(
                    onClick = {
                        val newAgent = AgentEntity(
                            id = agent?.id ?: "agent_${UUID.randomUUID().toString().take(8)}",
                            name = name.ifBlank { "Custom Agent" },
                            role = role.ifBlank { "Autonomous Assistant" },
                            description = description,
                            systemPrompt = systemPrompt,
                            modelId = modelId.ifBlank { "gemini-3.5-flash" },
                            providerId = providerId.ifBlank { "gemini" },
                            tools = agent?.tools ?: listOf("Search", "CodeInterpreter"),
                            avatarEmoji = avatarEmoji,
                            avatarColorHex = avatarColorHex,
                            temperature = temperature,
                            isAutonomous = isAutonomous,
                            isEnabled = agent?.isEnabled ?: true,
                            createdAt = agent?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        onSave(newAgent)
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("save_agent_profile_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = ObsidianDark
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Agent Profile", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
