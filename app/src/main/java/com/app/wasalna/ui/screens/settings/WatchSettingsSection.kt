package com.app.wasalna.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.wasalna.R
import com.app.wasalna.data.wear.WatchAlertSender

/**
 * Phase 10.10: "الساعة الذكية" settings section (Arabic-first).
 *
 * - Connection status via CapabilityClient node discovery (refresh + on-open).
 * - Test-vibration button (48dp+ target, disabled while busy / not connected).
 * - Watch-only alert toggle, PREMIUM-gated: free users see an upsell card
 *   pointing at the Paywall instead of the toggle.
 * - Graceful states: no watch paired / watch app missing / free plan, each
 *   with a clear Arabic explanation + action.
 */
@Composable
fun WatchSettingsSection(
    viewModel: SettingsViewModel,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isPremium by viewModel.isPremium.collectAsState()
    val watchOnly by viewModel.watchOnlyAlert.collectAsState()
    val status by viewModel.watchStatus.collectAsState()
    val testBusy by viewModel.watchTestBusy.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshWatchStatus()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Watch,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.settings_watch_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(
                    onClick = viewModel::refreshWatchStatus,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.settings_watch_refresh),
                    )
                }
            }

            when (status) {
                WatchAlertSender.WatchStatus.CHECKING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.settings_watch_checking),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                WatchAlertSender.WatchStatus.CONNECTED -> {
                    Text(
                        text = stringResource(R.string.settings_watch_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                WatchAlertSender.WatchStatus.APP_MISSING -> {
                    Text(
                        text = stringResource(R.string.settings_watch_app_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                WatchAlertSender.WatchStatus.NO_WATCH -> {
                    Text(
                        text = stringResource(R.string.settings_watch_no_watch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                WatchAlertSender.WatchStatus.UNKNOWN -> {
                    Text(
                        text = stringResource(R.string.settings_watch_unknown),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    viewModel.sendWatchTestVibration { delivered ->
                        val msg = if (delivered) {
                            context.getString(R.string.settings_watch_test_ok)
                        } else {
                            context.getString(R.string.settings_watch_test_fail)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !testBusy &&
                    status == WatchAlertSender.WatchStatus.CONNECTED,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    Icons.Filled.Vibration,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text = if (testBusy) {
                        stringResource(R.string.settings_watch_test_busy)
                    } else {
                        stringResource(R.string.settings_watch_test)
                    },
                )
            }

            if (!isPremium) {
                // Free plan → upsell card pointing at the Paywall (reuses the
                // existing paywall flow, no duplicate purchase logic).
                OutlinedButton(
                    onClick = onOpenPaywall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(text = stringResource(R.string.settings_watch_upsell))
                }
                Text(
                    text = stringResource(R.string.settings_watch_premium_only),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_watch_only_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.settings_watch_only_sub),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = watchOnly,
                        enabled = status == WatchAlertSender.WatchStatus.CONNECTED,
                        onCheckedChange = viewModel::setWatchOnlyAlert,
                    )
                }
                if (status != WatchAlertSender.WatchStatus.CONNECTED) {
                    Text(
                        text = stringResource(R.string.settings_watch_only_needs_watch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Button(
                        onClick = { viewModel.setWatchOnlyAlert(!watchOnly) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = if (watchOnly) {
                                stringResource(R.string.settings_watch_only_on)
                            } else {
                                stringResource(R.string.settings_watch_only_off)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = stringResource(R.string.settings_watch_fallback_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
