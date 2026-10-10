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
import com.app.wasalna.domain.model.TransitPlan

/**
 * Live transit plan card (BYOK key present): walk + ride steps with
 * lines/stops/times straight from the Routes API v2 response.
 * Arabic-first, RTL-safe, 8dp grid, 48dp+ rows implied by padding.
 */
@Composable
fun TransitLiveCard(
    plan: TransitPlan,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.transit_live_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            plan.steps.forEach { step ->
                if (step.isRide) {
                    val line = step.lineLabel().ifBlank { "—" }
                    Text(
                        text = stringResource(
                            R.string.transit_ride_step,
                            line,
                            step.departStop ?: "—",
                            step.arriveStop ?: "—",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(
                            R.string.transit_ride_meta,
                            step.departTime ?: "—",
                            step.arriveTime ?: "—",
                            step.stopCount ?: 0,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else if (step.distanceM > 1.0) {
                    val leg = if (step.distanceM >= 1000.0) {
                        stringResource(
                            R.string.transit_walk_leg,
                            String.format(java.util.Locale.US, "%.1f", step.distanceM / 1000.0),
                        )
                    } else {
                        stringResource(
                            R.string.transit_walk_leg_m,
                            step.distanceM.toInt().toString(),
                        )
                    }
                    Text(
                        text = stringResource(R.string.transit_walk_step, leg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

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
