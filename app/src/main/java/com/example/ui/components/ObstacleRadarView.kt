package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onDistanceChange: (Int, Int, Int) -> Unit,
    onToggleAutoSweep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alertColor = when (telemetry.alert) {
        ReflexAlert.STOP -> BlindSightBad
        ReflexAlert.STEER_LEFT, ReflexAlert.STEER_RIGHT -> BlindSightAmber
        ReflexAlert.CAREFUL -> Color(0xFF38BDF8)
        ReflexAlert.CLEAR -> BlindSightOk
    }

    val alertBg = when (telemetry.alert) {
        ReflexAlert.STOP -> BlindSightBadDark
        ReflexAlert.STEER_LEFT, ReflexAlert.STEER_RIGHT -> BlindSightAmberDark
        ReflexAlert.CAREFUL -> Color(0xFF0C4A6E)
        ReflexAlert.CLEAR -> BlindSightOkDark
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BlindSightCard)
            .border(1.dp, BlindSightCardEdge, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .semantics {
                contentDescription = "Obstacle radar. Status is ${telemetry.alert.title}. Closest obstacle is ${telemetry.minDistance} centimeters away. ${telemetry.alert.description}"
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
                Text(
                    text = "Tier 0: Ultrasonic Reflex",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlindSightTextPrimary
                )
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
                    text = if (isAutoSweeping) "Stop Demo" else "Test Demo",
                    fontSize = 12.sp,
                    color = if (isAutoSweeping) Color.Black else BlindSightTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Big Alert Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(alertBg)
                .border(2.dp, alertColor, RoundedCornerShape(12.dp))
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
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3 Sensor Columns (Left, Center, Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensorColumn(
                label = "Left Beam",
                distanceCm = telemetry.leftCm,
                onValueChange = { onDistanceChange(it, telemetry.centerCm, telemetry.rightCm) },
                modifier = Modifier.weight(1f)
            )
            SensorColumn(
                label = "Center Beam",
                distanceCm = telemetry.centerCm,
                onValueChange = { onDistanceChange(telemetry.leftCm, it, telemetry.rightCm) },
                modifier = Modifier.weight(1f)
            )
            SensorColumn(
                label = "Right Beam",
                distanceCm = telemetry.rightCm,
                onValueChange = { onDistanceChange(telemetry.leftCm, telemetry.centerCm, it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SensorColumn(
    label: String,
    distanceCm: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = when {
        distanceCm < 25 -> BlindSightBad
        distanceCm <= 45 -> BlindSightAmber
        distanceCm < 100 -> Color(0xFF38BDF8)
        else -> BlindSightOk
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF05070C))
            .border(1.dp, BlindSightCardEdge, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BlindSightTextMuted
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$distanceCm cm",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = barColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = distanceCm.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 10f..250f,
            colors = SliderDefaults.colors(
                thumbColor = barColor,
                activeTrackColor = barColor,
                inactiveTrackColor = BlindSightCardEdge
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
