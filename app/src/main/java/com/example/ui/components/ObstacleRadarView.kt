package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReflexAlert
import com.example.data.model.ReflexTelemetry
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightAmberDark
import com.example.ui.theme.BlindSightBad
import com.example.ui.theme.BlindSightBadDark
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightCardEdge
import com.example.ui.theme.BlindSightOk
import com.example.ui.theme.BlindSightOkDark
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun ObstacleRadarView(
    telemetry: ReflexTelemetry,
    isAutoSweeping: Boolean,
    onToggleAutoSweep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = telemetry.isHardwareConnected || isAutoSweeping

    val alertColor by animateColorAsState(
        targetValue = when {
            !isLive -> Color(0xFFF59E0B) // Amber warning for disconnected
            telemetry.alert == ReflexAlert.STOP -> BlindSightBad
            telemetry.alert == ReflexAlert.STEER_LEFT || telemetry.alert == ReflexAlert.STEER_RIGHT -> BlindSightAmber
            telemetry.alert == ReflexAlert.CAREFUL -> Color(0xFF38BDF8)
            else -> BlindSightOk
        },
        animationSpec = tween(300),
        label = "alertColor"
    )

    val alertBg by animateColorAsState(
        targetValue = when {
            !isLive -> Color(0xFF1E170A)
            telemetry.alert == ReflexAlert.STOP -> BlindSightBadDark
            telemetry.alert == ReflexAlert.STEER_LEFT || telemetry.alert == ReflexAlert.STEER_RIGHT -> BlindSightAmberDark
            telemetry.alert == ReflexAlert.CAREFUL -> Color(0xFF0C4A6E)
            else -> BlindSightOkDark
        },
        animationSpec = tween(300),
        label = "alertBg"
    )

    val accessibilityDesc = if (isLive) {
        "Obstacle safety radar. Status is ${telemetry.alert.title}. Closest obstacle is ${telemetry.minDistance} centimeters away. Left beam is ${telemetry.leftCm} cm. Center beam is ${telemetry.centerCm} cm. Right beam is ${telemetry.rightCm} cm."
    } else {
        "Obstacle safety radar. Hardware is not connected. Tap Demo Walk to test simulated ultrasonic radar."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BlindSightCard)
            .border(1.5.dp, BlindSightCardEdge, RoundedCornerShape(20.dp))
            .padding(18.dp)
            .semantics {
                contentDescription = accessibilityDesc
            }
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (isLive) BlindSightAmber else BlindSightTextMuted,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Ultrasonic Reflex Radar",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlindSightTextPrimary
                    )
                    Text(
                        text = if (isLive) "Real-time hardware obstacle telemetry" else "Status: Hardware glasses disconnected",
                        fontSize = 12.sp,
                        color = if (isLive) BlindSightTextMuted else Color(0xFFF59E0B)
                    )
                }
            }

            Button(
                onClick = onToggleAutoSweep,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAutoSweeping) BlindSightAmber else BlindSightCardEdge
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isAutoSweeping) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isAutoSweeping) Color.Black else BlindSightTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = if (isAutoSweeping) "Stop Demo" else "Demo Walk",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAutoSweeping) Color.Black else BlindSightTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Primary Warning / Clearance / Disconnected Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(alertBg)
                .border(2.dp, alertColor, RoundedCornerShape(14.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (!isLive) Icons.Default.PowerOff else Icons.Default.Warning,
                        contentDescription = null,
                        tint = alertColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (!isLive) "HARDWARE NOT CONNECTED" else if (isAutoSweeping) "DEMO SIMULATION ACTIVE" else telemetry.alert.title,
                        color = BlindSightTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (!isLive) {
                        "ESP32 Smart Glasses Radar is not connected. Connect your glasses or tap \"Demo Walk\" above to test obstacle alerts."
                    } else if (isAutoSweeping) {
                        "Simulating moving obstacles and walking reflex feedback."
                    } else {
                        telemetry.alert.description
                    },
                    color = BlindSightTextPrimary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3 Distance Telemetry Gauges (Left, Center Forward, Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiveBeamGauge(
                label = "LEFT",
                distanceCm = telemetry.leftCm,
                isLive = isLive,
                modifier = Modifier.weight(1f)
            )
            LiveBeamGauge(
                label = "FORWARD",
                distanceCm = telemetry.centerCm,
                isLive = isLive,
                isPrimary = true,
                modifier = Modifier.weight(1f)
            )
            LiveBeamGauge(
                label = "RIGHT",
                distanceCm = telemetry.rightCm,
                isLive = isLive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LiveBeamGauge(
    label: String,
    distanceCm: Int,
    isLive: Boolean,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val barColor by animateColorAsState(
        targetValue = when {
            !isLive -> BlindSightCardEdge
            distanceCm < 25 -> BlindSightBad
            distanceCm <= 45 -> BlindSightAmber
            distanceCm < 100 -> Color(0xFF38BDF8)
            else -> BlindSightOk
        },
        label = "beamColor"
    )

    val zoneLabel = when {
        !isLive -> "OFFLINE"
        distanceCm < 25 -> "STOP"
        distanceCm <= 45 -> "CAREFUL"
        distanceCm < 100 -> "NEAR"
        else -> "CLEAR"
    }

    val fillPercent by animateFloatAsState(
        targetValue = if (isLive) (distanceCm.coerceIn(10, 200) / 200f) else 0f,
        animationSpec = tween(250),
        label = "fillPercent"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF05070C))
            .border(
                width = if (isPrimary && isLive) 1.5.dp else 1.dp,
                color = if (isPrimary && isLive) BlindSightAmber.copy(alpha = 0.5f) else BlindSightCardEdge,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isPrimary && isLive) BlindSightAmber else BlindSightTextMuted,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isLive) "$distanceCm" else "--",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = if (isLive) barColor else BlindSightTextMuted
        )
        Text(
            text = if (isLive) "cm" else "no data",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BlindSightTextMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Proximity bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BlindSightCardEdge)
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillPercent)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status badge pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLive) barColor.copy(alpha = 0.15f) else BlindSightCardEdge.copy(alpha = 0.3f))
                .border(1.dp, if (isLive) barColor.copy(alpha = 0.4f) else BlindSightCardEdge, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = zoneLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isLive) barColor else BlindSightTextMuted
            )
        }
    }
}
