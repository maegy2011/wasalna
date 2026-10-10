package com.app.wasalna.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.wasalna.R

/**
 * TRANSIT honesty UI (Oct 2026, outcome b APPROXIMATE): every transit
 * guidance surface carries the approximate label — NEVER presented as a
 * real timetable route. Arabic-first, RTL-safe (Column/Arrangement
 * only), 8dp grid, text ≥ bodySmall.
 */
@Composable
fun TransitApproxNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.transit_approx_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Nearest boarding/alighting card: walk → station → ride → station →
 * walk. Km strings are pre-formatted by the caller (locale-safe).
 */
@Composable
fun TransitGuideCard(
    boardingText: String?,
    alightingText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (boardingText != null) {
                Text(
                    text = boardingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = alightingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TransitApproxNote()
        }
    }
}
