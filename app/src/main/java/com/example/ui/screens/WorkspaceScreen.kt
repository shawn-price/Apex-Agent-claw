package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.WorkspaceFileInfo
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.CodeBlockWithRun
import com.example.ui.components.FileViewerModal
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Zero-DB File Inspector, 1: Code Sandbox Runner, 2: Backups
    var inspectedFile by remember { mutableStateOf<WorkspaceFileInfo?>(null) }
    var rawEncryptedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var decryptedContent by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Sandbox state
    var sandboxCode by remember { mutableStateOf("print('Hello from OpenClaw Python Sandbox!')\nx = 10 * 5\nprint('Computed Result:', x)") }
    var sandboxLang by remember { mutableStateOf("python") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Zero-DB & Workspace",
                            color = SlateTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AES-256-GCM Hardware Encrypted Filesystem",
                            color = CyanGlow,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshWorkspaceFiles() },
                        modifier = Modifier.testTag("refresh_files_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = EmeraldPrimary)
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
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianSurface,
                contentColor = EmeraldPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = EmeraldPrimary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Encrypted Files", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Code Sandbox", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Backups", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Zero-DB Files Explorer
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Zero-Database Encrypted Architecture", color = SlateTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("All files stored under /nanobot/ with monthly message partitioning and AES-256-GCM. Tap any file to inspect encrypted bytes vs decrypted JSON.", color = SlateTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        items(uiState.internalEncryptedFiles) { fileInfo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            inspectedFile = fileInfo
                                            rawEncryptedBytes = viewModel.getRawEncryptedFile(fileInfo)
                                            decryptedContent = viewModel.getDecryptedFile(fileInfo)
                                        }
                                    }
                                    .testTag("file_item_${fileInfo.relativePath}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = ObsidianCard)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (fileInfo.isEncrypted) Icons.Default.Lock else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (fileInfo.isEncrypted) EmeraldPrimary else CyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = fileInfo.relativePath,
                                                color = SlateTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = if (fileInfo.isEncrypted) "AES-256-GCM Encrypted" else "Plaintext Workspace File",
                                                color = SlateTextTertiary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${fileInfo.sizeBytes} B",
                                        color = CyanGlow,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Sandbox Runner
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Interactive Execution Sandbox", color = SlateTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row {
                                FilterChip(
                                    selected = sandboxLang == "python",
                                    onClick = { sandboxLang = "python" },
                                    label = { Text("Python", fontSize = 11.sp) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = sandboxLang == "javascript",
                                    onClick = { sandboxLang = "javascript" },
                                    label = { Text("JS", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = sandboxCode,
                            onValueChange = { sandboxCode = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = SlateTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CodeBackground,
                                unfocusedContainerColor = CodeBackground,
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = ObsidianBorder
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.executeCodeSnippet(sandboxCode, sandboxLang)
                                viewModel.setScreen(0) // Switch to chat to see execution
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianDark)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run & Inspect Output in Agent Chat", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                2 -> {
                    // Backups
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Encrypted Backup Archives", color = SlateTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Creates a timestamped snapshot of all conversations, tasks, and LLM configurations with SHA-256 hash verification.", color = SlateTextSecondary, fontSize = 12.sp)

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = { viewModel.createBackup() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianDark)
                                    ) {
                                        Icon(Icons.Default.Backup, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Encrypted Backup Archive", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        val backupFiles = uiState.internalEncryptedFiles.filter { it.relativePath.contains("backups/") }
                        if (backupFiles.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No backups created yet.", color = SlateTextTertiary, fontSize = 13.sp)
                                }
                            }
                        } else {
                            items(backupFiles) { bFile ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = ObsidianCard)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(bFile.relativePath.removePrefix("backups/"), color = SlateTextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                            Text("${bFile.sizeBytes} bytes", color = CyanGlow, fontSize = 11.sp)
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                val file = File(viewModel.getApplication<android.app.Application>().filesDir, "nanobot/${bFile.relativePath}")
                                                viewModel.restoreBackup(file)
                                            }
                                        ) {
                                            Text("Restore", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (inspectedFile != null) {
        FileViewerModal(
            fileName = inspectedFile!!.relativePath,
            rawEncryptedBytes = rawEncryptedBytes,
            decryptedContent = decryptedContent,
            onDismiss = {
                inspectedFile = null
                rawEncryptedBytes = null
                decryptedContent = null
            }
        )
    }
}
