package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.theme.BlindSightCard
import com.example.ui.theme.BlindSightOk
import com.example.ui.theme.BlindSightTextMuted
import com.example.ui.theme.BlindSightTextPrimary

/**
 * ChatGPT-Style Reactive Voice Orb for Blind Users.
 *
 * Designed with a massive 220dp touch area, fluid organic breathing pulses,
 * multi-layered gradient rings that react dynamically to listening, thinking, and speaking states.
 */
@Composable
fun GiantMicButton(
    appState: AppState,
    statusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = appState == AppState.COMMAND_LISTENING || appState == AppState.WAKE_LISTENING
    val isSpeaking = appState == AppState.SPEAKING
    val isThinking = appState == AppState.THINKING

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 1. Organic breathing animation (mimicking ChatGPT voice orb breathing)
    val infiniteTransition = rememberInfiniteTransition(label = "orbMotion")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (isListening) 1.10f else if (isSpeaking) 1.08f else if (isThinking) 1.04f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 650 else if (isSpeaking) 850 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = if (isListening) 0.65f else if (isThinking) 0.45f else if (isSpeaking) 0.50f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 700 else if (isThinking) 900 else 1600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Dynamic state colors
    val primaryOrbColor by animateColorAsState(
        targetValue = when {
            isListening -> BlindSightOk
            isThinking -> Color(0xFF38BDF8) // Electric cyan/blue
            isSpeaking -> BlindSightAmberStrong
            else -> BlindSightAmber
        },
        animationSpec = tween(350),
        label = "primaryOrbColor"
    )

    val secondaryOrbColor by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF10B981) // Deep emerald
            isThinking -> Color(0xFF6366F1) // Indigo
            isSpeaking -> Color(0xFFF97316) // Warm orange
            else -> Color(0xFFD97706)
        },
        animationSpec = tween(350),
        label = "secondaryOrbColor"
    )

    val accessibilityLabel = when {
        isSpeaking -> "BlindSight is speaking. Double tap anywhere or on the orb to stop."
        isListening -> "Listening to your voice. Speak your request now. Double tap to finish."
        isThinking -> "BlindSight is processing your answer."
        else -> "Large voice orb. Tap or triple-tap anywhere on screen to speak to BlindSight."
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Massive 220dp touch container
        Box(
            modifier = Modifier
                .size(220.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Layer 1: Outermost ethereal radial glow aura
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(breathingScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryOrbColor.copy(alpha = auraAlpha),
                                secondaryOrbColor.copy(alpha = auraAlpha * 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Layer 2: ChatGPT-style fluid rotating halo ripple ring
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .rotate(if (isThinking || isListening) rotationAngle else 0f)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryOrbColor.copy(alpha = 0.8f),
                            secondaryOrbColor.copy(alpha = 0.3f),
                            primaryOrbColor.copy(alpha = 0.9f),
                            secondaryOrbColor.copy(alpha = 0.2f),
                            primaryOrbColor.copy(alpha = 0.8f)
                        )
                    ),
                    radius = size.minDimension / 2,
                    style = Stroke(width = if (isListening) 4.dp.toPx() else 3.dp.toPx())
                )
            }

            // Layer 3: Solid Main Tactile Voice Orb (150dp core)
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(if (isPressed) 0.94f else 1.0f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF131D2D),
                                Color(0xFF070B12)
                            )
                        )
                    )
                    .border(
                        width = if (isListening) 4.dp else 3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(primaryOrbColor, secondaryOrbColor)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Layer 4: Inner reactive core
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryOrbColor.copy(alpha = if (isListening) 0.35f else if (isSpeaking) 0.28f else 0.15f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isSpeaking -> Icons.Default.VolumeUp
                            isListening -> Icons.Default.GraphicEq
                            isThinking -> Icons.Default.GraphicEq
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = primaryOrbColor,
                        modifier = Modifier.size(58.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // State indicator badge with accessibility cues
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BlindSightCard)
                .border(1.dp, primaryOrbColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = when {
                    isSpeaking -> "● SPEAKING ANSWER"
                    isListening -> "● LISTENING… SPEAK NOW"
                    isThinking -> "● THINKING…"
                    else -> "● TRIPLE-TAP SCREEN OR TAP ORB"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryOrbColor,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Spoken / Status Text caption
        Text(
            text = statusText,
            color = BlindSightTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
