package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
    val isThinking = appState == AppState.THINKING

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth decorative aura pulse (does NOT resize touch target)
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = if (isListening) 0.55f else if (isThinking) 0.40f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura"
    )

    val activeColor by animateColorAsState(
        targetValue = when {
            isListening -> BlindSightOk
            isThinking -> Color(0xFF38BDF8)
            isSpeaking -> BlindSightAmberStrong
            else -> BlindSightAmber
        },
        label = "activeColor"
    )

    val accessibilityLabel = when {
        isSpeaking -> "Assistant is speaking. Double tap to stop audio."
        isListening -> "Listening to your voice. Double tap to finish."
        isThinking -> "Processing your question."
        else -> "Microphone button. Double tap to speak to BlindSight."
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outer Container: Fixed 156.dp touch box - zero floppy layout jumps!
        Box(
            modifier = Modifier
                .size(156.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // 1. Soft decorative background aura
            if (isListening || isThinking || isSpeaking) {
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    activeColor.copy(alpha = auraAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // 2. Solid Main Button Surface (firm, tactile, non-floppy)
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .scale(if (isPressed) 0.95f else 1.0f)
                    .clip(CircleShape)
                    .background(Color(0xFF090E17))
                    .border(
                        width = if (isListening) 4.dp else 3.dp,
                        color = activeColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner button ring
                Box(
                    modifier = Modifier
                        .size(114.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening) activeColor.copy(alpha = 0.18f) else Color(0xFF05080E)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.Mic,
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(54.dp)
                    )
                }
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

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = when (appState) {
                AppState.IDLE -> "Say \"Hey BlindSight\" or tap button"
                AppState.COMMAND_LISTENING -> "● Listening… Speak your question"
                AppState.WAKE_LISTENING -> "Waiting for \"Hey BlindSight\"…"
                AppState.THINKING -> "Thinking…"
                AppState.SPEAKING -> "Tap to stop voice"
            },
            color = if (isListening) BlindSightOk else BlindSightTextMuted,
            fontSize = 14.sp,
            fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
