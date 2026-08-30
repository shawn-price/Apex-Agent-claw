package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.storage.AgentCapabilitiesRegistry
import com.example.storage.AgentEntity
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.AgentEditDialog
import com.example.ui.components.AgentToolManagerDialog
import com.example.ui.components.getToolVectorIcon
import com.example.ui.theme.*

@Composable
fun AgentHubView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var agentForToolManagement by remember { mutableStateOf<AgentEntity?>(null) }
    var agentForEdit by remember { mutableStateOf<AgentEntity?>(null) }
    var isCreatingNewAgent by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    // Keep dialog agent reference synchronized with real-time UI state
    val activeManagedAgent = remember(agentForToolManagement, uiState.agents) {
        agentForToolManagement?.let { managed ->
            uiState.agents.firstOrNull { it.id == managed.id } ?: managed
        }
    }

    val totalToolsAssigned = uiState.agents.sumOf { it.tools.size }
    val activeAgentsCount = uiState.agents.count { it.isEnabled }

    val filteredAgents = remember(uiState.agents, searchQuery, selectedFilter) {
        uiState.agents.filter { agent ->
            val matchesFilter = when (selectedFilter) {
                "Active" -> agent.isEnabled
                "Autonomous" -> agent.isAutonomous
                "With Tools" -> agent.tools.isNotEmpty()
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    agent.name.contains(searchQuery, ignoreCase = true) ||
                    agent.role.contains(searchQuery, ignoreCase = true) ||
                    agent.description.contains(searchQuery, ignoreCase = true) ||
                    agent.tools.any { it.contains(searchQuery, ignoreCase = true) }
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Fleet Overview Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("AGENT FLEET", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${uiState.agents.size} Registered", color = SlateTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("$activeAgentsCount Active", color = EmeraldLight, fontSize = 10.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(EmeraldDark.copy(alpha = 0.5f))
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("CAPABILITIES", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("$totalToolsAssigned Assigned", color = EmeraldPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Room & Zero-DB", color = CyanGlow, fontSize = 10.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1.1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("ACTIVE AGENT", color = SlateTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    val selected = uiState.agents.firstOrNull { it.id == uiState.selectedAgentId } ?: uiState.agents.firstOrNull()
                    Text(selected?.name ?: "None", color = SlateTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${selected?.tools?.size ?: 0} Tools Enabled", color = AmberGold, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search and Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search agents & capabilities...", color = SlateTextTertiary, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateTextTertiary, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateTextTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("search_agents_input"),
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

            // New Agent Button
            FilledTonalButton(
                onClick = { isCreatingNewAgent = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = ObsidianDark
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(46.dp)
                    .testTag("create_agent_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Agent", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Restore defaults
            IconButton(
                onClick = { viewModel.resetDefaultAgents() },
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianCard)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                    .testTag("reset_agents_btn")
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Reset Agents", tint = SlateTextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf("All", "Active", "Autonomous", "With Tools")
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    color = if (isSelected) EmeraldDark.copy(alpha = 0.4f) else ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isSelected) EmeraldPrimary else ObsidianBorder
                        )
                    ),
                    modifier = Modifier.clickable { selectedFilter = filter }
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) EmeraldLight else SlateTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Agents Fleet List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredAgents, key = { it.id }) { agent ->
                val isCurrentActive = agent.id == uiState.selectedAgentId
                AgentCard(
                    agent = agent,
                    isSelected = isCurrentActive,
                    onManageTools = {
                        agentForToolManagement = agent
                    },
                    onSelectActive = {
                        viewModel.selectActiveAgent(agent.id)
                    },
                    onEdit = {
                        agentForEdit = agent
                    },
                    onToggleEnable = { enabled ->
                        viewModel.updateAgent(agent.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis()))
                    },
                    onDelete = {
                        viewModel.deleteAgent(agent.id)
                    }
                )
            }
        }
    }

    // Dynamic Tool Selector Dialog
    if (activeManagedAgent != null) {
        AgentToolManagerDialog(
            agent = activeManagedAgent,
            onDismiss = { agentForToolManagement = null },
            onToggleTool = { toolKey, isEnabled ->
                viewModel.toggleAgentTool(activeManagedAgent.id, toolKey, isEnabled)
            },
            onEnableAll = {
                val allKeys = AgentCapabilitiesRegistry.ALL_AVAILABLE_TOOLS.map { it.key }
                viewModel.updateAgentTools(activeManagedAgent.id, allKeys)
            },
            onDisableAll = {
                viewModel.updateAgentTools(activeManagedAgent.id, emptyList())
            }
        )
    }

    // Edit / Create Agent Dialog
    if (agentForEdit != null || isCreatingNewAgent) {
        AgentEditDialog(
            agent = agentForEdit,
            onDismiss = {
                agentForEdit = null
                isCreatingNewAgent = false
            },
            onSave = { updated ->
                if (agentForEdit != null) {
                    viewModel.updateAgent(updated)
                } else {
                    viewModel.createAgent(updated)
                }
                agentForEdit = null
                isCreatingNewAgent = false
            }
        )
    }
}

@Composable
fun AgentCard(
    agent: AgentEntity,
    isSelected: Boolean,
    onManageTools: () -> Unit,
    onSelectActive: () -> Unit,
    onEdit: () -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val agentColor = Color(agent.avatarColorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_card_${agent.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ObsidianCardElevated else ObsidianCard
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) EmeraldPrimary else ObsidianBorder
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Avatar, Name, Role, and Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(agentColor.copy(alpha = 0.2f))
                            .border(1.5.dp, agentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = agent.avatarEmoji, fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = agent.name,
                                color = SlateTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSelected) {
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
                            text = agent.role,
                            color = agentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = agent.isEnabled,
                        onCheckedChange = onToggleEnable,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldPrimary,
                            checkedTrackColor = EmeraldDark
                        ),
                        modifier = Modifier.testTag("agent_enable_switch_${agent.id}")
                    )
                }
            }

            if (agent.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = agent.description,
                    color = SlateTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Chips (Model, Provider, Temperature)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
                    )
                ) {
                    Text(
                        text = "${agent.providerId} • ${agent.modelId}",
                        color = SlateTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
                    )
                ) {
                    Text(
                        text = "temp: ${String.format("%.2f", agent.temperature)}",
                        color = SlateTextTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(10.dp))

            // Assigned Capabilities & Tool Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = if (agent.tools.isNotEmpty()) EmeraldPrimary else SlateTextTertiary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "CAPABILITIES (${agent.tools.size})",
                        color = SlateTextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Interactive Manage Tools Builder Button
                FilledTonalButton(
                    onClick = onManageTools,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = EmeraldDark.copy(alpha = 0.4f),
                        contentColor = EmeraldLight
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("manage_tools_btn_${agent.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Manage Tools Builder",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manage Tools", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tool Chips Carousel
            if (agent.tools.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(agent.tools) { toolKey ->
                        val def = AgentCapabilitiesRegistry.getToolByKey(toolKey)
                        val toolColor = def?.let { Color(it.colorHex) } ?: EmeraldPrimary
                        val iconVector = getToolVectorIcon(def?.iconName ?: "Build")

                        Surface(
                            color = toolColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(toolColor.copy(alpha = 0.35f))
                            ),
                            modifier = Modifier.clickable { onManageTools() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = toolColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = def?.displayName?.substringBefore(" (") ?: toolKey,
                                    color = toolColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onManageTools() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No tools assigned yet. Tap 'Manage Tools' to enable capabilities.",
                            color = SlateTextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row (Activate, Edit, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSelected) {
                    Button(
                        onClick = onSelectActive,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianCardElevated,
                            contentColor = EmeraldLight
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("select_agent_btn_${agent.id}")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set as Active Agent", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        color = EmeraldDark.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Current Active Agent", color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp).testTag("edit_agent_btn_${agent.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Agent", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                    }

                    if (!agent.id.startsWith("agent_openclaw_prime")) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp).testTag("delete_agent_btn_${agent.id}")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Agent", tint = SlateTextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
