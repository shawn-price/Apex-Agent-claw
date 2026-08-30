package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.storage.ScheduledTask
import com.example.storage.VoiceTaskEntity
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.QuickRunTaskDialog
import com.example.ui.components.TaskHistoryComponent
import com.example.ui.components.VoiceWaveformVisualizer
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: EverSync Planner & Timeline, 1: Background Executions Log, 2: Cron Automations
    var selectedCategory by remember { mutableStateOf("All Moments") }
    var selectedDateIndex by remember { mutableStateOf(4) } // Default to Fri 28 / active day
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showQuickRunDialog by remember { mutableStateOf(false) }
    var voiceInputText by remember { mutableStateOf("") }

    val activeHistoryCount = remember(uiState.taskHistory) {
        uiState.taskHistory.count { it.isCancellable }
    }

    val daysList = remember {
        listOf(
            Triple("MON", "24", false),
            Triple("TUE", "25", false),
            Triple("WED", "26", false),
            Triple("THU", "27", false),
            Triple("FRI", "28", true), // Active Today
            Triple("SAT", "29", false),
            Triple("SUN", "30", false)
        )
    }

    val categories = remember {
        listOf("All Moments", "⚡ Urgent", "💼 Work", "🧘 Personal", "🤖 Automations")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = EverSyncBackground,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = { showQuickRunDialog = true },
                    containerColor = EverSyncViolet,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("eversync_ai_run_fab")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Instant AI Action")
                }

                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = EverSyncCyan,
                    contentColor = EverSyncBackground,
                    shape = CircleShape,
                    modifier = Modifier.testTag("eversync_add_task_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Schedule New Moment")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- EVER SYNC TOP APP HEADER & METRICS ---
            EverSyncHeader(
                uiState = uiState,
                activeHistoryCount = activeHistoryCount,
                onOpenVoice = { viewModel.toggleVoiceInput() }
            )

            // --- INTERACTIVE DATE STRIP ("Meet Your Moments") ---
            EverSyncDateStrip(
                daysList = daysList,
                selectedIndex = selectedDateIndex,
                onSelectDate = { selectedDateIndex = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- AI VOICE & INSTANT INPUT BAR ---
            EverSyncVoiceInputBar(
                isListening = uiState.isVoiceActive,
                voiceInputText = voiceInputText,
                onTextChange = { voiceInputText = it },
                onMicClick = { viewModel.startVoiceForTaskParsing() },
                onSubmitTask = { prompt ->
                    if (prompt.isNotBlank()) {
                        viewModel.parseAndSaveVoiceTask(prompt)
                        voiceInputText = ""
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- NAVIGATION TABS (Planner Timeline vs Execution Logs vs Cron) ---
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = EverSyncSurface,
                contentColor = EverSyncCyan,
                divider = { HorizontalDivider(color = EverSyncBorder) },
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = EverSyncCyan,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("tab_moments_timeline"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ViewTimeline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == 0) EverSyncCyan else SlateTextTertiary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Moments Timeline",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) SlateTextPrimary else SlateTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab_execution_logs"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == 1) EverSyncViolet else SlateTextTertiary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Task Logs",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) SlateTextPrimary else SlateTextSecondary,
                                fontSize = 13.sp
                            )
                            if (activeHistoryCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = EverSyncCyan,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = activeHistoryCount.toString(),
                                        color = EverSyncBackground,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.testTag("tab_cron_schedules"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == 2) EverSyncOrange else SlateTextTertiary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Automations",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) SlateTextPrimary else SlateTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }

            // --- TAB CONTENT ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> EverSyncTimelineView(
                        uiState = uiState,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        viewModel = viewModel
                    )
                    1 -> TaskHistoryComponent(
                        taskHistory = uiState.taskHistory,
                        viewModel = viewModel
                    )
                    2 -> EverSyncAutomationsView(
                        uiState = uiState,
                        viewModel = viewModel,
                        onAddNewTask = { showAddTaskDialog = true }
                    )
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onAddTask = { title, prompt, interval, isRecurring ->
                viewModel.addTask(title, prompt, interval, isRecurring)
                showAddTaskDialog = false
            }
        )
    }

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

// --- EVER SYNC TOP APP HEADER ---
@Composable
fun EverSyncHeader(
    uiState: MainUiState,
    activeHistoryCount: Int,
    onOpenVoice: () -> Unit
) {
    Surface(
        color = EverSyncSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Avatar with Neon Ring
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(EverSyncCyan, EverSyncViolet, EverSyncCoral, EverSyncCyan)
                                )
                            )
                            .padding(2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_eversync_avatar),
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Good afternoon, Alex",
                                color = SlateTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "⚡",
                                fontSize = 15.sp
                            )
                        }

                        Text(
                            text = if (activeHistoryCount > 0) "$activeHistoryCount moments executing with AI" else "EverSync AI Engine • 88% Productivity Sync",
                            color = if (activeHistoryCount > 0) EverSyncCyanGlow else SlateTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Voice / AI Toggle Button
                IconButton(
                    onClick = onOpenVoice,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(EverSyncCardElevated)
                        .border(1.dp, EverSyncBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = if (uiState.isVoiceActive) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Voice Input",
                        tint = if (uiState.isVoiceActive) EverSyncCoral else EverSyncCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Productivity Metrics Card Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric 1: Sync Score Ring
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = EverSyncCard),
                    border = BorderStroke(1.dp, EverSyncBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Progress ring drawing
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp)
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = EverSyncBorder, style = Stroke(width = 4.dp.toPx()))
                                drawArc(
                                    color = EverSyncCyan,
                                    startAngle = -90f,
                                    sweepAngle = 316f, // 88%
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "88%",
                                color = EverSyncCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Sync Rate", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("Optimal", color = SlateTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Metric 2: Focus Blocks
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = EverSyncCard),
                    border = BorderStroke(1.dp, EverSyncBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EverSyncViolet.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = EverSyncLavender,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Focus Blocks", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("4 / 5 Done", color = SlateTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- INTERACTIVE DATE STRIP ---
@Composable
fun EverSyncDateStrip(
    daysList: List<Triple<String, String, Boolean>>,
    selectedIndex: Int,
    onSelectDate: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(daysList.size) { index ->
            val (dayName, dayNum, isToday) = daysList[index]
            val isSelected = index == selectedIndex

            val bgBrush = if (isSelected) {
                Brush.horizontalGradient(listOf(EverSyncCyan, EverSyncViolet))
            } else {
                Brush.horizontalGradient(listOf(EverSyncCard, EverSyncCard))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectDate(index) }
                    .border(
                        1.dp,
                        if (isSelected) EverSyncCyanGlow else EverSyncBorder,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .background(bgBrush)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayName,
                            color = if (isSelected) EverSyncBackground else SlateTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayNum,
                            color = if (isSelected) EverSyncBackground else SlateTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isToday) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) EverSyncBackground else EverSyncCyan)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- AI VOICE & INSTANT INPUT BAR ---
@Composable
fun EverSyncVoiceInputBar(
    isListening: Boolean,
    voiceInputText: String,
    onTextChange: (String) -> Unit,
    onMicClick: () -> Unit,
    onSubmitTask: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = EverSyncCardElevated),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(listOf(EverSyncCyan.copy(alpha = 0.5f), EverSyncViolet.copy(alpha = 0.5f)))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic button
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isListening) EverSyncCoral else EverSyncViolet.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.GraphicEq,
                        contentDescription = "Voice Input",
                        tint = if (isListening) Color.White else EverSyncCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                TextField(
                    value = voiceInputText,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = if (isListening) "Listening... Say your plan" else "Meet your moment... Speak or type plan",
                            color = SlateTextTertiary,
                            fontSize = 13.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = SlateTextPrimary,
                        unfocusedTextColor = SlateTextPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                if (voiceInputText.isNotBlank()) {
                    IconButton(
                        onClick = { onSubmitTask(voiceInputText) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EverSyncCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Submit",
                            tint = EverSyncBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Live Voice visualizer when active
        if (isListening) {
            Spacer(modifier = Modifier.height(6.dp))
            VoiceWaveformVisualizer(
                rmsAmplitude = 0.6f,
                onStopListening = onMicClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// --- EVER SYNC TIMELINE VIEW ---
@Composable
fun EverSyncTimelineView(
    uiState: MainUiState,
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    viewModel: MainViewModel
) {
    // Sample structured moments for the EverSync AI Planner
    var sampleMoments by remember {
        mutableStateOf(
            listOf(
                MomentItem("m1", "09:00 AM", "Morning Standup & AI Task Triage", "💼 Work", "HIGH", "30 mins", true, false),
                MomentItem("m2", "10:30 AM", "Deep Focus: Key Store Security Audit", "💼 Work", "HIGH", "90 mins", false, true),
                MomentItem("m3", "01:30 PM", "AI Agent Market Research Synthesis", "🤖 Automations", "MEDIUM", "45 mins", false, false),
                MomentItem("m4", "03:45 PM", "Diaphragmatic Breathing & Mindful Walk", "🧘 Personal", "LOW", "20 mins", true, false),
                MomentItem("m5", "05:00 PM", "Daily Workspace Backup & Multi-Channel Sync", "⚡ Urgent", "HIGH", "15 mins", false, false)
            )
        )
    }

    val filteredMoments = remember(sampleMoments, selectedCategory) {
        if (selectedCategory == "All Moments") sampleMoments
        else sampleMoments.filter { it.category.contains(selectedCategory.replace("⚡ ", "").replace("💼 ", "").replace("🧘 ", "").replace("🤖 ", "")) }
    }

    val filteredVoiceTasks = remember(uiState.voiceTasks, selectedCategory) {
        val catKey = selectedCategory.replace("⚡ ", "").replace("💼 ", "").replace("🧘 ", "").replace("🤖 ", "")
        if (selectedCategory == "All Moments") uiState.voiceTasks
        else uiState.voiceTasks.filter { it.category.contains(catKey, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Pills Filter
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) EverSyncViolet else EverSyncCard,
                    border = BorderStroke(1.dp, if (isSelected) EverSyncLavender else EverSyncBorder),
                    modifier = Modifier.clickable { onSelectCategory(category) }
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) Color.White else SlateTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Parsing indicator card
            if (uiState.isParsingVoiceTask) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EverSyncViolet.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, EverSyncCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_parsing_indicator")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = EverSyncCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Gemini AI Voice Parsing Active...",
                                    color = SlateTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Structuring task details & storing into Encrypted Room DB",
                                    color = EverSyncCyanGlow,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Room DB Voice Parsed Section
            if (filteredVoiceTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = EverSyncCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Voice Parsed Moments (Room DB Encrypted)",
                            color = SlateTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = EverSyncCyan.copy(alpha = 0.2f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${filteredVoiceTasks.size}",
                                color = EverSyncCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                items(filteredVoiceTasks) { voiceTask ->
                    VoiceTaskCard(
                        voiceTask = voiceTask,
                        onToggleComplete = { viewModel.toggleVoiceTaskComplete(voiceTask.id) },
                        onRunAI = {
                            viewModel.enqueueAdHocTask(voiceTask.title, voiceTask.prompt, voiceTask.category, 2000L)
                        },
                        onDelete = { viewModel.deleteVoiceTask(voiceTask.id) }
                    )
                }
            }

            // Planned Moments Timeline Header
            item {
                Text(
                    text = "Planned Moments Timeline",
                    color = SlateTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(filteredMoments) { moment ->
                MomentCard(
                    moment = moment,
                    onToggleComplete = { id ->
                        sampleMoments = sampleMoments.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it }
                    },
                    onRunAI = {
                        viewModel.enqueueAdHocTask(moment.title, "Execute AI action for ${moment.title}", moment.category, 2000L)
                    }
                )
            }
        }
    }
}

@Composable
fun VoiceTaskCard(
    voiceTask: VoiceTaskEntity,
    onToggleComplete: () -> Unit,
    onRunAI: () -> Unit,
    onDelete: () -> Unit
) {
    val urgencyColor = when (voiceTask.urgency.uppercase()) {
        "HIGH" -> EverSyncCoral
        "MEDIUM" -> EverSyncOrange
        else -> EverSyncMint
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EverSyncCardElevated),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(listOf(EverSyncCyan.copy(alpha = 0.6f), EverSyncViolet.copy(alpha = 0.6f)))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_task_card_${voiceTask.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header bar: Badges & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = EverSyncCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EverSyncCyan,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Room DB",
                                color = EverSyncCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = EverSyncViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = EverSyncLavender,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "AI Voice Parsed",
                                color = EverSyncLavender,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Task",
                        tint = SlateTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Checkbox and Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (voiceTask.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle Complete",
                        tint = if (voiceTask.isCompleted) EverSyncMint else EverSyncCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = voiceTask.title,
                        color = if (voiceTask.isCompleted) SlateTextTertiary else SlateTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(
                            textDecoration = if (voiceTask.isCompleted) TextDecoration.LineThrough else null
                        )
                    )

                    if (voiceTask.rawVoiceTranscript.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🎤 \"${voiceTask.rawVoiceTranscript}\"",
                            color = SlateTextSecondary,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer row: Scheduled Time, Duration, Urgency & Run AI Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = EverSyncCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = voiceTask.scheduledTime,
                        color = EverSyncCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${voiceTask.duration}",
                        color = SlateTextTertiary,
                        fontSize = 10.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = urgencyColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = voiceTask.urgency,
                            color = urgencyColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = onRunAI,
                        colors = ButtonDefaults.buttonColors(containerColor = EverSyncViolet),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run AI", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class MomentItem(
    val id: String,
    val time: String,
    val title: String,
    val category: String,
    val urgency: String, // HIGH, MEDIUM, LOW
    val duration: String,
    val isCompleted: Boolean,
    val isRunning: Boolean
)

@Composable
fun MomentCard(
    moment: MomentItem,
    onToggleComplete: (String) -> Unit,
    onRunAI: () -> Unit
) {
    val urgencyColor = when (moment.urgency) {
        "HIGH" -> EverSyncCoral
        "MEDIUM" -> EverSyncOrange
        else -> EverSyncMint
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EverSyncCard),
        border = BorderStroke(
            1.dp,
            if (moment.isRunning) EverSyncCyan else EverSyncBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = EverSyncCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = moment.time,
                        color = EverSyncCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${moment.duration}",
                        color = SlateTextTertiary,
                        fontSize = 11.sp
                    )
                }

                // Category & Urgency Badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = EverSyncCardElevated,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = moment.category,
                            color = SlateTextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = urgencyColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = moment.urgency,
                            color = urgencyColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Title & Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { onToggleComplete(moment.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (moment.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = "Toggle Complete",
                            tint = if (moment.isCompleted) EverSyncCyan else SlateTextTertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = moment.title,
                        color = if (moment.isCompleted) SlateTextTertiary else SlateTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (moment.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                }

                // Quick AI Trigger Button
                IconButton(
                    onClick = onRunAI,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(EverSyncViolet.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Run with AI",
                        tint = EverSyncLavender,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (moment.isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    color = EverSyncCyan,
                    trackColor = EverSyncBorder,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

// --- EVER SYNC AUTOMATIONS VIEW ---
@Composable
fun EverSyncAutomationsView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onAddNewTask: () -> Unit
) {
    if (uiState.tasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = SlateTextTertiary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Active Automations",
                    color = SlateTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Create automated background cron jobs & AI moment triggers",
                    color = SlateTextTertiary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddNewTask,
                    colors = ButtonDefaults.buttonColors(containerColor = EverSyncCyan, contentColor = EverSyncBackground)
                ) {
                    Text("+ Create Automation")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(uiState.tasks) { task ->
                TaskCard(
                    task = task,
                    onToggle = { enabled -> viewModel.toggleTask(task.id, enabled) },
                    onRunNow = { viewModel.runTaskNow(task.id) },
                    onDelete = { viewModel.deleteTask(task.id) }
                )
            }
        }
    }
}

@Composable
fun TaskCard(
    task: ScheduledTask,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = EverSyncCard),
        border = BorderStroke(1.dp, EverSyncBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (task.isEnabled) EverSyncCyan else SlateTextTertiary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        color = SlateTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = task.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EverSyncCyan,
                        checkedTrackColor = EverSyncViolet
                    ),
                    modifier = Modifier.testTag("task_switch_${task.id}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = task.prompt,
                color = SlateTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = EverSyncCardElevated,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (task.isRecurring) "Every ${task.intervalMinutes}m" else "One-shot (${task.intervalMinutes}m)",
                            color = EverSyncCyanGlow,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    if (task.lastRunMillis > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Last: ${dateFormat.format(Date(task.lastRunMillis))}",
                            color = SlateTextTertiary,
                            fontSize = 10.sp
                        )
                    }
                }

                Row {
                    // Run Now Button
                    IconButton(
                        onClick = onRunNow,
                        modifier = Modifier.size(32.dp).testTag("run_task_now_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run Now",
                            tint = EverSyncCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp).testTag("delete_task_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = EverSyncCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!task.lastOutput.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CodeBackground,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Last Output: ${task.lastOutput}",
                        color = SlateTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAddTask: (title: String, prompt: String, interval: Int, isRecurring: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var intervalMinutes by remember { mutableStateOf("60") }
    var isRecurring by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = EverSyncSurface),
            border = BorderStroke(1.dp, EverSyncBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "New EverSync Moment Automation",
                    color = SlateTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    placeholder = { Text("e.g. Daily Standup AI Summary") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("AI Prompt to Execute") },
                    placeholder = { Text("e.g. Synthesize today's emails & write report to workspace") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = intervalMinutes,
                    onValueChange = { intervalMinutes = it },
                    label = { Text("Interval (Minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recurring Execution",
                        color = SlateTextPrimary,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = EverSyncCyan)
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
                            val interval = intervalMinutes.toIntOrNull() ?: 60
                            if (title.isNotBlank() && prompt.isNotBlank()) {
                                onAddTask(title, prompt, interval, isRecurring)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EverSyncCyan, contentColor = EverSyncBackground)
                    ) {
                        Text("Schedule Automation")
                    }
                }
            }
        }
    }
}
