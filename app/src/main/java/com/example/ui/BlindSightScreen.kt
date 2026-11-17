package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppState
import com.example.service.UpdateDownloadState
import com.example.ui.components.AllyFaceOrb
import com.example.ui.components.MemoryBankDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.UpdateDialog
import com.example.ui.theme.AllyAmber
import com.example.ui.theme.AllyBackground
import com.example.ui.theme.AllyBorder
import com.example.ui.theme.AllyCyan
import com.example.ui.theme.AllySurface
import com.example.ui.theme.AllyTextPrimary
import com.example.ui.theme.AllyTextSecondary
import com.example.ui.theme.AllyWarning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BlindSight "Ally" Experience: Serene, accessible, and expressive conversational companion.
 *
 * Full tactile interaction designed specifically for blind & low-vision ergonomics:
 * - Single tap anywhere on lower 60%: Toggle voice listening / speak to BlindSight
 * - Double tap anywhere: Instantly capture & describe scene
 * - Two-finger tap anywhere: Instantly silence speech
 */
@Composable
fun BlindSightScreen(
    viewModel: BlindSightViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            // Global Gesture Detector for Blind Ergonomics:
            // 2 fingers = Silence speech
            // Double tap = Describe camera scene
            // Single tap = Talk / Mic
            .pointerInput(Unit) {
                var lastTapTime = 0L
                var singleTapJob: Job? = null

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressedPointers = event.changes.filter { it.pressed }
                        val downChanges = event.changes.filter { it.changedToDown() }

                        // 1. Two-finger tap: instantly silence speech
                        if (pressedPointers.size >= 2 || downChanges.size >= 2) {
                            singleTapJob?.cancel()
                            lastTapTime = 0L
                            event.changes.forEach { it.consume() }
                            viewModel.stopSpeaking()
                        } else if (downChanges.size == 1) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 380L) {
                                // 2. Double-tap detected: describe scene immediately
                                singleTapJob?.cancel()
                                lastTapTime = 0L
                                event.changes.forEach { it.consume() }
                                viewModel.describeScene()
                            } else {
                                // Potential single tap: launch delayed job
                                lastTapTime = now
                                singleTapJob?.cancel()
                                singleTapJob = coroutineScope.launch {
                                    delay(340L)
                                    if (lastTapTime == now) {
                                        viewModel.onTactileSingleTap()
                                    }
                                }
                            }
                        }
                    }
                }
            },
        containerColor = AllyBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AllyBackground),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =================================================================
            // 1. TOP HEADER: Subtle Battery/GPS Status Pill & Language Badge
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtle Battery / GPS Status Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AllySurface)
                        .border(1.dp, AllyBorder, RoundedCornerShape(20.dp))
                        .clickable { viewModel.speakHardwareStatus() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Battery and GPS status. Tap to hear hardware report."
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = AllyCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "READY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AllyTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = AllyTextSecondary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AllyAmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (telemetry.isHardwareConnected) "RADAR ACTIVE" else "GPS READY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AllyTextSecondary
                    )
                }

                // Right Actions: Stop Button (if speaking), Language Badge, Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stop Speech button visible when speaking
                    AnimatedVisibility(
                        visible = uiState.appState == AppState.SPEAKING,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = { viewModel.stopSpeaking() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AllyWarning.copy(alpha = 0.18f))
                                .border(1.dp, AllyWarning, CircleShape)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Stop speaking."
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Silence audio",
                                tint = AllyWarning,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Active Language Badge (Strictly locked to 4 languages: EN, HI, PA, BN)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AllySurface)
                            .border(1.dp, AllyBorder, RoundedCornerShape(20.dp))
                            .clickable { viewModel.cycleLanguage() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Current language: ${uiState.language.label}. Tap to switch language."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.language.shortCode,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AllyAmber,
                            letterSpacing = 1.sp
                        )
                    }

                    // Quick Settings Icon
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AllySurface)
                            .border(1.dp, AllyBorder, CircleShape)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Open Settings."
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AllyTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // =================================================================
            // 2. THE LIVING "ALLY" FACE / MORPHING EXPRESSION ORB (Top 40%)
            // =================================================================
            Spacer(modifier = Modifier.weight(0.12f))

            AllyFaceOrb(
                appState = uiState.appState,
                onClick = { viewModel.onTactileSingleTap() },
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ally face presence. Current state: ${uiState.appState.label}. Tap to talk."
                    }
            )

            Spacer(modifier = Modifier.weight(0.12f))

            // =================================================================
            // 3. FULL-SCREEN TACTILE CANVAS & LARGE ACCESSIBILITY TYPOGRAPHY (Lower 60%)
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.76f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(AllySurface.copy(alpha = 0.5f))
                    .border(1.dp, AllyBorder.copy(alpha = 0.6f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Conversational State Headline (28sp–32sp Inter / SF Pro)
                val stateHeadline = when (uiState.appState) {
                    AppState.IDLE -> "Tap Anywhere to Talk"
                    AppState.WAKE_LISTENING, AppState.COMMAND_LISTENING -> "Listening to you…"
                    AppState.THINKING -> "Thinking…"
                    AppState.SPEAKING -> "BlindSight Guiding…"
                    AppState.CONFIRMING -> "Understood"
                    AppState.CAUTION -> "Obstacle Warning"
                }

                val headlineColor = when (uiState.appState) {
                    AppState.CAUTION -> AllyWarning
                    AppState.CONFIRMING -> AllyAmber
                    AppState.WAKE_LISTENING, AppState.COMMAND_LISTENING -> AllyCyan
                    else -> AllyTextPrimary
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stateHeadline,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = headlineColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Prominent Spoken Text / Assistant Response (24sp–26sp, high contrast #FFFFFF)
                    val primaryGuidanceText = if (uiState.statusText.isNotBlank()) {
                        uiState.statusText
                    } else {
                        "Directly ahead is clear. Proceed with confidence."
                    }

                    Text(
                        text = "\"$primaryGuidanceText\"",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = AllyTextPrimary,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )
                }

                // Blind Ergonomics Gesture Anchors
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AllyBackground)
                            .border(1.dp, AllyBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = AllyAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Double-tap: Describe Scene  •  2 fingers: Silence",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AllyTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    // Modal Dialogs
    if (showSettingsDialog) {
        SettingsDialog(
            currentLanguage = uiState.language,
            onLanguageChange = { viewModel.setLanguage(it) },
            currentVoicePersona = uiState.voicePersona,
            onVoicePersonaChange = { viewModel.setVoicePersona(it) },
            currentTextSize = uiState.textSizeMultiplier,
            onTextSizeChange = { viewModel.setTextSize(it) },
            highContrastYellow = uiState.highContrastYellow,
            onToggleHighContrast = { viewModel.toggleHighContrastYellow() },
            isPhoneCameraFallbackEnabled = uiState.isPhoneCameraFallbackEnabled,
            onTogglePhoneCameraFallback = { viewModel.togglePhoneCameraFallback() },
            currentGatewayUrl = uiState.gatewayUrl,
            onGatewayUrlChange = { viewModel.setGatewayUrl(it) },
            onCheckUpdates = { viewModel.checkForUpdates(silent = false) },
            onReadHelp = { viewModel.speakHardwareStatus() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showMemoryDialog) {
        val facts by viewModel.facts.collectAsState()
        MemoryBankDialog(
            facts = facts,
            onAddFact = { viewModel.addMemoryFact(it) },
            onForgetFact = { viewModel.removeMemoryFact(it) },
            onClearAll = { viewModel.clearAllMemories() },
            onDismiss = { showMemoryDialog = false }
        )
    }

    if (updateState is UpdateDownloadState.Available) {
        UpdateDialog(
            updateState = updateState,
            onStartDownload = { info ->
                viewModel.startUpdateDownload(info)
            },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }
}
