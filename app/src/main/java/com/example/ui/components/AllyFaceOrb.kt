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
import com.example.ui.theme.AlertWarningAmber
import com.example.ui.theme.AlertWarningRed
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.MidnightBlueGlass
import com.example.ui.theme.VividCyan
import kotlin.math.sin

/**
 * Animated Ally Smiley Companion Face.
 *
 * An expressive, warm, and tactile presence for blind and low-vision users:
 * - IDLE: Friendly calm eyes with natural periodic blinks and relaxed smile curve.
 * - LISTENING: Eyes widen with curious upward tilt; dynamic concentric audio ripples.
 * - THINKING: Eyes narrow into focused squint; soft horizontal shimmer across brow.
 * - SPEAKING: Fluid audio-reactive soundwave mouth line undulating with speech amplitude.
 * - CONFIRMING: Joyful smiling crescent eyes and radiant warm smile curve.
 * - CAUTION: Alert warning state with soft red-amber aura.
 * - PAUSED / SLEEPING: Relaxed sleepy curved arcs (⌒ ⌒) with calm resting flat mouth.
 */
@Composable
fun AllyFaceOrb(
    appState: AppState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 240.dp,
    isDarkMode: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AllySmileyTransition")

    // 1. Organic Breathing Pulse (scale 0.97 to 1.03)
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // 2. Natural Blink Cycle (~4.2s interval with quick 160ms dip)
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blinkProgress"
    )

    // 3. Concentric Wave Phase (Listening)
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    // 4. Shimmer Phase (Thinking)
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )

    // 5. Soundwave Undulation Phase (Speaking)
    val mouthWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mouthWavePhase"
    )

    // State Classification
    val isListening = appState == AppState.WAKE_LISTENING || appState == AppState.COMMAND_LISTENING
    val isThinking = appState == AppState.THINKING
    val isSpeaking = appState == AppState.SPEAKING
    val isConfirming = appState == AppState.CONFIRMING
    val isCaution = appState == AppState.CAUTION
    val isPaused = appState == AppState.STANDBY_PAUSED

    // Dynamic Colors based on State and Light/Dark Mode
    val primaryGlow by animateColorAsState(
        targetValue = when {
            isCaution -> AlertWarningRed
            isConfirming -> VividCyan
            isListening -> VividCyan
            isThinking -> ElectricCobalt
            isSpeaking -> VividCyan
            isPaused -> Color(0xFF475569)
            else -> if (isDarkMode) ElectricCobalt else Color(0xFF0066FF)
        },
        animationSpec = tween(350),
        label = "primaryGlow"
    )

    val secondaryGlow by animateColorAsState(
        targetValue = when {
            isCaution -> AlertWarningAmber
            isPaused -> Color(0xFF1E293B)
            else -> ElectricCobalt.copy(alpha = 0.5f)
        },
        animationSpec = tween(350),
        label = "secondaryGlow"
    )

    // Dynamic Eye Dimensions and Tilts
    val eyeTiltAngle by animateFloatAsState(
        targetValue = when {
            isListening -> 10f   // Curious upward tilt
            isThinking -> -6f   // Focused squint inward tilt
            else -> 0f
        },
        animationSpec = tween(300),
        label = "eyeTiltAngle"
    )

    val eyeHeightFactor by animateFloatAsState(
        targetValue = when {
            isThinking -> 0.32f
            isConfirming -> 0.7f
            isCaution -> 1.25f
            isListening -> 1.2f
            isSpeaking -> 1.15f
            isPaused -> 0.2f
            else -> 1.0f
        },
        animationSpec = tween(300),
        label = "eyeHeightFactor"
    )

    val eyeWidthFactor by animateFloatAsState(
        targetValue = when {
            isThinking -> 1.4f
            isListening -> 1.15f
            isPaused -> 1.25f
            else -> 1.0f
        },
        animationSpec = tween(300),
        label = "eyeWidthFactor"
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
            val baseRadius = (size.minDimension / 2f) * 0.74f
            val orbRadius = baseRadius * (if (isListening || isSpeaking) breathingScale else 1.0f)

            // -----------------------------------------------------------------
            // 1. Concentric Wave Ripples (Listening State)
            // -----------------------------------------------------------------
            if (isListening) {
                for (i in 0..2) {
                    val progress = (ripplePhase + i * 0.33f) % 1f
                    val rippleR = orbRadius + progress * (size.minDimension * 0.24f)
                    val alpha = (1f - progress).coerceIn(0f, 0.45f)
                    drawCircle(
                        color = VividCyan.copy(alpha = alpha),
                        radius = rippleR,
                        center = center,
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                }
            }

            // -----------------------------------------------------------------
            // 2. Ambient Volumetric Glow Halo
            // -----------------------------------------------------------------
            val glowRadius = orbRadius * (if (isSpeaking) 1.5f else 1.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = if (isSpeaking) 0.5f else if (isPaused) 0.15f else 0.35f),
                        secondaryGlow.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // -----------------------------------------------------------------
            // 3. True Liquid Glass Orb Core (VisionOS Style)
            // -----------------------------------------------------------------
            val glassBodyColors = if (isDarkMode) {
                listOf(
                    Color(0xFF0F2752),
                    Color(0xFF0A1934),
                    Color(0xFF060B14)
                )
            } else {
                listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFE2EDFF),
                    Color(0xFFC7DEFF)
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = glassBodyColors,
                    center = Offset(center.x - orbRadius * 0.25f, center.y - orbRadius * 0.3f),
                    radius = orbRadius * 1.25f
                ),
                radius = orbRadius,
                center = center
            )

            // Outer Liquid Glass Specular Rim Border
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.9f),
                        secondaryGlow.copy(alpha = 0.35f),
                        primaryGlow.copy(alpha = 0.9f)
                    ),
                    center = center
                ),
                radius = orbRadius,
                center = center,
                style = Stroke(width = 2.4.dp.toPx())
            )

            // Top Specular Caustic Reflection Arc
            val specularOffset = Offset(center.x, center.y - orbRadius * 0.45f)
            val specularWidth = orbRadius * 0.82f
            val specularHeight = orbRadius * 0.25f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDarkMode) 0.4f else 0.7f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = specularOffset,
                    radius = specularWidth / 2f
                ),
                topLeft = Offset(specularOffset.x - specularWidth / 2f, specularOffset.y - specularHeight / 2f),
                size = Size(specularWidth, specularHeight)
            )

            // -----------------------------------------------------------------
            // 4. Horizontal Shimmer Across Brow (Thinking State)
            // -----------------------------------------------------------------
            if (isThinking) {
                val browY = center.y - (orbRadius * 0.35f)
                val browWidth = orbRadius * 0.85f
                val shimmerX = center.x + shimmerPhase * (browWidth / 2f)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            VividCyan.copy(alpha = 0.85f),
                            Color.White,
                            VividCyan.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        startX = shimmerX - 30.dp.toPx(),
                        endX = shimmerX + 30.dp.toPx()
                    ),
                    start = Offset(center.x - browWidth / 2f, browY),
                    end = Offset(center.x + browWidth / 2f, browY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // -----------------------------------------------------------------
            // 5. The Ally Smiley Eyes
            // -----------------------------------------------------------------
            val eyeSpacing = orbRadius * 0.42f
            val eyeY = center.y - (orbRadius * 0.12f)
            val baseEyeWidth = 17.dp.toPx()
            val baseEyeHeight = 28.dp.toPx()

            // Calculate instantaneous natural blink factor
            val blinkFactor = if (blinkProgress > 0.96f) {
                val norm = ((blinkProgress - 0.96f) / 0.04f) * 3.14159f
                (1f - sin(norm)).coerceIn(0.12f, 1f)
            } else 1f

            val currentEyeHeight = (baseEyeHeight * eyeHeightFactor * blinkFactor).coerceAtLeast(3.dp.toPx())
            val currentEyeWidth = baseEyeWidth * eyeWidthFactor

            val eyeFillColor = if (isDarkMode) Color.White else Color(0xFF0F172A)

            // Left Eye
            val leftEyeCenter = Offset(center.x - eyeSpacing, eyeY)
            rotate(eyeTiltAngle, pivot = leftEyeCenter) {
                if (isPaused) {
                    // Relaxed, sleepy curved arc (⌒)
                    val arcPath = Path().apply {
                        val w = currentEyeWidth * 1.3f
                        moveTo(leftEyeCenter.x - w / 2, leftEyeCenter.y + 4.dp.toPx())
                        quadraticTo(
                            leftEyeCenter.x, leftEyeCenter.y - 7.dp.toPx(),
                            leftEyeCenter.x + w / 2, leftEyeCenter.y + 4.dp.toPx()
                        )
                    }
                    drawPath(
                        arcPath,
                        color = VividCyan.copy(alpha = 0.85f),
                        style = Stroke(width = 3.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else if (isConfirming) {
                    // Joyful Crescent Eye (^)
                    val crescentPath = Path().apply {
                        val w = currentEyeWidth * 1.15f
                        moveTo(leftEyeCenter.x - w / 2, leftEyeCenter.y + 6.dp.toPx())
                        quadraticTo(
                            leftEyeCenter.x, leftEyeCenter.y - 8.dp.toPx(),
                            leftEyeCenter.x + w / 2, leftEyeCenter.y + 6.dp.toPx()
                        )
                    }
                    drawPath(
                        crescentPath,
                        color = VividCyan,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    // Friendly Rounded Eye Pill
                    val rect = RoundRect(
                        left = leftEyeCenter.x - currentEyeWidth / 2f,
                        top = leftEyeCenter.y - currentEyeHeight / 2f,
                        right = leftEyeCenter.x + currentEyeWidth / 2f,
                        bottom = leftEyeCenter.y + currentEyeHeight / 2f,
                        cornerRadius = CornerRadius(currentEyeWidth / 2f, currentEyeWidth / 2f)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(eyeFillColor, eyeFillColor.copy(alpha = 0.9f))
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
                if (isPaused) {
                    // Relaxed, sleepy curved arc (⌒)
                    val arcPath = Path().apply {
                        val w = currentEyeWidth * 1.3f
                        moveTo(rightEyeCenter.x - w / 2, rightEyeCenter.y + 4.dp.toPx())
                        quadraticTo(
                            rightEyeCenter.x, rightEyeCenter.y - 7.dp.toPx(),
                            rightEyeCenter.x + w / 2, rightEyeCenter.y + 4.dp.toPx()
                        )
                    }
                    drawPath(
                        arcPath,
                        color = VividCyan.copy(alpha = 0.85f),
                        style = Stroke(width = 3.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else if (isConfirming) {
                    // Joyful Crescent Eye (^)
                    val crescentPath = Path().apply {
                        val w = currentEyeWidth * 1.15f
                        moveTo(rightEyeCenter.x - w / 2, rightEyeCenter.y + 6.dp.toPx())
                        quadraticTo(
                            rightEyeCenter.x, rightEyeCenter.y - 8.dp.toPx(),
                            rightEyeCenter.x + w / 2, rightEyeCenter.y + 6.dp.toPx()
                        )
                    }
                    drawPath(
                        crescentPath,
                        color = VividCyan,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    // Friendly Rounded Eye Pill
                    val rect = RoundRect(
                        left = rightEyeCenter.x - currentEyeWidth / 2f,
                        top = rightEyeCenter.y - currentEyeHeight / 2f,
                        right = rightEyeCenter.x + currentEyeWidth / 2f,
                        bottom = rightEyeCenter.y + currentEyeHeight / 2f,
                        cornerRadius = CornerRadius(currentEyeWidth / 2f, currentEyeWidth / 2f)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(eyeFillColor, eyeFillColor.copy(alpha = 0.9f))
                        ),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = rect.topLeftCornerRadius
                    )
                }
            }

            // -----------------------------------------------------------------
            // 6. Dynamic Mouth Curve / Soundwave Waveform
            // -----------------------------------------------------------------
            val mouthY = center.y + (orbRadius * 0.36f)

            when {
                // Speaking: Fluid audio-reactive undulating soundwave line
                isSpeaking -> {
                    val waveWidth = orbRadius * 0.92f
                    val waveStartX = center.x - waveWidth / 2f
                    val wavePath = Path()
                    val segments = 32
                    for (step in 0..segments) {
                        val frac = step.toFloat() / segments
                        val x = waveStartX + frac * waveWidth
                        val envelope = sin(frac * 3.14159f)
                        val waveOffset = sin(frac * 3.5f * 6.283f + mouthWavePhase) * (13.dp.toPx() * envelope)
                        val y = mouthY + waveOffset
                        if (step == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    }
                    drawPath(
                        wavePath,
                        brush = Brush.horizontalGradient(
                            listOf(
                                VividCyan.copy(alpha = 0.5f),
                                Color.White,
                                VividCyan,
                                Color.White,
                                VividCyan.copy(alpha = 0.5f)
                            )
                        ),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // Paused / Sleeping: Calm flat resting mouth line
                isPaused -> {
                    val mouthWidth = orbRadius * 0.42f
                    drawLine(
                        color = Color(0xFF64748B),
                        start = Offset(center.x - mouthWidth / 2f, mouthY),
                        end = Offset(center.x + mouthWidth / 2f, mouthY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Confirming: Joyful warm smile curve
                isConfirming -> {
                    val smileWidth = orbRadius * 0.78f
                    val smilePath = Path().apply {
                        moveTo(center.x - smileWidth / 2f, mouthY - 4.dp.toPx())
                        quadraticTo(
                            center.x, mouthY + 16.dp.toPx(),
                            center.x + smileWidth / 2f, mouthY - 4.dp.toPx()
                        )
                    }
                    drawPath(
                        smilePath,
                        color = VividCyan,
                        style = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Thinking: Focused horizontal line with center spark
                isThinking -> {
                    val barWidth = orbRadius * 0.44f
                    drawLine(
                        color = VividCyan.copy(alpha = 0.9f),
                        start = Offset(center.x - barWidth / 2f, mouthY),
                        end = Offset(center.x + barWidth / 2f, mouthY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(center.x, mouthY)
                    )
                }

                // Caution: Alert straight warning line
                isCaution -> {
                    val warnWidth = orbRadius * 0.65f
                    drawLine(
                        color = AlertWarningRed,
                        start = Offset(center.x - warnWidth / 2f, mouthY + 2.dp.toPx()),
                        end = Offset(center.x + warnWidth / 2f, mouthY + 2.dp.toPx()),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Listening: Attentive gentle curve
                isListening -> {
                    val listenWidth = orbRadius * 0.52f
                    val listenPath = Path().apply {
                        moveTo(center.x - listenWidth / 2f, mouthY)
                        quadraticTo(
                            center.x, mouthY + 8.dp.toPx(),
                            center.x + listenWidth / 2f, mouthY
                        )
                    }
                    drawPath(
                        listenPath,
                        color = VividCyan,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Idle: Warm, relaxed friendly smile curve
                else -> {
                    val idleWidth = orbRadius * 0.5f
                    val idlePath = Path().apply {
                        moveTo(center.x - idleWidth / 2f, mouthY)
                        quadraticTo(
                            center.x, mouthY + 8.dp.toPx(),
                            center.x + idleWidth / 2f, mouthY
                        )
                    }
                    drawPath(
                        idlePath,
                        color = if (isDarkMode) Color.White.copy(alpha = 0.9f) else Color(0xFF0F172A),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
