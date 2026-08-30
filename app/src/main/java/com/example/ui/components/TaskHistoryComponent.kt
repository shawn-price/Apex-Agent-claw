package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.storage.AgentTaskExecution
import com.example.storage.TaskLogEntry
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryComponent(
    taskHistory: List<AgentTaskExecution>,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showQuickRunDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var taskToCancel by remember { mutableStateOf<AgentTaskExecution?>(null) }
    var selectedExecutionForLogs by remember { mutableStateOf<AgentTaskExecution?>(null) }

    // Keep bottom sheet task reactive to live updates
    val activeSelectedTask = remember(taskHistory, selectedExecutionForLogs) {
        if (selectedExecutionForLogs == null) null
        else taskHistory.find { it.id == selectedExecutionForLogs?.id } ?: selectedExecutionForLogs
    }

    val filteredList = remember(taskHistory, searchQuery, selectedFilter) {
        taskHistory.filter { task ->
            val matchesFilter = when (selectedFilter) {
                "PENDING" -> task.status.equals("pending", ignoreCase = true)
                "RUNNING" -> task.status.equals("running", ignoreCase = true)
                "ACTIVE" -> task.isCancellable
                "COMPLETED" -> task.status.equals("completed", ignoreCase = true) || task.status.equals("success", ignoreCase = true)
                "FAILED" -> task.status.equals("failed", ignoreCase = true) || task.status.equals("error", ignoreCase = true)
                "CANCELLED" -> task.status.equals("cancelled", ignoreCase = true)
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                task.title.contains(searchQuery, ignoreCase = true) ||
                        task.prompt.contains(searchQuery, ignoreCase = true) ||
                        (task.outputSummary?.contains(searchQuery, ignoreCase = true) == true) ||
                        (task.errorMessage?.contains(searchQuery, ignoreCase = true) == true) ||
                        (task.currentStep?.contains(searchQuery, ignoreCase = true) == true)
            }

            matchesFilter && matchesSearch
        }
    }

    val totalCount = taskHistory.size
    val completedCount = taskHistory.count { it.status.equals("completed", ignoreCase = true) || it.status.equals("success", ignoreCase = true) }
    val activeCount = taskHistory.count { it.isCancellable }
    val failedCount = taskHistory.count { it.status.equals("failed", ignoreCase = true) }
    val cancelledCount = taskHistory.count { it.status.equals("cancelled", ignoreCase = true) }
    val pendingCount = taskHistory.count { it.status.equals("pending", ignoreCase = true) }
    val runningCount = taskHistory.count { it.status.equals("running", ignoreCase = true) }

    val successRate = if (totalCount > 0) {
        ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt()
    } else 0

    Column(modifier = modifier.fillMaxSize()) {
        // Top Overview & Action Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Agent Task Execution Logs",
                            color = SlateTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeCount > 0) "$activeCount tasks actively running / pending" else "All automated tasks idle",
                            color = if (activeCount > 0) CyanGlow else SlateTextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { showQuickRunDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("quick_run_task_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Run", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (taskHistory.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearHistoryDialog = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("clear_task_history_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear History",
                                    tint = SlateTextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metric Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricMiniCard(
                        title = "Total",
                        value = totalCount.toString(),
                        accentColor = SlateTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Completed",
                        value = "$completedCount ($successRate%)",
                        accentColor = EmeraldPrimary,
                        modifier = Modifier.weight(1.3f)
                    )
                    MetricMiniCard(
                        title = "Active",
                        value = activeCount.toString(),
                        accentColor = if (activeCount > 0) CyanGlow else SlateTextTertiary,
                        isPulsing = activeCount > 0,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Failed",
                        value = failedCount.toString(),
                        accentColor = if (failedCount > 0) RubyRed else SlateTextTertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter task title, prompt, or logs...", color = SlateTextTertiary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = SlateTextTertiary, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = SlateTextTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianSurface,
                unfocusedContainerColor = ObsidianSurface,
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = ObsidianBorder,
                focusedTextColor = SlateTextPrimary,
                unfocusedTextColor = SlateTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("search_task_history_input")
        )

        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                StatusFilterChip(
                    label = "All ($totalCount)",
                    isSelected = selectedFilter == "ALL",
                    color = SlateTextPrimary,
                    tag = "filter_chip_all",
                    onClick = { selectedFilter = "ALL" }
                )
            }
            item {
                StatusFilterChip(
                    label = "Pending ($pendingCount)",
                    isSelected = selectedFilter == "PENDING",
                    color = AmberGold,
                    tag = "filter_chip_pending",
                    onClick = { selectedFilter = "PENDING" }
                )
            }
            item {
                StatusFilterChip(
                    label = "Running ($runningCount)",
                    isSelected = selectedFilter == "RUNNING",
                    color = CyanGlow,
                    tag = "filter_chip_running",
                    onClick = { selectedFilter = "RUNNING" }
                )
            }
            item {
                StatusFilterChip(
                    label = "Completed ($completedCount)",
                    isSelected = selectedFilter == "COMPLETED",
                    color = EmeraldPrimary,
                    tag = "filter_chip_completed",
                    onClick = { selectedFilter = "COMPLETED" }
                )
            }
            item {
                StatusFilterChip(
                    label = "Failed ($failedCount)",
                    isSelected = selectedFilter == "FAILED",
                    color = RubyRed,
                    tag = "filter_chip_failed",
                    onClick = { selectedFilter = "FAILED" }
                )
            }
            item {
                StatusFilterChip(
                    label = "Cancelled ($cancelledCount)",
                    isSelected = selectedFilter == "CANCELLED",
                    color = SlateTextTertiary,
                    tag = "filter_chip_cancelled",
                    onClick = { selectedFilter = "CANCELLED" }
                )
            }
        }

        // Task History LazyColumn (Clickable Items opening Bottom Sheet)
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        tint = SlateTextTertiary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedFilter != "ALL") "No Matching Tasks Found" else "No Task Execution History",
                        color = SlateTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedFilter != "ALL") "Try clearing filters or search term" else "Trigger a scheduled task or start a manual agent execution",
                        color = SlateTextTertiary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("task_history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)
            ) {
                items(
                    items = filteredList,
                    key = { it.id }
                ) { execution ->
                    TaskExecutionCard(
                        execution = execution,
                        onCardClick = { selectedExecutionForLogs = execution },
                        onCancelTask = { taskToCancel = execution },
                        onRetryTask = { viewModel.retryTaskExecution(execution.id) },
                        onDeleteTask = { viewModel.deleteTaskExecution(execution.id) }
                    )
                }
            }
        }
    }

    // Extended Logs & Timestamps Modal Bottom Sheet
    if (activeSelectedTask != null) {
        TaskDetailBottomSheet(
            execution = activeSelectedTask,
            onDismiss = { selectedExecutionForLogs = null },
            onCancelTask = {
                taskToCancel = activeSelectedTask
            },
            onRetryTask = {
                viewModel.retryTaskExecution(activeSelectedTask.id)
                selectedExecutionForLogs = null
            },
            onDeleteTask = {
                viewModel.deleteTaskExecution(activeSelectedTask.id)
                selectedExecutionForLogs = null
            }
        )
    }

    // Cancellation Confirmation Dialog
    if (taskToCancel != null) {
        val task = taskToCancel!!
        AlertDialog(
            onDismissRequest = { taskToCancel = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = RubyRed, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Agent Task?", color = SlateTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to cancel the execution of '${task.title}'?",
                        color = SlateTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The agent's active sub-processes, API calls, and tool operations will be immediately aborted.",
                        color = SlateTextTertiary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelTaskExecution(task.id)
                        taskToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyRed, contentColor = SlateTextPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_cancel_task_btn")
                ) {
                    Text("Abort Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToCancel = null }) {
                    Text("Keep Running", color = SlateTextSecondary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Clear History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = RubyRed, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Task History?", color = SlateTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "This will delete all past completed, failed, and cancelled task records from encrypted storage. Active tasks will be stopped.",
                    color = SlateTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearTaskHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyRed, contentColor = SlateTextPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = SlateTextSecondary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Quick Run Ad-Hoc Task Dialog
    if (showQuickRunDialog) {
        QuickRunTaskDialog(
            onDismiss = { showQuickRunDialog = false },
            onRunTask = { title, prompt, trigger, simulateDelayMs ->
                viewModel.enqueueAdHocTask(title, prompt, trigger, simulateDelayMs)
                showQuickRunDialog = false
            }
        )
    }
}

@Composable
fun TaskExecutionCard(
    execution: AgentTaskExecution,
    onCardClick: () -> Unit,
    onCancelTask: () -> Unit,
    onRetryTask: () -> Unit,
    onDeleteTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    val statusConfig = remember(execution.status) {
        when (execution.status.lowercase()) {
            "completed", "success" -> StatusUiConfig(
                label = "COMPLETED",
                color = EmeraldPrimary,
                bgColor = EmeraldDark.copy(alpha = 0.2f),
                icon = Icons.Default.CheckCircle
            )
            "running" -> StatusUiConfig(
                label = "RUNNING",
                color = CyanGlow,
                bgColor = CyanAccent.copy(alpha = 0.2f),
                icon = Icons.Default.Sync,
                isSpinning = true
            )
            "pending" -> StatusUiConfig(
                label = "PENDING",
                color = AmberGold,
                bgColor = AmberGold.copy(alpha = 0.18f),
                icon = Icons.Default.Schedule,
                isPulsing = true
            )
            "failed", "error" -> StatusUiConfig(
                label = "FAILED",
                color = RubyRed,
                bgColor = RubyRed.copy(alpha = 0.2f),
                icon = Icons.Default.ErrorOutline
            )
            "cancelled" -> StatusUiConfig(
                label = "CANCELLED",
                color = SlateTextTertiary,
                bgColor = ObsidianCardElevated,
                icon = Icons.Default.Block
            )
            else -> StatusUiConfig(
                label = execution.status.uppercase(),
                color = SlateTextSecondary,
                bgColor = ObsidianCardElevated,
                icon = Icons.Default.Info
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("task_history_item_${execution.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(
            1.dp,
            if (execution.status.equals("running", ignoreCase = true)) CyanAccent.copy(alpha = 0.6f)
            else if (execution.status.equals("pending", ignoreCase = true)) AmberGold.copy(alpha = 0.4f)
            else ObsidianBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Status Badge + Title + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge
                    Surface(
                        color = statusConfig.bgColor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, statusConfig.color.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (statusConfig.isSpinning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    color = statusConfig.color,
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = statusConfig.icon,
                                    contentDescription = null,
                                    tint = statusConfig.color,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusConfig.label,
                                color = statusConfig.color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = execution.title,
                        color = SlateTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Cancellation or Secondary Action Button
                if (execution.isCancellable) {
                    Button(
                        onClick = onCancelTask,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RubyRed.copy(alpha = 0.15f),
                            contentColor = RubyRedLight
                        ),
                        border = BorderStroke(1.dp, RubyRed.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("cancel_task_btn_${execution.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (execution.status.equals("failed", ignoreCase = true) || execution.status.equals("cancelled", ignoreCase = true)) {
                            IconButton(
                                onClick = onRetryTask,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("retry_task_btn_${execution.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Retry",
                                    tint = CyanGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDeleteTask,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("delete_task_history_btn_${execution.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = SlateTextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Current Step / Progress Bar for running or pending tasks
            if (execution.isCancellable && !execution.currentStep.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (execution.status.equals("running", ignoreCase = true)) {
                    LinearProgressIndicator(
                        progress = { execution.progressPercent.coerceIn(0.1f, 0.95f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = CyanGlow,
                        trackColor = ObsidianCardElevated,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (execution.status.equals("running", ignoreCase = true)) CyanGlow else AmberGold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = execution.currentStep,
                        color = if (execution.status.equals("running", ignoreCase = true)) CyanGlow else AmberGoldLight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Task Prompt Preview
            Text(
                text = execution.prompt,
                color = SlateTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Metadata Chips Bar with Bottom Sheet Click Hint
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Trigger Chip
                    Surface(
                        color = ObsidianCardElevated,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = execution.triggerType,
                            color = SlateTextTertiary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Model Chip
                    Surface(
                        color = ObsidianCardElevated,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = execution.modelUsed,
                            color = CyanAccent,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Duration Tag
                    if (execution.durationMs > 0) {
                        Text(
                            text = "${(execution.durationMs / 1000f).let { String.format(Locale.US, "%.1fs", it) }}",
                            color = SlateTextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Tools Count
                    if (execution.toolCallsCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${execution.toolCallsCount}",
                                color = EmeraldLight,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Timestamp & View Logs CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = dateFormat.format(Date(execution.startedAt)),
                        color = SlateTextTertiary,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View Extended Logs",
                        tint = CyanAccent.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    execution: AgentTaskExecution,
    onDismiss: () -> Unit,
    onCancelTask: () -> Unit,
    onRetryTask: () -> Unit,
    onDeleteTask: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val fullDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.getDefault()) }
    val timeWithMsFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    var selectedLogTab by remember { mutableStateOf("ALL") } // "ALL", "STEPS_TOOLS", "ERRORS"
    var logSearchQuery by remember { mutableStateOf("") }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val logs = remember(execution) { execution.getEffectiveLogs() }

    val filteredLogs = remember(logs, selectedLogTab, logSearchQuery) {
        logs.filter { entry ->
            val matchesTab = when (selectedLogTab) {
                "STEPS_TOOLS" -> entry.level.equals("STEP", ignoreCase = true) || entry.level.equals("TOOL", ignoreCase = true)
                "ERRORS" -> entry.level.equals("ERROR", ignoreCase = true) || entry.level.equals("WARN", ignoreCase = true)
                else -> true
            }
            val matchesSearch = if (logSearchQuery.isBlank()) true else {
                entry.message.contains(logSearchQuery, ignoreCase = true) ||
                        (entry.details?.contains(logSearchQuery, ignoreCase = true) == true) ||
                        entry.level.contains(logSearchQuery, ignoreCase = true)
            }
            matchesTab && matchesSearch
        }
    }

    val statusConfig = remember(execution.status) {
        when (execution.status.lowercase()) {
            "completed", "success" -> StatusUiConfig(
                label = "COMPLETED",
                color = EmeraldPrimary,
                bgColor = EmeraldDark.copy(alpha = 0.2f),
                icon = Icons.Default.CheckCircle
            )
            "running" -> StatusUiConfig(
                label = "RUNNING",
                color = CyanGlow,
                bgColor = CyanAccent.copy(alpha = 0.2f),
                icon = Icons.Default.Sync,
                isSpinning = true
            )
            "pending" -> StatusUiConfig(
                label = "PENDING",
                color = AmberGold,
                bgColor = AmberGold.copy(alpha = 0.18f),
                icon = Icons.Default.Schedule,
                isPulsing = true
            )
            "failed", "error" -> StatusUiConfig(
                label = "FAILED",
                color = RubyRed,
                bgColor = RubyRed.copy(alpha = 0.2f),
                icon = Icons.Default.ErrorOutline
            )
            "cancelled" -> StatusUiConfig(
                label = "CANCELLED",
                color = SlateTextTertiary,
                bgColor = ObsidianCardElevated,
                icon = Icons.Default.Block
            )
            else -> StatusUiConfig(
                label = execution.status.uppercase(),
                color = SlateTextSecondary,
                bgColor = ObsidianCardElevated,
                icon = Icons.Default.Info
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = SlateTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = ObsidianBorderLight
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("task_detail_bottom_sheet")
        ) {
            // Header Bar
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
                        color = statusConfig.bgColor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, statusConfig.color.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (statusConfig.isSpinning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = statusConfig.color,
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = statusConfig.icon,
                                    contentDescription = null,
                                    tint = statusConfig.color,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = statusConfig.label,
                                color = statusConfig.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = execution.title,
                        color = SlateTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Sheet",
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Sub-header: Task Execution ID with Copy Button & Tags
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(execution.id))
                        copiedNotice = "Execution ID copied"
                    }
                ) {
                    Text(
                        text = "ID: ${execution.id}",
                        color = SlateTextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy ID",
                        tint = SlateTextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = ObsidianCardElevated,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Text(
                            text = execution.triggerType,
                            color = SlateTextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = ObsidianCardElevated,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Text(
                            text = execution.modelUsed,
                            color = CyanAccent,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Copy feedback toast banner if triggered
            if (copiedNotice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = EmeraldDark.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(copiedNotice!!, color = EmeraldLight, fontSize = 11.sp)
                        }
                        IconButton(onClick = { copiedNotice = null }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            // Active Progress Banner if running
            if (execution.isCancellable && !execution.currentStep.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (execution.status.equals("running", ignoreCase = true)) CyanAccent.copy(alpha = 0.5f) else AmberGold.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (execution.status.equals("running", ignoreCase = true)) CyanGlow else AmberGold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (execution.status.equals("running", ignoreCase = true)) "Active Progress" else "Queue Status",
                                    color = if (execution.status.equals("running", ignoreCase = true)) CyanGlow else AmberGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (execution.status.equals("running", ignoreCase = true)) {
                                Text(
                                    text = "${(execution.progressPercent * 100).toInt()}%",
                                    color = CyanGlow,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (execution.status.equals("running", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { execution.progressPercent.coerceIn(0.1f, 0.95f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = CyanGlow,
                                trackColor = ObsidianCardElevated,
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = execution.currentStep,
                            color = SlateTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Extended Timestamps & Telemetry Breakdown Card
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Timing & Timestamps Breakdown",
                            color = SlateTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Started At Row
                    TimestampRowItem(
                        label = "Started At",
                        exactTimestamp = fullDateFormat.format(Date(execution.startedAt)),
                        relativeNote = "Epoch: ${execution.startedAt}ms"
                    )

                    HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Finished At Row
                    val finishedAtStr = if (execution.finishedAt != null) fullDateFormat.format(Date(execution.finishedAt))
                    else if (execution.isCancellable) "Execution in progress..."
                    else "Not recorded"

                    TimestampRowItem(
                        label = "Finished At",
                        exactTimestamp = finishedAtStr,
                        relativeNote = if (execution.finishedAt != null) "Epoch: ${execution.finishedAt}ms" else "Pending completion"
                    )

                    HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Duration / Latency Row
                    val durationSeconds = if (execution.durationMs > 0) String.format(Locale.US, "%.3fs", execution.durationMs / 1000f)
                    else if (execution.isCancellable) String.format(Locale.US, "%.1fs (active)", (System.currentTimeMillis() - execution.startedAt) / 1000f)
                    else "0.0s"

                    TimestampRowItem(
                        label = "Total Duration",
                        exactTimestamp = "$durationSeconds (${execution.durationMs} ms)",
                        relativeNote = if (execution.toolCallsCount > 0) "${execution.toolCallsCount} autonomous tool operations" else "Direct LLM reasoning"
                    )
                }
            }

            // Original Prompt Section
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Agent Instruction Prompt",
                                color = SlateTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${execution.prompt.length} chars)",
                                color = SlateTextTertiary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(execution.prompt))
                                copiedNotice = "Prompt copied to clipboard"
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Prompt", tint = SlateTextTertiary, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = CodeBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = execution.prompt,
                            color = SlateTextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // Output Response Summary (if completed)
            if (!execution.outputSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Agent Execution Result / Output",
                                    color = EmeraldPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(execution.outputSummary))
                                    copiedNotice = "Agent output copied to clipboard"
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Output", tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = CodeBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = execution.outputSummary,
                                color = SlateTextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Error Diagnostics Box (if failed)
            if (!execution.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = RubyRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RubyRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RubyRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fatal Error Diagnostics",
                                color = RubyRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = CodeBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, RubyRed.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = execution.errorMessage,
                                color = RubyRedLight,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Cancellation Audit Box (if cancelled)
            if (!execution.cancellationReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cancellation Audit Record",
                                color = AmberGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = execution.cancellationReason,
                            color = SlateTextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Extended Event Logs Timeline Section
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ListAlt, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Extended Lifecycle Logs",
                                color = SlateTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${logs.size} events)",
                                color = SlateTextTertiary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Copy all logs
                        TextButton(
                            onClick = {
                                val fullLogDump = buildString {
                                    appendLine("=== AGENT TASK EXECUTION LOG DUMP ===")
                                    appendLine("Task: ${execution.title}")
                                    appendLine("ID: ${execution.id}")
                                    appendLine("Status: ${execution.status}")
                                    appendLine("Started: ${fullDateFormat.format(Date(execution.startedAt))}")
                                    if (execution.finishedAt != null) appendLine("Finished: ${fullDateFormat.format(Date(execution.finishedAt))}")
                                    appendLine("Duration: ${execution.durationMs}ms")
                                    appendLine("Model: ${execution.modelUsed}")
                                    appendLine("Trigger: ${execution.triggerType}")
                                    appendLine("---------------------------------------")
                                    appendLine("CHRONOLOGICAL LOGS:")
                                    logs.forEach { entry ->
                                        appendLine("[${timeWithMsFormat.format(Date(entry.timestamp))}] [${entry.level}] ${entry.message}")
                                        if (!entry.details.isNullOrBlank()) {
                                            appendLine("   Details: ${entry.details}")
                                        }
                                    }
                                }
                                clipboardManager.setText(AnnotatedString(fullLogDump))
                                copiedNotice = "All execution logs copied"
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Logs", color = CyanAccent, fontSize = 11.sp)
                        }
                    }

                    // Log Category Tabs
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LogTabChip(
                            label = "All (${logs.size})",
                            isSelected = selectedLogTab == "ALL",
                            onClick = { selectedLogTab = "ALL" }
                        )
                        LogTabChip(
                            label = "Steps & Tools",
                            isSelected = selectedLogTab == "STEPS_TOOLS",
                            onClick = { selectedLogTab = "STEPS_TOOLS" }
                        )
                        LogTabChip(
                            label = "Diagnostics",
                            isSelected = selectedLogTab == "ERRORS",
                            onClick = { selectedLogTab = "ERRORS" }
                        )
                    }

                    // Log timeline events list
                    Spacer(modifier = Modifier.height(12.dp))
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = "No log events matching current filter.",
                            color = SlateTextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredLogs.forEachIndexed { index, entry ->
                                LogTimelineEntryRow(
                                    entry = entry,
                                    startTime = execution.startedAt,
                                    timeFormatter = timeWithMsFormat,
                                    isLast = index == filteredLogs.size - 1,
                                    onCopySnippet = { snippet ->
                                        clipboardManager.setText(AnnotatedString(snippet))
                                        copiedNotice = "Snippet copied"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Raw JSON Telemetry Export & Sheet Action Buttons
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy Raw JSON Button
                OutlinedButton(
                    onClick = {
                        val jsonStr = execution.toJson().toString(2)
                        clipboardManager.setText(AnnotatedString(jsonStr))
                        copiedNotice = "Raw JSON Telemetry copied"
                    },
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DataObject, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy JSON", color = SlateTextSecondary, fontSize = 12.sp)
                }

                // Retry / Re-run Button
                if (!execution.isCancellable) {
                    Button(
                        onClick = onRetryTask,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = ObsidianDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Again", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    // Abort Button
                    Button(
                        onClick = onCancelTask,
                        colors = ButtonDefaults.buttonColors(containerColor = RubyRed, contentColor = SlateTextPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Abort Task", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun TimestampRowItem(
    label: String,
    exactTimestamp: String,
    relativeNote: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, color = SlateTextTertiary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = exactTimestamp,
                color = SlateTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = relativeNote,
            color = SlateTextTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun LogTabChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) CyanAccent.copy(alpha = 0.2f) else ObsidianCardElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isSelected) CyanAccent else ObsidianBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (isSelected) CyanAccent else SlateTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LogTimelineEntryRow(
    entry: TaskLogEntry,
    startTime: Long,
    timeFormatter: SimpleDateFormat,
    isLast: Boolean,
    onCopySnippet: (String) -> Unit
) {
    val deltaMs = (entry.timestamp - startTime).coerceAtLeast(0L)
    val deltaFormatted = if (deltaMs < 1000) "+${deltaMs}ms" else "+${String.format(Locale.US, "%.2fs", deltaMs / 1000f)}"

    val (badgeColor, badgeBg) = when (entry.level.uppercase()) {
        "INFO" -> Pair(CyanAccent, CyanAccent.copy(alpha = 0.15f))
        "TOOL" -> Pair(EmeraldPrimary, EmeraldDark.copy(alpha = 0.2f))
        "STEP" -> Pair(AmberGold, AmberGold.copy(alpha = 0.15f))
        "WARN" -> Pair(AmberGold, AmberGold.copy(alpha = 0.25f))
        "ERROR" -> Pair(RubyRed, RubyRed.copy(alpha = 0.2f))
        "STATUS" -> Pair(PurpleAccent, PurpleAccent.copy(alpha = 0.15f))
        else -> Pair(SlateTextSecondary, ObsidianCardElevated)
    }

    Surface(
        color = ObsidianCardElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = entry.level.uppercase(),
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = timeFormatter.format(Date(entry.timestamp)),
                        color = SlateTextTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = deltaFormatted,
                    color = SlateTextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                color = SlateTextPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (!entry.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = CodeBackground,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopySnippet(entry.details) }
                ) {
                    Text(
                        text = entry.details,
                        color = SlateTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPulsing) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    color = SlateTextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun StatusFilterChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) color.copy(alpha = 0.2f) else ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isSelected) color else ObsidianBorder),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = label,
            color = if (isSelected) color else SlateTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun QuickRunTaskDialog(
    onDismiss: () -> Unit,
    onRunTask: (title: String, prompt: String, triggerType: String, simulateDelayMs: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("Manual Run") }
    var simulatePendingQueue by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trigger Ad-Hoc Agent Task",
                        color = SlateTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Label") },
                    placeholder = { Text("e.g. Web Research Agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Agent Instruction Prompt") },
                    placeholder = { Text("e.g. Search latest local LLM benchmarks and format results") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option to enqueue as Pending (allowing test of cancellation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { simulatePendingQueue = !simulatePendingQueue },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enqueue in Pending Queue first",
                            color = SlateTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Holds in Pending state for 8s so you can test individual task cancellation",
                            color = SlateTextTertiary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = simulatePendingQueue,
                        onCheckedChange = { simulatePendingQueue = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmberGold,
                            checkedTrackColor = AmberGold.copy(alpha = 0.4f)
                        )
                    )
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
                            if (prompt.isNotBlank()) {
                                onRunTask(
                                    title.ifBlank { "Ad-hoc Execution" },
                                    prompt,
                                    triggerType,
                                    if (simulatePendingQueue) 8000L else 0L
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("submit_quick_run_task")
                    ) {
                        Text("Start Execution", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class StatusUiConfig(
    val label: String,
    val color: Color,
    val bgColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isSpinning: Boolean = false,
    val isPulsing: Boolean = false
)
