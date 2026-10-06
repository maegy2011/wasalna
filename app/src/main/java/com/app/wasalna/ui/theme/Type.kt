package com.app.wasalna.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Base typography (normal mode). Elderly mode scales key styles up. */
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
