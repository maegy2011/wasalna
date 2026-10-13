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

// Brand palette (Oct 2026, user order): coolors navy set —
// #FFFFFF · #00171F · #003459 · #007EA7 · #00A8E8. Full M3 token
// coverage (surfaces/outlines/containers) so every screen follows
// the same azure language. Elderly high-contrast schemes unchanged.
val NavyDeep = Color(0xFF00171F)
val Navy = Color(0xFF003459)
val Azure = Color(0xFF007EA7)
val AzureBright = Color(0xFF00A8E8)

val WasalnaLightColors = lightColorScheme(
    primary = Azure,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EDF9),
    onPrimaryContainer = Navy,
    secondary = AzureBright,
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFFE0F4FD),
    onSecondaryContainer = Navy,
    tertiary = Navy,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE8F0),
    onTertiaryContainer = NavyDeep,
    background = Color(0xFFF7FBFD),
    onBackground = NavyDeep,
    surface = Color.White,
    onSurface = NavyDeep,
    surfaceVariant = Color(0xFFDCE9F0),
    onSurfaceVariant = Color(0xFF3F4A52),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F8FB),
    surfaceContainer = Color(0xFFE9F2F7),
    surfaceContainerHigh = Color(0xFFDEE9EF),
    outline = Color(0xFF6E7E87),
    outlineVariant = Color(0xFFBCCAD2),
    error = WasalnaError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val WasalnaDarkColors = darkColorScheme(
    primary = Color(0xFF7CCBF0),
    onPrimary = Color(0xFF00364D),
    primaryContainer = Azure,
    onPrimaryContainer = Color(0xFFD3EDF9),
    secondary = AzureBright,
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFF0E4A63),
    onSecondaryContainer = Color(0xFFE0F4FD),
    tertiary = Color(0xFF9FBFD6),
    onTertiary = Navy,
    tertiaryContainer = Navy,
    onTertiaryContainer = Color(0xFFDCE8F0),
    background = NavyDeep,
    onBackground = Color(0xFFDDE8EE),
    surface = Color(0xFF0A2029),
    onSurface = Color(0xFFDDE8EE),
    surfaceVariant = Color(0xFF3F4A52),
    onSurfaceVariant = Color(0xFFBCCAD2),
    surfaceContainerLowest = Color(0xFF000F14),
    surfaceContainerLow = Color(0xFF0F2831),
    surfaceContainer = Color(0xFF14313C),
    surfaceContainerHigh = Color(0xFF1B3D4A),
    outline = Color(0xFF86949C),
    outlineVariant = Color(0xFF3F4A52),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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
