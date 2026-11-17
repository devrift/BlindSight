package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Unified Royal / Electric Blue Design System (BlindSight v1.9.2)
// =============================================================================

// Primary Accents
val ElectricCobalt = Color(0xFF1E6FFF)
val VividCyan = Color(0xFF00D2FF)
val CeruleanBlue = Color(0xFF0066FF)
val ElectricBlueGlow = Color(0xFF38BDF8)

// Dark Mode Canvas & Surfaces
val DeepOceanicObsidian = Color(0xFF060B14)
val MidnightBlueGlass = Color(0x990A1934)      // rgba(10, 25, 52, 0.60)
val MidnightBlueGlassElevated = Color(0xB20C1F40) // rgba(12, 31, 64, 0.70)
val LiquidGlassBorder = Color(0x38FFFFFF)         // rgba(255, 255, 255, 0.22)
val LiquidGlassSpecular = Color(0x59FFFFFF)       // rgba(255, 255, 255, 0.35)

// Light Mode Canvas & Surfaces
val PorcelainCanvas = Color(0xFFEAF2FF)
val PorcelainGlass = Color(0xD9FFFFFF)            // rgba(255, 255, 255, 0.85)
val PorcelainGlassBorder = Color(0x331E6FFF)      // rgba(30, 111, 255, 0.20)
val SlateTextDark = Color(0xFF0F172A)
val SlateTextMuted = Color(0xFF475569)

// Universal Text
val TextPrimaryWhite = Color(0xFFFFFFFF)
val TextSecondarySlate = Color(0xFF94A3B8)

// Status Alerts
val AlertWarningRed = Color(0xFFEF4444)
val AlertWarningAmber = Color(0xFFF97316)
val AlertSuccessGreen = Color(0xFF10B981)

// Backward-compatible semantic aliases
val DeepObsidianCanvas = DeepOceanicObsidian
val LiquidGlassBackground = MidnightBlueGlass
val BlindSightBackground = DeepOceanicObsidian
val BlindSightCard = MidnightBlueGlass
val BlindSightCardEdge = LiquidGlassBorder
val BlindSightTextPrimary = TextPrimaryWhite
val BlindSightTextMuted = TextSecondarySlate

val AllyBackground = DeepOceanicObsidian
val AllySurface = MidnightBlueGlass
val AllyBorder = LiquidGlassBorder
val AllyTextPrimary = TextPrimaryWhite
val AllyTextSecondary = TextSecondarySlate
val AllyAmber = ElectricCobalt
val AllyAmberGlow = VividCyan
val AllyCyan = VividCyan
val AllyWarning = AlertWarningRed
val AllyWarningAmber = AlertWarningAmber

val BlindSightAmber = ElectricCobalt
val BlindSightAmberStrong = VividCyan
val BlindSightAmberDark = Color(0xFF0A2E66)
val BlindSightOk = AlertSuccessGreen
val BlindSightOkDark = Color(0xFF064E3B)
val BlindSightBad = AlertWarningRed
val BlindSightBadDark = Color(0xFF450A0A)
val BlindSightYellow = ElectricCobalt
