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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.AppState
import com.example.ui.theme.AllyAmber
import com.example.ui.theme.AllyAmberGlow
import com.example.ui.theme.AllyCyan
import com.example.ui.theme.AllyWarning
import com.example.ui.theme.AllyWarningAmber
import kotlin.math.cos
import kotlin.math.sin

/**
 * Organic, Fluid Audio-Reactive Liquid Orb / Membrane.
 *
 * An industrial-grade, Apple/VisionOS-inspired liquid glass intelligence presence:
 * - IDLE: Calm, rhythmic breathing expansion, deep obsidian and cyan specular glass reflections.
 * - LISTENING: Concentric harmonic ripples propagating outward, liquid cyan membrane reacting to audio.
 * - THINKING: Swirling rotational liquid plasma vortex with orbiting caustic flares.
 * - SPEAKING: Volumetric fluid undulation and expansion with multi-harmonic wave nodes and radiant glow.
 * - CONFIRMING: Radiant golden fluid shimmer expanding outward with glass refraction.
 * - CAUTION: Soft pulsing red-amber warning luminescence across the outer perimeter.
 *
 * Strictly adheres to the project's accessibility rules: zero emojis, pure tactile liquid aesthetic.
 */
@Composable
fun AllyFaceOrb(
    appState: AppState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 250.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidOrbTransition")

    // 1. Organic Harmonic Phases
    val idleBreathing by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleBreathing"
    )

    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 6.28318f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase2"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    // Conversational State Flags
    val isListening = appState == AppState.WAKE_LISTENING || appState == AppState.COMMAND_LISTENING
    val isThinking = appState == AppState.THINKING
    val isSpeaking = appState == AppState.SPEAKING
    val isConfirming = appState == AppState.CONFIRMING
    val isCaution = appState == AppState.CAUTION

    // Dynamic Liquid Glass Colors
    val primaryColor by animateColorAsState(
        targetValue = when {
            isCaution -> AllyWarning
            isConfirming -> AllyAmberGlow
            isListening -> AllyCyan
            isThinking -> AllyAmber
            isSpeaking -> AllyAmberGlow
            else -> AllyCyan.copy(alpha = 0.85f)
        },
        animationSpec = tween(400),
        label = "liquidPrimaryColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when {
            isCaution -> AllyWarningAmber
            isConfirming -> AllyAmber
            isListening -> Color(0xFF1D4ED8)
            isThinking -> Color(0xFFB45309)
            isSpeaking -> AllyAmber
            else -> Color(0xFF0F172A)
        },
        animationSpec = tween(400),
        label = "liquidSecondaryColor"
    )

    Box(
        modifier = modifier
            .size(sizeDp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.68f

            // -----------------------------------------------------------------
            // 1. Concentric Harmonic Ripples (Listening State)
            // -----------------------------------------------------------------
            if (isListening) {
                for (i in 0..2) {
                    val progress = (ripplePhase + i * 0.33f) % 1f
                    val rippleR = baseRadius + progress * (size.minDimension * 0.28f)
                    val alpha = (1f - progress).coerceIn(0f, 0.45f)
                    drawCircle(
                        color = AllyCyan.copy(alpha = alpha),
                        radius = rippleR,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // -----------------------------------------------------------------
            // 2. Ambient Volumetric Glow Halo
            // -----------------------------------------------------------------
            val glowRadius = baseRadius * (if (isSpeaking) 1.55f else 1.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (isSpeaking || isConfirming) 0.55f else 0.32f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // -----------------------------------------------------------------
            // 3. Fluid Organic Membrane Generation
            // -----------------------------------------------------------------
            val points = 36
            val membranePath = Path()
            val amp1 = when {
                isSpeaking -> baseRadius * 0.12f
                isListening -> baseRadius * 0.08f
                isThinking -> baseRadius * 0.06f
                isCaution -> baseRadius * 0.09f
                else -> baseRadius * 0.035f
            }
            val amp2 = when {
                isSpeaking -> baseRadius * 0.08f
                isListening -> baseRadius * 0.05f
                isThinking -> baseRadius * 0.04f
                else -> baseRadius * 0.02f
            }

            val rScale = if (isListening || isSpeaking) idleBreathing else 1.0f

            val coords = mutableListOf<Offset>()
            for (i in 0 until points) {
                val theta = (i.toFloat() / points) * 6.2831853f
                // Multi-harmonic radial displacement
                val harmonic = sin(theta * 3f + wavePhase1) * amp1 + cos(theta * 5f + wavePhase2) * amp2
                val currentR = (baseRadius + harmonic) * rScale
                val x = center.x + currentR * cos(theta)
                val y = center.y + currentR * sin(theta)
                coords.add(Offset(x, y))
            }

            // Construct smooth spline path through harmonic points
            if (coords.isNotEmpty()) {
                membranePath.moveTo(
                    (coords[0].x + coords[points - 1].x) / 2f,
                    (coords[0].y + coords[points - 1].y) / 2f
                )
                for (i in 0 until points) {
                    val current = coords[i]
                    val next = coords[(i + 1) % points]
                    val midX = (current.x + next.x) / 2f
                    val midY = (current.y + next.y) / 2f
                    membranePath.quadraticTo(current.x, current.y, midX, midY)
                }
                membranePath.close()
            }

            // Draw Liquid Glass Membrane Interior
            drawPath(
                path = membranePath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E293B),
                        Color(0xFF111827),
                        Color(0xFF080B10)
                    ),
                    center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.25f),
                    radius = baseRadius * 1.1f
                )
            )

            // Draw Liquid Membrane Outer Specular Rim
            drawPath(
                path = membranePath,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.4f),
                        primaryColor.copy(alpha = 0.85f)
                    ),
                    center = center
                ),
                style = Stroke(width = 2.4.dp.toPx())
            )

            // -----------------------------------------------------------------
            // 4. Rotational Plasma Shimmer Vortex (Thinking State)
            // -----------------------------------------------------------------
            if (isThinking) {
                rotate(rotationAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                AllyAmber.copy(alpha = 0.2f),
                                AllyAmberGlow,
                                Color.Transparent
                            ),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 0.85f, center.y - baseRadius * 0.85f),
                        size = Size(baseRadius * 1.7f, baseRadius * 1.7f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // -----------------------------------------------------------------
            // 5. Inner Luminous Core / Audio Nucleus
            // -----------------------------------------------------------------
            val nucleusRadius = baseRadius * (if (isSpeaking) 0.48f else 0.35f) * idleBreathing
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (isSpeaking) 0.9f else 0.65f),
                        secondaryColor.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = nucleusRadius
                ),
                radius = nucleusRadius,
                center = center
            )

            // -----------------------------------------------------------------
            // 6. Liquid Glass Top Specular Caustic Reflection
            // -----------------------------------------------------------------
            val specularOffset = Offset(center.x, center.y - baseRadius * 0.42f)
            val specularWidth = baseRadius * 0.85f
            val specularHeight = baseRadius * 0.26f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isSpeaking) 0.45f else 0.28f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = specularOffset,
                    radius = specularWidth / 2f
                ),
                topLeft = Offset(specularOffset.x - specularWidth / 2f, specularOffset.y - specularHeight / 2f),
                size = Size(specularWidth, specularHeight)
            )
        }
    }
}
