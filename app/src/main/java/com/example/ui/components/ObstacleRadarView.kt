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
    val alertColor by animateColorAsState(
        targetValue = when (telemetry.alert) {
            ReflexAlert.STOP -> BlindSightBad
            ReflexAlert.STEER_LEFT, ReflexAlert.STEER_RIGHT -> BlindSightAmber
            ReflexAlert.CAREFUL -> Color(0xFF38BDF8)
            ReflexAlert.CLEAR -> BlindSightOk
        },
        animationSpec = tween(300),
        label = "alertColor"
    )

    val alertBg by animateColorAsState(
        targetValue = when (telemetry.alert) {
            ReflexAlert.STOP -> BlindSightBadDark
            ReflexAlert.STEER_LEFT, ReflexAlert.STEER_RIGHT -> BlindSightAmberDark
            ReflexAlert.CAREFUL -> Color(0xFF0C4A6E)
            ReflexAlert.CLEAR -> BlindSightOkDark
        },
        animationSpec = tween(300),
        label = "alertBg"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BlindSightCard)
            .border(1.5.dp, BlindSightCardEdge, RoundedCornerShape(20.dp))
            .padding(18.dp)
            .semantics {
                contentDescription = "Obstacle safety radar. Status is ${telemetry.alert.title}. Closest obstacle is ${telemetry.minDistance} centimeters away. Left beam is ${telemetry.leftCm} cm. Center beam is ${telemetry.centerCm} cm. Right beam is ${telemetry.rightCm} cm."
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
                    tint = BlindSightAmber,
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
                        text = "Real-time hardware obstacle telemetry",
                        fontSize = 12.sp,
                        color = BlindSightTextMuted
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

        // Big Primary Warning / Clearance Banner
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
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = alertColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = telemetry.alert.title,
                        color = BlindSightTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = telemetry.alert.description,
                    color = BlindSightTextPrimary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3 Live Distance Telemetry Gauges (Left, Center Forward, Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiveBeamGauge(
                label = "LEFT",
                distanceCm = telemetry.leftCm,
                modifier = Modifier.weight(1f)
            )
            LiveBeamGauge(
                label = "FORWARD",
                distanceCm = telemetry.centerCm,
                isPrimary = true,
                modifier = Modifier.weight(1f)
            )
            LiveBeamGauge(
                label = "RIGHT",
                distanceCm = telemetry.rightCm,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LiveBeamGauge(
    label: String,
    distanceCm: Int,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val barColor by animateColorAsState(
        targetValue = when {
            distanceCm < 25 -> BlindSightBad
            distanceCm <= 45 -> BlindSightAmber
            distanceCm < 100 -> Color(0xFF38BDF8)
            else -> BlindSightOk
        },
        label = "beamColor"
    )

    val zoneLabel = when {
        distanceCm < 25 -> "STOP"
        distanceCm <= 45 -> "CAREFUL"
        distanceCm < 100 -> "NEAR"
        else -> "CLEAR"
    }

    // Normalized progress fill (0.0 to 1.0) for 0 to 200 cm
    val fillPercent by animateFloatAsState(
        targetValue = (distanceCm.coerceIn(10, 200) / 200f),
        animationSpec = tween(250),
        label = "fillPercent"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF05070C))
            .border(
                width = if (isPrimary) 1.5.dp else 1.dp,
                color = if (isPrimary) BlindSightAmber.copy(alpha = 0.5f) else BlindSightCardEdge,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isPrimary) BlindSightAmber else BlindSightTextMuted,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "$distanceCm",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = barColor
        )
        Text(
            text = "cm",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BlindSightTextMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Visual proximity bar (filled bar, NO user sliders)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BlindSightCardEdge)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillPercent)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status badge pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(barColor.copy(alpha = 0.15f))
                .border(1.dp, barColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = zoneLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = barColor
            )
        }
    }
}
