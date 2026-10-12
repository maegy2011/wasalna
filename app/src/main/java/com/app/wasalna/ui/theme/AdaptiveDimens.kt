package com.app.wasalna.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/**
 * Screen-density adaptivity (Oct 2026 fix): width-first adaptation layered
 * on top of the elderly/normal base dimens — never replacing them.
 *
 * - Compact (<600dp, phones): base dimens untouched, single column.
 * - Medium (600–840dp, foldables/tablets portrait): roomier padding,
 *   two-column lists.
 * - Expanded (>840dp, tablets landscape): widest padding, three columns.
 *
 * The elderly multiplier stays independent: a large-font tablet gets
 * BOTH the width treatment and the elderly scale. On Medium/Expanded the
 * explicit values (not base-relative arithmetic) keep the visual result
 * predictable and screenshot-comparable.
 */

/** Columns for list→grid adaptive layouts. Compact = 1 (legacy look). */
val LocalColumnCount = compositionLocalOf { 1 }

fun adaptiveDimens(
    base: WasalnaDimens,
    widthClass: WindowWidthSizeClass,
): WasalnaDimens = when (widthClass) {
    WindowWidthSizeClass.Compact -> base
    WindowWidthSizeClass.Medium -> base.copy(
        screenPadding = 24.dp,
        cardPadding = 20.dp,
        spacingSmall = 12.dp,
        spacingMedium = 20.dp,
        spacingLarge = 32.dp,
    )
    else -> base.copy(
        screenPadding = 32.dp,
        cardPadding = 24.dp,
        spacingSmall = 12.dp,
        spacingMedium = 24.dp,
        spacingLarge = 40.dp,
    )
}

fun columnsFor(widthClass: WindowWidthSizeClass): Int = when (widthClass) {
    WindowWidthSizeClass.Compact -> 1
    WindowWidthSizeClass.Medium -> 2
    else -> 3
}

@Composable
fun rememberAdaptiveDimens(
    elderly: Boolean,
    widthClass: WindowWidthSizeClass,
): WasalnaDimens {
    val base = if (elderly) ElderlyDimens else NormalDimens
    return remember(base, widthClass) { adaptiveDimens(base, widthClass) }
}
