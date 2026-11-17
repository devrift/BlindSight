package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.AlertWarningRed
import com.example.ui.theme.DeepOceanicObsidian
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.LiquidGlassBorder
import com.example.ui.theme.MidnightBlueGlass
import com.example.ui.theme.PorcelainCanvas
import com.example.ui.theme.PorcelainGlass
import com.example.ui.theme.SlateTextDark
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate
import com.example.ui.theme.VividCyan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BlindSight True Liquid Glass Accessibility Experience.
 *
 * Designed with authentic VisionOS multi-layer liquid depth, animated Ally Smiley face,
 * and unified Electric Cobalt / Royal Blue design system.
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

    val isDark = uiState.isDarkMode
    val canvasBg = if (isDark) DeepOceanicObsidian else PorcelainCanvas
    val glassBg = if (isDark) MidnightBlueGlass else PorcelainGlass
    val textPrimary = if (isDark) TextPrimaryWhite else SlateTextDark
    val textSecondary = if (isDark) TextSecondarySlate else SlateTextMuted

    val isGlassesConnected = uiState.isHardwareGlassesConnected || telemetry.isHardwareConnected

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
            // Single tap = Talk / Mic / Resume
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
        containerColor = canvasBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(canvasBg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =================================================================
            // 1. TOP HEADER: Functional Connection Chips & Animated Theme Switch
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Functional Telemetry Chips ONLY (Glasses & Active Lens)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Glasses Connection Chip
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isGlassesConnected) ElectricCobalt.copy(alpha = 0.85f) else glassBg)
                            .border(1.dp, if (isGlassesConnected) VividCyan else LiquidGlassBorder, RoundedCornerShape(20.dp))
                            .clickable { viewModel.speakHardwareStatus() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = if (isGlassesConnected) "Glasses connected." else "Glasses disconnected. Using phone fallback."
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isGlassesConnected) VividCyan else Color(0xFF64748B))
                        )
                        Text(
                            text = if (isGlassesConnected) "Glasses Connected" else "Glasses Disconnected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGlassesConnected) Color.White else textSecondary,
                            letterSpacing = 0.3.sp
                        )
                    }

                    // Active Sensor Lens Chip (Tap to Flip Camera)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(glassBg)
                            .border(1.dp, LiquidGlassBorder, RoundedCornerShape(20.dp))
                            .clickable { viewModel.onGiantMicClick() }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Active camera lens."
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = null,
                            tint = ElectricCobalt,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Back Camera",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )
                    }
                }

                // Right Actions: Mic Indicator Dot, Stop Speech, Spring Theme Switch, Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Subtle Audio Level / Listening Dot
                    val isListening = uiState.appState == AppState.COMMAND_LISTENING || uiState.appState == AppState.WAKE_LISTENING
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isListening) VividCyan else Color(0xFF334155))
                    )

                    // Stop Speech button visible when speaking
                    AnimatedVisibility(
                        visible = uiState.appState == AppState.SPEAKING,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = { viewModel.stopSpeaking() },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AlertWarningRed.copy(alpha = 0.2f))
                                .border(1.dp, AlertWarningRed, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Silence audio",
                                tint = AlertWarningRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Animated Spring Physics Theme Switch
                    val thumbOffset by animateDpAsState(
                        targetValue = if (isDark) 18.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "topThemeSwitch"
                    )

                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isDark) Color(0xFF0F2752) else Color(0xFFC7DEFF))
                            .border(1.dp, LiquidGlassBorder, RoundedCornerShape(13.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.toggleDarkMode() }
                            )
                            .padding(2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = thumbOffset)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(ElectricCobalt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Toggle Dark/Light theme",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Settings Button
                    IconButton(
                        onClick = { showSettingsDialogLocal = true },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(glassBg)
                            .border(1.dp, LiquidGlassBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // =================================================================
            // 2. TOP 40%: VIEWPORT HUD (TRUE LIQUID GLASS)
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0x2EFFFFFF),
                                Color(0x08FFFFFF)
                            )
                        )
                    )
                    .background(glassBg)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(32.dp)),
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
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = ElectricCobalt,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.isCameraScanning) "Scanning Path Ahead..." else "Back Camera Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCobalt,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Double-tap lower glass to describe scene",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondary
                        )
                    }
                }

                // Ribbon
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xCC060B14) else Color(0xCCEAF2FF))
                        .border(1.dp, LiquidGlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (latestBitmap != null) "FRAME CAPTURED" else "UPRIGHT 640x480",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VividCyan,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // =================================================================
            // 3. CENTER: ANIMATED ALLY SMILEY COMPANION FACE
            // =================================================================
            Spacer(modifier = Modifier.height(6.dp))

            AllyFaceOrb(
                appState = uiState.appState,
                onClick = { viewModel.onTactileSingleTap() },
                sizeDp = 215.dp,
                isDarkMode = isDark,
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ally companion face. State: ${uiState.appState.label}. Tap to talk or resume."
                    }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // =================================================================
            // 4. BOTTOM 50%: MASSIVE TACTILE TOUCH GLASS SURFACE
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x2EFFFFFF),
                                Color(0x05FFFFFF)
                            )
                        )
                    )
                    .background(glassBg)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Conversational State Headline
                val stateHeadline = when (uiState.appState) {
                    AppState.STANDBY_PAUSED -> "Paused (Standby)"
                    AppState.IDLE -> "Tap Lower Glass to Talk"
                    AppState.WAKE_LISTENING, AppState.COMMAND_LISTENING -> "Listening to you…"
                    AppState.THINKING -> "Thinking…"
                    AppState.SPEAKING -> "BlindSight Guiding…"
                    AppState.CONFIRMING -> "Understood"
                    AppState.CAUTION -> "Caution"
                }

                val headlineColor = when (uiState.appState) {
                    AppState.CAUTION -> AlertWarningRed
                    AppState.STANDBY_PAUSED -> Color(0xFF64748B)
                    AppState.WAKE_LISTENING, AppState.COMMAND_LISTENING -> VividCyan
                    AppState.CONFIRMING -> VividCyan
                    else -> textPrimary
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

                    // Prominent Spoken Text / Assistant Response
                    val primaryGuidanceText = if (uiState.statusText.isNotBlank()) {
                        uiState.statusText
                    } else {
                        "Directly ahead is clear. Proceed with confidence."
                    }

                    Text(
                        text = "\"$primaryGuidanceText\"",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary,
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
                            .background(if (isDark) Color(0xCC060B14) else Color(0xCCEAF2FF))
                            .border(1.dp, LiquidGlassBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = ElectricCobalt,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Single-tap: Speak  •  Double-tap: Describe  •  2 fingers: Silence",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary
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
            isDarkMode = uiState.isDarkMode,
            onToggleDarkMode = { viewModel.toggleDarkMode() },
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
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF060B14) else Color(0xFFEAF2FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(28.dp))
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
                            color = textPrimary
                        )
                        IconButton(onClick = { viewModel.setShowHistoryDialog(false) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close history",
                                tint = textSecondary
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
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCobalt),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
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
