package com.app.wasalna.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val WasalnaGreen = Color(0xFF0D7C66)
val WasalnaGreenDark = Color(0xFF0A5F4F)
val WasalnaGreenLight = Color(0xFFBFE3D8)
val WasalnaAmber = Color(0xFFF2A007)
val WasalnaAmberDark = Color(0xFFB87700)
val WasalnaBackground = Color(0xFFF8F9FA)
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

// Elderly high-contrast (user order Oct 2026): pure black-on-white text,
// darker primary, solid containers — no pastels, no low-contrast greys.
val WasalnaElderlyLightColors = lightColorScheme(
    primary = Color(0xFF0A4D2E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A4D2E),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF7A4A00),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF1A1A1A),
    outline = Color.Black,
    error = Color(0xFF8B0000),
    onError = Color.White
)

val WasalnaElderlyDarkColors = darkColorScheme(
    primary = Color(0xFF7DFFCE),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF7DFFCE),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFFFFC94D),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color.White,
    outline = Color.White,
    error = Color(0xFFFF8A80),
    onError = Color.Black
)
