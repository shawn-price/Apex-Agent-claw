package com.example.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.storage.LlmProviderConfig
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.util.UUID

enum class ProviderCategoryFilter(val label: String) {
    ALL("All Providers"),
    CLOUD("Cloud Gateways"),
    LOCAL("Local & Edge"),
    CONFIGURED("Keys Stored"),
    ENABLED("Enabled Only")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmProviderSettingsView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ProviderCategoryFilter.ALL) }
    var editingProvider by remember { mutableStateOf<LlmProviderConfig?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var showZeroizeKeysDialog by remember { mutableStateOf(false) }
    var showResetDefaultsDialog by remember { mutableStateOf(false) }

    val activeProviderId = uiState.settings.activeProviderId
    val providers = uiState.providers

    // Calculate security vault metrics
    val totalProviders = providers.size
    val enabledCount = providers.count { it.isEnabled }
    val keysConfiguredCount = providers.count { !it.isLocal && it.apiKey.isNotBlank() }
    val localCount = providers.count { it.isLocal }

    // Filter providers based on search query and selected filter
    val filteredProviders = remember(providers, searchQuery, selectedFilter) {
        providers.filter { provider ->
            val matchesSearch = searchQuery.isBlank() ||
                    provider.name.contains(searchQuery, ignoreCase = true) ||
                    provider.type.contains(searchQuery, ignoreCase = true) ||
                    provider.defaultModel.contains(searchQuery, ignoreCase = true) ||
                    provider.baseUrl.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedFilter) {
                ProviderCategoryFilter.ALL -> true
                ProviderCategoryFilter.CLOUD -> !provider.isLocal
                ProviderCategoryFilter.LOCAL -> provider.isLocal
                ProviderCategoryFilter.CONFIGURED -> provider.apiKey.isNotBlank() || provider.isLocal
                ProviderCategoryFilter.ENABLED -> provider.isEnabled
            }

            matchesSearch && matchesCategory
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)
    ) {
        // Security Vault Overview Banner
        item {
            SecurityVaultBanner(
                totalCount = totalProviders,
                enabledCount = enabledCount,
                keysCount = keysConfiguredCount,
                localCount = localCount,
                activeProviderName = providers.firstOrNull { it.id == activeProviderId }?.name ?: "Gemini",
                onPingAll = { viewModel.pingAllProviders() },
                onAddCustom = { showAddCustomDialog = true },
                onZeroizeKeys = { showZeroizeKeysDialog = true },
                onResetDefaults = { showResetDefaultsDialog = true }
            )
        }

        // Search and Filter Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search providers, models, endpoints...", color = SlateTextTertiary, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateTextSecondary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateTextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = SlateTextPrimary,
                        unfocusedTextColor = SlateTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_search_bar")
                )

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ProviderCategoryFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = EmeraldLight
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ObsidianCard,
                                labelColor = SlateTextSecondary,
                                selectedContainerColor = EmeraldDark.copy(alpha = 0.35f),
                                selectedLabelColor = EmeraldLight
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = ObsidianBorder,
                                selectedBorderColor = EmeraldPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Section header with provider count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONFIGURED PROVIDERS (${filteredProviders.size})",
                    color = SlateTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "AES-256 Hardware Encrypted",
                    color = EmeraldLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (filteredProviders.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = SlateTextTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No matching LLM providers found",
                            color = SlateTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Try adjusting your search query or filter tags, or add a custom endpoint.",
                            color = SlateTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(filteredProviders, key = { it.id }) { provider ->
                val isActive = provider.id == activeProviderId

                ProviderManagementCard(
                    provider = provider,
                    isActive = isActive,
                    onSelectActive = {
                        viewModel.selectModel(provider.id, provider.defaultModel)
                    },
                    onToggleEnable = { enabled ->
                        viewModel.toggleProvider(provider.id, enabled)
                    },
                    onUpdateApiKey = { newKey ->
                        viewModel.updateProviderApiKey(provider.id, newKey)
                    },
                    onSelectModel = { modelId ->
                        val updated = provider.copy(defaultModel = modelId)
                        viewModel.updateProvider(updated)
                        if (isActive) {
                            viewModel.selectModel(provider.id, modelId)
                        }
                    },
                    onPing = { viewModel.pingProvider(provider.id) },
                    onEditFull = { editingProvider = provider },
                    onDelete = { viewModel.deleteProvider(provider.id) }
                )
            }
        }
    }

    // Modal Dialogs
    editingProvider?.let { provider ->
        EditProviderConfigDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onSave = { updated ->
                viewModel.updateProvider(updated)
                editingProvider = null
            }
        )
    }

    if (showAddCustomDialog) {
        AddCustomProviderDialog(
            onDismiss = { showAddCustomDialog = false },
            onSave = { newProvider ->
                viewModel.addCustomProvider(newProvider)
                showAddCustomDialog = false
            }
        )
    }

    if (showZeroizeKeysDialog) {
        ZeroizeKeysConfirmDialog(
            onDismiss = { showZeroizeKeysDialog = false },
            onConfirm = {
                viewModel.clearAllApiKeys()
                showZeroizeKeysDialog = false
            }
        )
    }

    if (showResetDefaultsDialog) {
        ResetDefaultsConfirmDialog(
            onDismiss = { showResetDefaultsDialog = false },
            onConfirm = {
                viewModel.resetProvidersToDefaults()
                showResetDefaultsDialog = false
            }
        )
    }
}

