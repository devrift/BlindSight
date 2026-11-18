package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.SupportedLanguage
import com.example.data.model.TactileAction
import com.example.service.VoicePersona
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.LiquidGlassBorder
import com.example.ui.theme.MidnightBlueGlass
import com.example.ui.theme.PorcelainGlass
import com.example.ui.theme.SlateTextDark
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate
import com.example.ui.theme.VividCyan

/**
 * Modern Liquid Glass Settings Dialog.
 * Fully customized for BlindSight v1.0.0.
 * Includes Tactile Gestures, Easy Voice Wake Words, Update Permissions, Theme, Text Size.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    currentLanguage: SupportedLanguage,
    onLanguageChange: (SupportedLanguage) -> Unit,
    currentVoicePersona: VoicePersona = VoicePersona.AUTO,
    onVoicePersonaChange: (VoicePersona) -> Unit = {},
    currentTextSize: Float,
    onTextSizeChange: (Float) -> Unit,
    isDarkMode: Boolean = true,
    onToggleDarkMode: () -> Unit = {},
    isPhoneCameraFallbackEnabled: Boolean = true,
    onTogglePhoneCameraFallback: () -> Unit = {},
    singleTapAction: TactileAction = TactileAction.START_LISTENING,
    onSingleTapActionChange: (TactileAction) -> Unit = {},
    doubleTapAction: TactileAction = TactileAction.DESCRIBE_SCENE,
    onDoubleTapActionChange: (TactileAction) -> Unit = {},
    tripleTapAction: TactileAction = TactileAction.READ_TEXT,
    onTripleTapActionChange: (TactileAction) -> Unit = {},
    canInstallPackages: Boolean = false,
    onOpenInstallSettings: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onReadHelp: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDarkMode) MidnightBlueGlass else PorcelainGlass
    val textPrimary = if (isDarkMode) TextPrimaryWhite else SlateTextDark
    val textSecondary = if (isDarkMode) TextSecondarySlate else SlateTextMuted
    val cardSurface = if (isDarkMode) Color(0xFF060B14) else Color(0xFFF1F6FF)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(dialogBg)
                .border(1.dp, LiquidGlassBorder, RoundedCornerShape(28.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = ElectricCobalt,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Close settings dialog."
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Spring Physics Theme Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardSurface)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDarkMode) "Deep Oceanic Obsidian" else "Porcelain Ice Light",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = if (isDarkMode) "Electric blue and midnight glass" else "Clean porcelain glass with cerulean accents",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }

                val thumbOffset by animateDpAsState(
                    targetValue = if (isDarkMode) 24.dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "themeSwitchThumb"
                )

                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (isDarkMode) Color(0xFF0F2752) else Color(0xFFC7DEFF))
                        .border(1.dp, LiquidGlassBorder, RoundedCornerShape(15.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleDarkMode
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricCobalt, VividCyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =================================================================
            // TACTILE GESTURES CUSTOMIZATION
            // =================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Gesture,
                    contentDescription = null,
                    tint = ElectricCobalt,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Tactile Screen Gestures",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose what happens when you tap the giant touch surface.",
                fontSize = 12.sp,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Single Tap Selector
            TactileGestureSelector(
                gestureLabel = "Single Tap",
                selectedAction = singleTapAction,
                onSelectAction = onSingleTapActionChange,
                cardSurface = cardSurface,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Double Tap Selector
            TactileGestureSelector(
                gestureLabel = "Double Tap",
                selectedAction = doubleTapAction,
                onSelectAction = onDoubleTapActionChange,
                cardSurface = cardSurface,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Triple Tap Selector
            TactileGestureSelector(
                gestureLabel = "Triple Tap",
                selectedAction = tripleTapAction,
                onSelectAction = onTripleTapActionChange,
                cardSurface = cardSurface,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // =================================================================
            // EASY VOICE WAKE WORDS CARD
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardSurface)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = VividCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Voice Wake Words",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "You can speak naturally anytime to activate BlindSight:",
                    fontSize = 12.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCobalt.copy(alpha = 0.2f))
                            .border(1.dp, ElectricCobalt.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\"Hey Assistant\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VividCyan
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCobalt.copy(alpha = 0.2f))
                            .border(1.dp, ElectricCobalt.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\"Hey BlindSight\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VividCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Common pronunciation variations (like 'have line sight') are automatically recognized.",
                    fontSize = 11.sp,
                    color = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =================================================================
            // APP UPDATE & PERMISSION CARD
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardSurface)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (canInstallPackages) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "In-App Updates",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (canInstallPackages) Color(0x3310B981) else Color(0x33F59E0B)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (canInstallPackages) "Permission Granted" else "Permission Needed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canInstallPackages) Color(0xFF34D399) else Color(0xFFFBBF24)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (canInstallPackages) {
                        "BlindSight has permission to install updates directly inside the app without opening a browser."
                    } else {
                        "Android requires you to allow 'Install unknown apps' so BlindSight can update automatically."
                    },
                    fontSize = 12.sp,
                    color = textSecondary
                )

                if (!canInstallPackages) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenInstallSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCobalt),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Update Permission", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Text Size
            Text(
                text = "Text Size",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizes = listOf(1.0f to "A Normal", 1.2f to "A+ Large", 1.45f to "A++ Extra")
                sizes.forEach { (scale, label) ->
                    val isSelected = currentTextSize == scale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ElectricCobalt else cardSurface)
                            .border(1.dp, if (isSelected) VividCyan else LiquidGlassBorder, RoundedCornerShape(12.dp))
                            .clickable { onTextSizeChange(scale) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mobile Back Camera Fallback
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardSurface)
                    .border(1.dp, LiquidGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mobile Camera Fallback",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "Inspect path ahead using phone camera when smart glasses are disconnected.",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }

                Switch(
                    checked = isPhoneCameraFallbackEnabled,
                    onCheckedChange = { onTogglePhoneCameraFallback() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VividCyan,
                        checkedTrackColor = ElectricCobalt.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Check for updates
            Button(
                onClick = {
                    onDismiss()
                    onCheckUpdates()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCobalt.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = VividCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Check for App Updates", color = VividCyan, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Read help commands out loud
            Button(
                onClick = onReadHelp,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = cardSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = ElectricCobalt,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Speak All Commands Help", color = textPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Reusable Tactile Gesture Selector Card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TactileGestureSelector(
    gestureLabel: String,
    selectedAction: TactileAction,
    onSelectAction: (TactileAction) -> Unit,
    cardSurface: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardSurface)
            .border(1.dp, LiquidGlassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = gestureLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                text = selectedAction.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = VividCyan
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TactileAction.values().forEach { action ->
                val isSelected = action == selectedAction
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) ElectricCobalt else Color(0x1A2563EB)
                        )
                        .border(
                            1.dp,
                            if (isSelected) VividCyan else Color(0x332563EB),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectAction(action) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = action.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else textPrimary
                    )
                }
            }
        }
    }
}
