package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.CameraSource
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightOk
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun HardwareStatusStrip(
    isHardwareGlassesConnected: Boolean,
    latestFrameSource: CameraSource,
    isCameraScanning: Boolean,
    isPhoneCameraFallbackEnabled: Boolean,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scanningColor by infiniteTransition.animateColor(
        initialValue = Color(0xFF10B981),
        targetValue = Color(0xFF34D399).copy(alpha = 0.4f),
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanningPulse"
    )

    val cameraLabel: String
    val cameraColor: Color
    val isCameraInActiveUse = isCameraScanning || latestFrameSource == CameraSource.PHONE_BACK_CAMERA

    if (isCameraScanning) {
        if (isHardwareGlassesConnected) {
            cameraLabel = "Glasses Cam Scanning…"
            cameraColor = scanningColor
        } else {
            cameraLabel = "Mobile Back Cam Scanning…"
            cameraColor = scanningColor
        }
    } else {
        when {
            latestFrameSource == CameraSource.PHONE_BACK_CAMERA -> {
                cameraLabel = "Mobile Back Cam (In Use)"
                cameraColor = Color(0xFF10B981)
            }
            isHardwareGlassesConnected -> {
                cameraLabel = "Glasses Cam Ready"
                cameraColor = Color(0xFF10B981)
            }
            isPhoneCameraFallbackEnabled -> {
                cameraLabel = "Mobile Cam Ready"
                cameraColor = BlindSightAmber
            }
            else -> {
                cameraLabel = "Cam Fallback Off"
                cameraColor = BlindSightTextMuted
            }
        }
    }

    val accessibilityText = buildString {
        append("Hardware and Privacy Status. ")
        if (isHardwareGlassesConnected) {
            append("Smart glasses hardware is connected. ")
        } else {
            append("Smart glasses hardware is disconnected. ")
        }
        if (latestFrameSource == CameraSource.PHONE_BACK_CAMERA) {
            append("Vision is using your mobile back camera. ")
        } else if (isPhoneCameraFallbackEnabled) {
            append("Mobile back camera fallback is ready. ")
        } else {
            append("Mobile camera fallback is disabled. ")
        }
        append("Ultrasonic reflex sensors: ${if (isHardwareGlassesConnected) "Active" else "Hardware disconnected"}. ")
        append("Microphone is active. Tap to hear full hardware details.")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BlindSightCard)
            .border(1.dp, if (isCameraInActiveUse) Color(0xFF10B981).copy(alpha = 0.5f) else BlindSightCardEdge, RoundedCornerShape(14.dp))
            .clickable { onStatusClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityText
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Top Row: Glasses status & Cam status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glasses Hardware Connection Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isHardwareGlassesConnected) BlindSightOk else BlindSightAmber)
                    )
                    Text(
                        text = if (isHardwareGlassesConnected) "Glasses: Connected" else "Glasses: Disconnected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHardwareGlassesConnected) BlindSightOk else BlindSightAmber
                    )
                }

                // Active Camera Indicator (Explicitly shows Mobile Back Cam vs Glasses)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isPhoneCameraFallbackEnabled || isHardwareGlassesConnected) Icons.Default.CameraAlt else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = cameraColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = cameraLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = cameraColor
                    )
                }
            }

            // Bottom Sub-Row: Radar & Mic status with Privacy disclosure tap hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Radar Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = if (isHardwareGlassesConnected) BlindSightOk else BlindSightTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isHardwareGlassesConnected) "Radar Active" else "Radar Disconnected",
                            fontSize = 11.sp,
                            color = if (isHardwareGlassesConnected) BlindSightOk else BlindSightTextMuted
                        )
                    }

                    // Mic Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = BlindSightOk,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Mic Active",
                            fontSize = 11.sp,
                            color = BlindSightOk
                        )
                    }
                }

                // Privacy Disclosure Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BlindSightAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Hardware & Privacy Info",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BlindSightAmber
                    )
                }
            }
        }
    }
}