/**
 * Security Vault Banner displaying hardware Keystore status, key count, and rapid operations.
 */
@Composable
fun SecurityVaultBanner(
    totalCount: Int,
    enabledCount: Int,
    keysCount: Int,
    localCount: Int,
    activeProviderName: String,
    onPingAll: () -> Unit,
    onAddCustom: () -> Unit,
    onZeroizeKeys: () -> Unit,
    onResetDefaults: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("security_vault_banner")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with Lock Icon & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldDark.copy(alpha = 0.3f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Security Vault",
                                tint = EmeraldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Hardware KeyStore Vault",
                            color = SlateTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AES-256-GCM Zero-Cloud Plaintext",
                            color = EmeraldLight,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldDark.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "SECURE",
                        color = EmeraldLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric statistics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                VaultStatPill(
                    label = "Active Engine",
                    value = activeProviderName,
                    color = CyanGlow
                )
                VaultStatPill(
                    label = "Keys Stored",
                    value = "$keysCount Configured",
                    color = EmeraldLight
                )
                VaultStatPill(
                    label = "Active / Total",
                    value = "$enabledCount / $totalCount Enabled",
                    color = AmberGoldLight
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onPingAll,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ObsidianBorder,
                        contentColor = CyanAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp).testTag("ping_all_providers_btn")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Benchmark All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = onAddCustom,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = EmeraldDark.copy(alpha = 0.35f),
                        contentColor = EmeraldLight
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp).testTag("add_custom_provider_btn")
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Custom", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                IconButton(
                    onClick = onZeroizeKeys,
                    modifier = Modifier.size(34.dp).testTag("clear_all_keys_btn")
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Zeroize All Keys",
                        tint = RubyRedLight,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.size(34.dp).testTag("reset_defaults_btn")
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = "Reset Defaults",
                        tint = SlateTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultStatPill(label: String, value: String, color: Color) {
    Column {
        Text(text = label, color = SlateTextTertiary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/**
 * Rich Material 3 Card representing a single LLM Provider with active selection,
 * toggle enable, model selector chips, secure API Key input with visibility toggle,
 * latency benchmark badge, and endpoint config.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderManagementCard(
    provider: LlmProviderConfig,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onPing: () -> Unit,
    onEditFull: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var apiKeyInput by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isKeyDirty by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    val brandColor = getProviderBrandColor(provider.type)
    val providerIcon = getProviderIcon(provider.type, provider.isLocal)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) ObsidianCardElevated else ObsidianCard
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) EmeraldPrimary else ObsidianBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Brand Icon, Name, Active Badge, Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = brandColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, brandColor.copy(alpha = 0.4f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = providerIcon,
                                contentDescription = null,
                                tint = brandColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                color = SlateTextPrimary,
                                fontSize = 15.sp,
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
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (provider.isLocal) "Local Inference Engine" else "Cloud Gateway API",
                                color = if (provider.isLocal) AmberGold else CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${provider.defaultModel}",
                                color = SlateTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Enable/Disable Switch with dedicated test tag
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleEnable,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldPrimary,
                        checkedTrackColor = EmeraldDark.copy(alpha = 0.5f),
                        uncheckedThumbColor = SlateTextTertiary,
                        uncheckedTrackColor = ObsidianDark
                    ),
                    modifier = Modifier.testTag("provider_toggle_${provider.id}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Available Models Chips Carousel
            if (provider.availableModels.isNotEmpty()) {
                Text(
                    text = "AVAILABLE MODELS",
                    color = SlateTextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    provider.availableModels.take(4).forEach { modelId ->
                        val isSelectedModel = modelId == provider.defaultModel
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelectedModel) EmeraldDark.copy(alpha = 0.4f) else ObsidianDark,
                            border = BorderStroke(
                                1.dp,
                                if (isSelectedModel) EmeraldPrimary else ObsidianBorderLight
                            ),
                            modifier = Modifier
                                .clickable { onSelectModel(modelId) }
                                .testTag("model_chip_${provider.id}_$modelId")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                if (isSelectedModel) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = EmeraldLight,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = modelId,
                                    color = if (isSelectedModel) EmeraldLight else SlateTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelectedModel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Secure API Key Section (if not a pure local engine without key)
            if (!provider.isLocal) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianDark,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    tint = if (provider.apiKey.isNotBlank()) EmeraldPrimary else AmberGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "API Key Vault",
                                    color = SlateTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Key presence badge
                            if (provider.apiKey.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = EmeraldDark.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "ENCRYPTED",
                                        color = EmeraldLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AmberGold.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "NO KEY SET",
                                        color = AmberGoldLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // API Key Input with Visibility Toggle, Paste, and Save Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = {
                                    apiKeyInput = it
                                    isKeyDirty = true
                                },
                                placeholder = {
                                    Text("sk-...", color = SlateTextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                },
                                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    onUpdateApiKey(apiKeyInput)
                                    isKeyDirty = false
                                }),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isKeyVisible = !isKeyVisible },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isKeyVisible) "Hide Key" else "Show Key",
                                            tint = SlateTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = SlateTextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ObsidianCard,
                                    unfocusedContainerColor = ObsidianCard,
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = ObsidianBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("api_key_input_${provider.id}")
                            )

                            // Quick Paste Button
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { clipboardText ->
                                        if (clipboardText.isNotBlank()) {
                                            apiKeyInput = clipboardText.trim()
                                            isKeyDirty = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("paste_api_key_${provider.id}")
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "Paste from Clipboard",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Save Key Button (active when changed)
                            if (isKeyDirty) {
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        onUpdateApiKey(apiKeyInput.trim())
                                        isKeyDirty = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPrimary,
                                        contentColor = ObsidianDark
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("save_api_key_${provider.id}")
                                ) {
                                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (provider.apiKey.isNotBlank()) {
                                // Clear key button
                                IconButton(
                                    onClick = {
                                        apiKeyInput = ""
                                        onUpdateApiKey("")
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("clear_api_key_${provider.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear Key",
                                        tint = RubyRedLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                // Local inference engine info
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianDark,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lan,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Endpoint: ${provider.baseUrl.ifBlank { "http://localhost:11434" }}",
                            color = SlateTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Bottom Action Bar: Select Active, Ping / Benchmark, Advanced Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Latency Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (provider.latencyMs > 0) {
                        Surface(
                            color = EmeraldDark.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
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
                    } else if (provider.latencyMs == -1L && !provider.isOnline) {
                        Surface(
                            color = RubyRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Unreachable",
                                color = RubyRedLight,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = if (provider.isLocal) "On-Device / LAN" else "Cloud Gateway",
                            color = SlateTextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Ping / Verify Button
                    IconButton(
                        onClick = onPing,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("ping_provider_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Test Connection",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Edit full configuration button
                    IconButton(
                        onClick = onEditFull,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("edit_provider_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit Configuration",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Select active button
                    if (!isActive) {
                        Button(
                            onClick = onSelectActive,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = ObsidianDark
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("select_active_${provider.id}")
                        ) {
                            Text("Set Active", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Edit Provider Configuration Dialog for modifying URLs, models, API keys and headers.
 */
@Composable
fun EditProviderConfigDialog(
    provider: LlmProviderConfig,
    onDismiss: () -> Unit,
    onSave: (LlmProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var defaultModel by remember { mutableStateOf(provider.defaultModel) }
    var availableModelsText by remember { mutableStateOf(provider.availableModels.joinToString(", ")) }
    var isKeyVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_provider_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configure ${provider.name}",
                        color = SlateTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Gateway Base URL") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (AES-256 Encrypted)") },
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = SlateTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("Default Model ID") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = availableModelsText,
                    onValueChange = { availableModelsText = it },
                    label = { Text("Available Models (comma-separated)") },
                    maxLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedModels = availableModelsText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            val updated = provider.copy(
                                name = name.trim(),
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                defaultModel = defaultModel.trim(),
                                availableModels = if (parsedModels.isNotEmpty()) parsedModels else provider.availableModels
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = ObsidianDark
                        )
                    ) {
                        Text("Save Configuration", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Add Custom Provider Dialog for connecting custom local or private proxy inference endpoints.
 */
@Composable
fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onSave: (LlmProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("http://192.168.1.100:8000/v1") }
    var apiKey by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("custom-model") }
    var isLocal by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth().testTag("add_custom_provider_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Add Custom LLM Endpoint",
                    color = SlateTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OpenAI-compatible REST API or local server",
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name (e.g. My vLLM Server)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base Endpoint URL") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Bearer Token (Optional)") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("Model Identifier") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Is Local / On-Device Server", color = SlateTextPrimary, fontSize = 13.sp)
                    Switch(checked = isLocal, onCheckedChange = { isLocal = it })
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SlateTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && baseUrl.isNotBlank()) {
                                val newId = "custom_${UUID.randomUUID().toString().take(6)}"
                                val config = LlmProviderConfig(
                                    id = newId,
                                    name = name.trim(),
                                    type = "custom",
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    isEnabled = true,
                                    isLocal = isLocal,
                                    defaultModel = defaultModel.trim().ifBlank { "custom" },
                                    availableModels = listOf(defaultModel.trim().ifBlank { "custom" })
                                )
                                onSave(config)
                            }
                        },
                        enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = ObsidianDark
                        )
                    ) {
                        Text("Add Endpoint", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Security Confirmation Dialog for Zeroizing all stored API keys in hardware keystore.
 */
@Composable
fun ZeroizeKeysConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = RubyRedLight, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Zeroize All API Keys?", fontWeight = FontWeight.Bold, color = SlateTextPrimary)
        },
        text = {
            Text(
                "This will permanently wipe all encrypted API keys from the hardware Keystore vault on this device. You will need to re-enter your keys for cloud inference.",
                color = SlateTextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = RubyRed, contentColor = Color.White)
            ) {
                Text("Wipe All Keys", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateTextSecondary)
            }
        },
        containerColor = ObsidianSurface
    )
}

