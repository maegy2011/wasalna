package com.app.wasalna.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Base typography (normal mode). Elderly mode scales key styles up.
 * Design spec: clean geometric sans — Inter / Roboto with Cairo for
 * Arabic. FontFamily.Default resolves to Roboto on Android with system
 * Arabic fallback (Cairo-compatible), so no bundled font needed. */
val WasalnaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

/** Elderly typography: bodyLarge 24sp minimum, larger touch-friendly scale. */
val WasalnaElderlyTypography = Typography(
    displayLarge = WasalnaTypography.displayLarge.copy(fontSize = 64.sp, lineHeight = 72.sp),
    headlineMedium = WasalnaTypography.headlineMedium.copy(fontSize = 34.sp, lineHeight = 42.sp),
    titleLarge = WasalnaTypography.titleLarge.copy(fontSize = 28.sp, lineHeight = 36.sp),
    bodyLarge = WasalnaTypography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
    bodyMedium = WasalnaTypography.bodyMedium.copy(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = WasalnaTypography.labelLarge.copy(fontSize = 20.sp, lineHeight = 26.sp)
)

/**
 * Transit design system (Oct 2026): station names + ETA hero.
 * Extra-large bold so a waking user reads them instantly.
 * Standalone styles (M3 Typography has no custom slots).
 */
val StopDisplayStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 40.sp
)

val EtaDisplayStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 48.sp
)

val ElderlyStopDisplayStyle = StopDisplayStyle.copy(fontSize = 40.sp, lineHeight = 48.sp)
val ElderlyEtaDisplayStyle = EtaDisplayStyle.copy(fontSize = 48.sp, lineHeight = 56.sp)

/**
 * Hero-text guard (Oct 2026 density fix): caps the SYSTEM fontScale for
 * display-size titles only (station names, ETA hero). Body/label text
 * keeps following the user's accessibility setting untouched.
 *
 * Pair with `maxLines + overflow = Ellipsis` at the call site so a
 * 320dp-wide screen can never break the layout: scaled-down text that
 * still doesn't fit truncates gracefully instead of overflowing.
 */
@Composable
fun rememberHeroStyle(base: TextStyle, maxScale: Float = 1.3f): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    return remember(base, fontScale) {
        if (fontScale <= maxScale) {
            base
        } else {
            val factor = maxScale / fontScale
            base.copy(
                fontSize = base.fontSize * factor,
                lineHeight = base.lineHeight * factor,
            )
        }
    }
}

/**
 * Display-size title with the hero guard baked in: fontScale-capped
 * style + 2-line ellipsis. Replaces raw Text() for station names and
 * the ETA hero so small/dense screens can never break the layout.
 */
@Composable
fun HeroText(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        style = rememberHeroStyle(baseStyle),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
