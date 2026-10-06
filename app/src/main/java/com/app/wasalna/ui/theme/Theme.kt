package com.app.wasalna.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elderly mode flag: when true, typography scales up and touch targets grow.
 * Phase 1: simple CompositionLocal, persisted later via DataStore.
 */
val LocalElderlyMode = compositionLocalOf { false }

/** Helper dimens that adapt to elderly mode. */
data class WasalnaDimens(
    val screenPadding: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val buttonHeight: Dp = 48.dp,
    val iconSize: Dp = 24.dp,
    val spacingSmall: Dp = 8.dp,
    val spacingMedium: Dp = 16.dp,
    val spacingLarge: Dp = 24.dp
)

val NormalDimens = WasalnaDimens()
val ElderlyDimens = WasalnaDimens(
    screenPadding = 20.dp,
    cardPadding = 20.dp,
    buttonHeight = 64.dp,
    iconSize = 32.dp,
    spacingSmall = 12.dp,
    spacingMedium = 20.dp,
    spacingLarge = 32.dp
)

val LocalWasalnaDimens = compositionLocalOf { NormalDimens }

@Composable
fun WasalnaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    elderlyMode: Boolean = false,
    content: @Composable () -> Unit
) {
    var elderly by remember(elderlyMode) { mutableStateOf(elderlyMode) }
    val colors = if (darkTheme) WasalnaDarkColors else WasalnaLightColors
    val typography = if (elderly) WasalnaElderlyTypography else WasalnaTypography
    val dimens = if (elderly) ElderlyDimens else NormalDimens

    CompositionLocalProvider(
        LocalElderlyMode provides elderly,
        LocalWasalnaDimens provides dimens
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            content = content
        )
    }
}
