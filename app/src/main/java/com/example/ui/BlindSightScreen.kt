package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AppState
import com.example.service.UpdateDownloadState
import com.example.ui.components.AllyFaceOrb
import com.example.ui.components.ConversationHistoryView
import com.example.ui.components.MemoryBankDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.UpdateDialog
import com.example.ui.theme.AllyAmber
import com.example.ui.theme.AllyCyan
import com.example.ui.theme.AllyTextPrimary
import com.example.ui.theme.AllyTextSecondary
import com.example.ui.theme.AllyWarning
import com.example.ui.theme.DeepObsidianCanvas
import com.example.ui.theme.LiquidGlassBackground
import com.example.ui.theme.LiquidGlassBorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BlindSight Liquid Glass Accessible Experience.
 *
 * Full tactile interaction designed specifically for blind & low-vision ergonomics:
 * - Single tap anywhere on lower glass: Toggle voice listening / speak to BlindSight
 * - Double tap anywhere: Instantly capture & describe scene
 * - Two-finger tap anywhere: Instantly silence speech
 *
 * Features:
 * - Top 40%: Viewport HUD with subtle glowing liquid glass border showing live upright camera
 * - Center: Organic fluid audio-reactive liquid orb/membrane
 * - Bottom 50%: Massive tactile touch surface with subtle haptic feedback
 * - Zero emojis throughout UI and spoken output
 */
@Composable
fun BlindSightScreen(
    viewModel: BlindSightViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val history by viewModel.history.collectAsState()

    var showSettingsDialogLocal by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val latestBitmap = remember(uiState.latestFrameBytes) {
        uiState.latestFrameBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

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
        containerColor = DeepObsidianCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DeepObsidianCanvas),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =================================================================
            // 1. TOP HEADER: Subtle Battery/GPS Status Pill & Language Badge
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtle Battery / GPS Status Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(LiquidGlassBackground)
                        .border(1.dp, LiquidGlassBorder, RoundedCornerShape(20.dp))
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
                            .background(LiquidGlassBackground)
                            .border(1.dp, LiquidGlassBorder, RoundedCornerShape(20.dp))
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
                        onClick = { showSettingsDialogLocal = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(LiquidGlassBackground)
                            .border(1.dp, LiquidGlassBorder, CircleShape)
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
            // 2. TOP 40%: VIEWPORT HUD WITH GLOWING LIQUID GLASS BORDER
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(LiquidGlassBackground)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (latestBitmap != null) {
                    Image(
                        bitmap = latestBitmap,
                        contentDescription = "Live camera view upright snapshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = AllyCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.isCameraScanning) "SCANNING SCENE..." else "VISION VIEWPORT READY",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AllyCyan,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Double-tap to describe immediate path ahead",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = AllyTextSecondary
                        )
                    }
                }

                // HUD Ribbon
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeepObsidianCanvas.copy(alpha = 0.8f))
                        .border(1.dp, LiquidGlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (latestBitmap != null) "FRAME CAPTURED" else "UPRIGHT HUD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AllyCyan,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // =================================================================
            // 3. CENTER: ORGANIC FLUID AUDIO-REACTIVE LIQUID ORB (Center)
            // =================================================================
            Spacer(modifier = Modifier.height(8.dp))

            AllyFaceOrb(
                appState = uiState.appState,
                onClick = { viewModel.onTactileSingleTap() },
                sizeDp = 210.dp,
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ally fluid presence. Current state: ${uiState.appState.label}. Tap to talk."
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // =================================================================
            // 4. BOTTOM 50%: FULL-SCREEN TACTILE CANVAS & ACCESSIBLE TYPOGRAPHY
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(LiquidGlassBackground)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Conversational State Headline (28sp–32sp Inter / SF Pro)
                val stateHeadline = when (uiState.appState) {
                    AppState.IDLE -> "Tap Lower Glass to Talk"
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
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = headlineColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Prominent Spoken Text / Assistant Response (22sp–24sp, high contrast #FFFFFF)
                    val primaryGuidanceText = if (uiState.statusText.isNotBlank()) {
                        uiState.statusText
                    } else {
                        "Directly ahead is clear. Proceed with confidence."
                    }

                    Text(
                        text = "\"$primaryGuidanceText\"",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = AllyTextPrimary,
                        lineHeight = 28.sp,
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
                            .background(DeepObsidianCanvas.copy(alpha = 0.8f))
                            .border(1.dp, LiquidGlassBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = AllyAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Single-tap: Speak  •  Double-tap: Describe  •  2 fingers: Silence",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AllyTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }

    // Modal Dialogs
    val isSettingsOpen = showSettingsDialogLocal || uiState.showSettingsDialog
    if (isSettingsOpen) {
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
            onDismiss = {
                showSettingsDialogLocal = false
                viewModel.setShowSettingsDialog(false)
            }
        )
    }

    if (uiState.showHistoryDialog) {
        Dialog(onDismissRequest = { viewModel.setShowHistoryDialog(false) }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DeepObsidianCanvas),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Conversation History",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = { viewModel.setShowHistoryDialog(false) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close history",
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ConversationHistoryView(
                        messages = history,
                        onClearHistory = { viewModel.clearConversationHistory() },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.setShowHistoryDialog(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = AllyCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
