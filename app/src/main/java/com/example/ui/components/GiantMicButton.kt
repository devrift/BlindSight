package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppState
import com.example.ui.theme.BlindSightAmber
import com.example.ui.theme.BlindSightAmberStrong
import com.example.ui.theme.BlindSightOk
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

@Composable
fun GiantMicButton(
    appState: AppState,
    statusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = appState == AppState.COMMAND_LISTENING || appState == AppState.WAKE_LISTENING
    val isSpeaking = appState == AppState.SPEAKING

    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val borderColor = when {
        isListening -> BlindSightOk
        isSpeaking -> BlindSightAmberStrong
        else -> BlindSightAmber
    }

    val iconColor = when {
        isListening -> BlindSightOk
        isSpeaking -> BlindSightAmberStrong
        else -> BlindSightAmberStrong
    }

    val accessibilityLabel = when {
        isSpeaking -> "Assistant is speaking. Double tap to stop audio."
        isListening -> "Listening to your voice. Double tap to finish."
        else -> "Microphone button. Double tap to speak to BlindSight."
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .scale(if (isListening || isSpeaking) pulseScale else 1f)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            borderColor.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
                .border(4.dp, borderColor, CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B1220)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.Mic,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = statusText,
            color = BlindSightTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when (appState) {
                AppState.IDLE -> "Double-tap button or say \"Hey BlindSight\""
                AppState.COMMAND_LISTENING -> "Listening… speak clearly"
                AppState.WAKE_LISTENING -> "Waiting for wake word…"
                AppState.THINKING -> "Thinking…"
                AppState.SPEAKING -> "Tap button to silence"
            },
            color = BlindSightTextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
