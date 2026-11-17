package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import kotlin.math.sin

/**
 * The Living "Ally" Face / Morphing Expression Orb.
 *
 * An expressive, emotionally intelligent visual presence inspired by Envision Ally.
 * Seamlessly reflects 6 organic conversational states:
 * - Idle / Attentive: Gentle breathing glow with soft, occasional eye blinks.
 * - Listening: Concentric soft wave ripples reacting dynamically to user voice amplitude; slight upward "curious" eye tilt.
 * - Thinking / Processing: Smooth, warm rotational shimmer and slight squint/focus expression.
 * - Speaking / Guiding: Fluid, audio-reactive mouth/soundwave line moving in sync with speech playback.
 * - Empathetic / Confirming: Warm amber/gold smile flash when a command is understood or safe path is confirmed.
 * - Caution / Obstacle Hazard: Gentle pulse shift to soft red-amber warning state accompanied by alert glow.
 */
@Composable
fun AllyFaceOrb(
    appState: AppState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 250.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AllyOrbInfinite")

    // 1. Organic Breathing Pulse (scale 0.96 to 1.04)
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // 2. Periodic Eye Blink (every ~4.2s quick dip to 0.1f)
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blinkProgress"
    )

    // 3. Concentric Ripple Expansion (for Listening)
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    // 4. Rotational Shimmer Angle (for Thinking)
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // 5. Soundwave Undulation Phase (for Speaking)
    val mouthWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mouthWavePhase"
    )

    // Dynamic State Colors
    val isListening = appState == AppState.WAKE_LISTENING || appState == AppState.COMMAND_LISTENING
    val isThinking = appState == AppState.THINKING
    val isSpeaking = appState == AppState.SPEAKING
    val isConfirming = appState == AppState.CONFIRMING
    val isCaution = appState == AppState.CAUTION

    val primaryGlowColor by animateColorAsState(
        targetValue = when {
            isCaution -> AllyWarning
            isConfirming -> AllyAmberGlow
            isListening -> AllyCyan
            isThinking -> AllyAmber
            isSpeaking -> AllyAmberGlow
            else -> AllyCyan.copy(alpha = 0.85f)
        },
        animationSpec = tween(400),
        label = "primaryGlowColor"
    )

    val secondaryGlowColor by animateColorAsState(
        targetValue = when {
            isCaution -> AllyWarningAmber
            isConfirming -> AllyAmber
            isListening -> Color(0xFF1D4ED8)
            isThinking -> Color(0xFFB45309)
            isSpeaking -> AllyAmber
            else -> Color(0xFF0F172A)
        },
        animationSpec = tween(400),
        label = "secondaryGlowColor"
    )

    val eyeTiltAngle by animateFloatAsState(
        targetValue = when {
            isListening -> 10f // curious upward tilt
            isThinking -> -6f // focused inward squint
            isConfirming -> 0f
            else -> 0f
        },
        animationSpec = tween(350),
        label = "eyeTiltAngle"
    )

    val eyeHeightFactor by animateFloatAsState(
        targetValue = when {
            isThinking -> 0.28f // narrow concentration slit
            isConfirming -> 0.65f // happy crescent
            isCaution -> 1.15f // wide alert eyes
            isSpeaking -> 1.1f
            else -> 1.0f
        },
        animationSpec = tween(300),
        label = "eyeHeightFactor"
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
            val baseRadius = (size.minDimension / 2f) * 0.72f
            val orbRadius = baseRadius * (if (isListening || isSpeaking) breathingScale else 1.0f)

            // ---------------------------------------------------------------
            // A. Concentric Soft Wave Ripples (Listening state)
            // ---------------------------------------------------------------
            if (isListening) {
                for (i in 0..2) {
                    val progress = (ripplePhase + i * 0.33f) % 1f
                    val rippleRadius = orbRadius + progress * (size.minDimension * 0.25f)
                    val rippleAlpha = (1f - progress).coerceIn(0f, 0.45f)
                    drawCircle(
                        color = AllyCyan.copy(alpha = rippleAlpha),
                        radius = rippleRadius,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            // ---------------------------------------------------------------
            // B. Ambient Breathing Glow Halo
            // ---------------------------------------------------------------
            val glowRadius = orbRadius * 1.35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = if (isSpeaking || isConfirming) 0.5f else 0.32f),
                        secondaryGlowColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // ---------------------------------------------------------------
            // C. Main Dark Glass Orb Core
            // ---------------------------------------------------------------
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF161F30),
                        Color(0xFF0F172A),
                        Color(0xFF0B0E14)
                    ),
                    center = center,
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = center
            )

            // Outer Ring Border with subtle rim glow
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.8f),
                        secondaryGlowColor.copy(alpha = 0.3f),
                        primaryGlowColor.copy(alpha = 0.8f)
                    ),
                    center = center
                ),
                radius = orbRadius,
                center = center,
                style = Stroke(width = 2.2.dp.toPx())
            )

            // ---------------------------------------------------------------
            // D. Rotational Shimmer Arc (Thinking state)
            // ---------------------------------------------------------------
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
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(center.x - orbRadius, center.y - orbRadius),
                        size = Size(orbRadius * 2, orbRadius * 2),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // ---------------------------------------------------------------
            // E. The Living Ally Eyes
            // ---------------------------------------------------------------
            val eyeSpacing = orbRadius * 0.44f
            val eyeY = center.y - (orbRadius * 0.16f)
            val baseEyeWidth = 19.dp.toPx()
            val baseEyeHeight = 30.dp.toPx()

            // Calculate instantaneous blink factor: dips briefly every 4.2s
            val blinkFactor = if (blinkProgress > 0.95f) {
                // In the blink window: interpolate to thin slit
                val normalized = ((blinkProgress - 0.95f) / 0.05f) * 3.14159f
                (1f - sin(normalized)).coerceIn(0.12f, 1f)
            } else {
                1f
            }

            val currentEyeHeight = (baseEyeHeight * eyeHeightFactor * blinkFactor).coerceAtLeast(3.dp.toPx())
            val currentEyeWidth = if (isThinking) baseEyeWidth * 1.25f else baseEyeWidth

            // Left Eye
            val leftEyeCenter = Offset(center.x - eyeSpacing, eyeY)
            rotate(eyeTiltAngle, pivot = leftEyeCenter) {
                if (isConfirming) {
                    // Joyful Crescent Eye (^)
                    val crescentPath = Path().apply {
                        val w = currentEyeWidth * 1.1f
                        val h = currentEyeHeight * 0.8f
                        moveTo(leftEyeCenter.x - w / 2, leftEyeCenter.y + h / 2)
                        quadraticTo(
                            leftEyeCenter.x, leftEyeCenter.y - h / 2,
                            leftEyeCenter.x + w / 2, leftEyeCenter.y + h / 2
                        )
                    }
                    drawPath(
                        crescentPath,
                        color = AllyAmberGlow,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    val rect = RoundRect(
                        left = leftEyeCenter.x - currentEyeWidth / 2,
                        top = leftEyeCenter.y - currentEyeHeight / 2,
                        right = leftEyeCenter.x + currentEyeWidth / 2,
                        bottom = leftEyeCenter.y + currentEyeHeight / 2,
                        cornerRadius = CornerRadius(currentEyeWidth / 2, currentEyeWidth / 2)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White, primaryGlowColor)
                        ),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = rect.topLeftCornerRadius
                    )
                }
            }

            // Right Eye
            val rightEyeCenter = Offset(center.x + eyeSpacing, eyeY)
            rotate(-eyeTiltAngle, pivot = rightEyeCenter) {
                if (isConfirming) {
                    // Joyful Crescent Eye (^)
                    val crescentPath = Path().apply {
                        val w = currentEyeWidth * 1.1f
                        val h = currentEyeHeight * 0.8f
                        moveTo(rightEyeCenter.x - w / 2, rightEyeCenter.y + h / 2)
                        quadraticTo(
                            rightEyeCenter.x, rightEyeCenter.y - h / 2,
                            rightEyeCenter.x + w / 2, rightEyeCenter.y + h / 2
                        )
                    }
                    drawPath(
                        crescentPath,
                        color = AllyAmberGlow,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    val rect = RoundRect(
                        left = rightEyeCenter.x - currentEyeWidth / 2,
                        top = rightEyeCenter.y - currentEyeHeight / 2,
                        right = rightEyeCenter.x + currentEyeWidth / 2,
                        bottom = rightEyeCenter.y + currentEyeHeight / 2,
                        cornerRadius = CornerRadius(currentEyeWidth / 2, currentEyeWidth / 2)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White, primaryGlowColor)
                        ),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = rect.topLeftCornerRadius
                    )
                }
            }

            // ---------------------------------------------------------------
            // F. Expressive Mouth / Audio-Reactive Soundwave Line
            // ---------------------------------------------------------------
            val mouthY = center.y + (orbRadius * 0.38f)

            when {
                // 1. Speaking: Fluid audio-reactive soundwave mouth line
                isSpeaking -> {
                    val waveWidth = orbRadius * 0.92f
                    val waveStartX = center.x - waveWidth / 2
                    val wavePath = Path()
                    val segments = 32
                    for (step in 0..segments) {
                        val fraction = step.toFloat() / segments
                        val x = waveStartX + fraction * waveWidth
                        // Gaussian window envelope so edges taper to zero
                        val envelope = sin(fraction * 3.14159f)
                        val frequency = 3.5f
                        val waveOffset = sin(fraction * frequency * 6.283f + mouthWavePhase) * (14.dp.toPx() * envelope)
                        val y = mouthY + waveOffset
                        if (step == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    }
                    drawPath(
                        wavePath,
                        brush = Brush.horizontalGradient(
                            listOf(
                                AllyAmber.copy(alpha = 0.5f),
                                Color.White,
                                AllyAmberGlow,
                                Color.White,
                                AllyAmber.copy(alpha = 0.5f)
                            )
                        ),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 2. Confirming / Empathetic: Warm amber/gold joyful smile curve
                isConfirming -> {
                    val smileWidth = orbRadius * 0.8f
                    val smilePath = Path().apply {
                        moveTo(center.x - smileWidth / 2, mouthY - 4.dp.toPx())
                        quadraticTo(
                            center.x, mouthY + 16.dp.toPx(),
                            center.x + smileWidth / 2, mouthY - 4.dp.toPx()
                        )
                    }
                    drawPath(
                        smilePath,
                        color = AllyAmberGlow,
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Thinking: Concentrated focus bar with glowing center
                isThinking -> {
                    val barWidth = orbRadius * 0.44f
                    drawLine(
                        color = AllyAmber.copy(alpha = 0.9f),
                        start = Offset(center.x - barWidth / 2, mouthY),
                        end = Offset(center.x + barWidth / 2, mouthY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(center.x, mouthY)
                    )
                }

                // 4. Caution: Alert straight/cautionary line in soft red-amber
                isCaution -> {
                    val warnWidth = orbRadius * 0.65f
                    drawLine(
                        color = AllyWarning,
                        start = Offset(center.x - warnWidth / 2, mouthY + 2.dp.toPx()),
                        end = Offset(center.x + warnWidth / 2, mouthY + 2.dp.toPx()),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 5. Listening: Open attentive curve
                isListening -> {
                    val listenWidth = orbRadius * 0.52f
                    val listenPath = Path().apply {
                        moveTo(center.x - listenWidth / 2, mouthY)
                        quadraticTo(
                            center.x, mouthY + 7.dp.toPx(),
                            center.x + listenWidth / 2, mouthY
                        )
                    }
                    drawPath(
                        listenPath,
                        color = AllyCyan,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 6. Idle: Gentle resting subtle smile
                else -> {
                    val idleWidth = orbRadius * 0.48f
                    val idlePath = Path().apply {
                        moveTo(center.x - idleWidth / 2, mouthY)
                        quadraticTo(
                            center.x, mouthY + 5.dp.toPx(),
                            center.x + idleWidth / 2, mouthY
                        )
                    }
                    drawPath(
                        idlePath,
                        color = Color.White.copy(alpha = 0.75f),
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
