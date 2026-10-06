package com.app.wasalna.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val WasalnaGreen = Color(0xFF0D7C66)
val WasalnaGreenDark = Color(0xFF0A5F4F)
val WasalnaGreenLight = Color(0xFFBFE3D8)
val WasalnaAmber = Color(0xFFF2A007)
val WasalnaAmberDark = Color(0xFFB87700)
val WasalnaBackground = Color(0xFFF7F5F0)
val WasalnaSurface = Color(0xFFFFFFFF)
val WasalnaOnPrimary = Color(0xFFFFFFFF)
val WasalnaError = Color(0xFFBA1A1A)

val WasalnaLightColors = lightColorScheme(
    primary = WasalnaGreen,
    onPrimary = WasalnaOnPrimary,
    primaryContainer = WasalnaGreenLight,
    onPrimaryContainer = WasalnaGreenDark,
    secondary = WasalnaAmber,
    onSecondary = Color(0xFF201A00),
    background = WasalnaBackground,
    surface = WasalnaSurface,
    error = WasalnaError
)

val WasalnaDarkColors = darkColorScheme(
    primary = Color(0xFF6FD3B8),
    onPrimary = Color(0xFF00382B),
    primaryContainer = WasalnaGreenDark,
    onPrimaryContainer = WasalnaGreenLight,
    secondary = WasalnaAmber,
    onSecondary = Color(0xFF201A00),
    background = Color(0xFF101414),
    surface = Color(0xFF1A1F1D),
    error = Color(0xFFFFB4AB)
)
