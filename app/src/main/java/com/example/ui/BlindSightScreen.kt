package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BlindSightTopBar
import com.example.ui.components.ConversationHistoryView
import com.example.ui.components.GiantMicButton
import com.example.ui.components.MemoryBankDialog
import com.example.ui.components.ObstacleRadarView
import com.example.ui.components.SettingsDialog
import com.example.ui.components.VisionHelperView
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightBackground
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary
import com.example.ui.theme.BlindSightYellow

@Composable
fun BlindSightScreen(
    viewModel: BlindSightViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val history by viewModel.history.collectAsState()
    val facts by viewModel.facts.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var typedPrompt by remember { mutableStateOf("") }

    val activePrimaryColor = if (uiState.highContrastYellow) BlindSightYellow else BlindSightAmber

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BlindSightBackground,
        topBar = {
            BlindSightTopBar(
                language = uiState.language,
                onStopClick = { viewModel.stopSpeaking() },
                onSettingsClick = { showSettingsDialog = true }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = BlindSightCard,
                tonalElevation = 8.dp
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = uiState.selectedTab == tab
                    val tabIcon = when (tab) {
                        AppTab.COMPANION -> Icons.Default.Mic
                        AppTab.RADAR -> Icons.Default.Security
                        AppTab.VISION -> Icons.Default.CameraAlt
                        AppTab.MEMORIES -> Icons.Default.Bookmark
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = activePrimaryColor,
                            indicatorColor = activePrimaryColor,
                            unselectedIconColor = BlindSightTextMuted,
                            unselectedTextColor = BlindSightTextMuted
                        ),
                        modifier = Modifier.semantics {
                            role = Role.Tab
                            contentDescription = "${tab.title} tab. ${if (isSelected) "Active" else "Double tap to switch"}"
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (uiState.selectedTab) {
                AppTab.COMPANION -> {
                    // Giant Mic Button
                    Spacer(modifier = Modifier.height(10.dp))
                    GiantMicButton(
                        appState = uiState.appState,
                        statusText = uiState.statusText,
                        onClick = { viewModel.onGiantMicClick() }
                    )

                    // Quick Command Chips for Blind Users
                    QuickCommandRow(
                        onCommand = { viewModel.onSpeechResult(it) }
                    )

                    // Text fallback entry for typing
                    TextPromptInputRow(
                        text = typedPrompt,
                        onTextChange = { typedPrompt = it },
                        onSubmit = {
                            if (typedPrompt.isNotBlank()) {
                                viewModel.onSpeechResult(typedPrompt)
                                typedPrompt = ""
                            }
                        }
                    )

                    // Conversation history
                    ConversationHistoryView(
                        messages = history,
                        onClearHistory = { viewModel.clearConversationHistory() }
                    )
                }

                AppTab.RADAR -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    ObstacleRadarView(
                        telemetry = telemetry,
                        isAutoSweeping = uiState.isAutoSweeping,
                        onDistanceChange = { l, c, r -> viewModel.updateSensorDistance(l, c, r) },
                        onToggleAutoSweep = { viewModel.toggleAutoSweep() }
                    )
                }

                AppTab.VISION -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    VisionHelperView(
                        isScanning = uiState.isCameraScanning,
                        onDescribeScene = { viewModel.describeScene() }
                    )
                }

                AppTab.MEMORIES -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(BlindSightCard)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Memory Bank (${facts.size}/20 facts)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlindSightTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Teach facts like \"remember that my meeting room is on the 3rd floor\". They stay stored on-device.",
                            fontSize = 13.sp,
                            color = BlindSightTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showMemoryDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = activePrimaryColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Manage Memories",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentLanguage = uiState.language,
            onLanguageChange = { viewModel.setLanguage(it) },
            currentMode = uiState.listenMode,
            onModeChange = { viewModel.setListenMode(it) },
            currentTextSize = uiState.textSizeMultiplier,
            onTextSizeChange = { viewModel.setTextSize(it) },
            highContrastYellow = uiState.highContrastYellow,
            onToggleHighContrast = { viewModel.toggleHighContrastYellow() },
            onReadHelp = {
                viewModel.onSpeechResult("help")
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showMemoryDialog) {
        MemoryBankDialog(
            facts = facts,
            onAddFact = { viewModel.addManualFact(it) },
            onForgetFact = { viewModel.forgetFact(it) },
            onClearAll = { viewModel.clearAllFacts() },
            onDismiss = { showMemoryDialog = false }
        )
    }
}

@Composable
private fun QuickCommandRow(
    onCommand: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Quick Spoken Commands",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = BlindSightTextMuted,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val commands = listOf("describe" to "Describe Scene", "read status" to "Read Status", "help" to "Help")
            commands.forEach { (cmd, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .semantics {
                            role = Role.Button
                            contentDescription = "Quick action: $label"
                        }
                        .padding(vertical = 10.dp)
                        .clickable { onCommand(cmd) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun TextPromptInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Or type question here…", color = BlindSightTextMuted) },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BlindSightAmber,
                unfocusedBorderColor = BlindSightCardEdge,
                focusedTextColor = BlindSightTextPrimary,
                unfocusedTextColor = BlindSightTextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(
            onClick = onSubmit,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BlindSightAmber)
                .semantics {
                    role = Role.Button
                    contentDescription = "Submit question to BlindSight."
                }
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = null,
                tint = Color.Black
            )
        }
    }
}
