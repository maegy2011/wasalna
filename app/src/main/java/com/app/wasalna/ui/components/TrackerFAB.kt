package com.app.wasalna.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.wasalna.ui.theme.LocalWasalnaDimens

/**
 * Transit design system (Oct 2026): prominent Wasalna Green FAB
 * to "Start Tracker". Extended variant with play icon + label,
 * 56dp height (64dp elderly), 16dp corner, 6dp elevation.
 * RTL-safe, 8dp grid, no `!!`.
 */
@Composable
fun TrackerFAB(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = LocalWasalnaDimens.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.height(dimens.fabHeight),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