/**
 * Reset Confirmation Dialog for resetting LLM providers back to factory defaults.
 */
@Composable
fun ResetDefaultsConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Restore, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Reset Providers to Defaults?", fontWeight = FontWeight.Bold, color = SlateTextPrimary)
        },
        text = {
            Text(
                "This will restore the 15+ standard provider configurations (Gemini, OpenAI, Claude, DeepSeek, Groq, Mistral, Ollama, etc.) to their default endpoints and model lists.",
                color = SlateTextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = ObsidianDark)
            ) {
                Text("Restore Defaults", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateTextSecondary)
            }
        },
        containerColor = ObsidianSurface
    )
}

private fun getProviderBrandColor(type: String): Color {
    return when (type.lowercase()) {
        "gemini" -> CyanGlow
        "openai" -> EmeraldPrimary
        "claude" -> Color(0xFFD97706) // Warm Anthropic Amber
        "deepseek" -> Color(0xFF3B82F6) // Deep Blue
        "mistral" -> Color(0xFFF97316) // Mistral Orange
        "groq" -> Color(0xFFF43F5E) // Groq Rose/Red
        "ollama" -> PurpleAccent
        "together" -> Color(0xFF6366F1)
        "openrouter" -> Color(0xFF8B5CF6)
        "perplexity" -> Color(0xFF06B6D4)
        "xai" -> Color(0xFFE2E8F0)
        else -> CyanAccent
    }
}

private fun getProviderIcon(type: String, isLocal: Boolean): ImageVector {
    return when {
        isLocal -> Icons.Default.Computer
        type.equals("gemini", ignoreCase = true) -> Icons.Default.AutoAwesome
        type.equals("openai", ignoreCase = true) -> Icons.Default.Psychology
        type.equals("claude", ignoreCase = true) -> Icons.Default.SmartToy
        type.equals("groq", ignoreCase = true) -> Icons.Default.Bolt
        type.equals("deepseek", ignoreCase = true) -> Icons.Default.TravelExplore
        type.equals("mistral", ignoreCase = true) -> Icons.Default.Air
        else -> Icons.Default.CloudQueue
    }
}
