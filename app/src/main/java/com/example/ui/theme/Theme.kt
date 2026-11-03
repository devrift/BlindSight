package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BlindSightColorScheme = darkColorScheme(
    primary = BlindSightAmber,
    onPrimary = BlindSightBackground,
    primaryContainer = BlindSightAmberDark,
    onPrimaryContainer = BlindSightAmberStrong,
    secondary = BlindSightAmberStrong,
    onSecondary = BlindSightBackground,
    background = BlindSightBackground,
    onBackground = BlindSightTextPrimary,
    surface = BlindSightCard,
    onSurface = BlindSightTextPrimary,
    surfaceVariant = BlindSightCardEdge,
    onSurfaceVariant = BlindSightTextMuted,
    error = BlindSightBad,
    onError = BlindSightBackground,
    errorContainer = BlindSightBadDark,
    onErrorContainer = BlindSightTextPrimary
)

@Composable
fun BlindSightTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlindSightColorScheme,
        typography = Typography,
        content = content
    )
}
