package com.app.wasalna.ui.components

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.wasalna.utils.DestinationShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 10.11: destination share sheet (Arabic).
 *
 * Entry: "مشاركة الوجهة" from trip/destination screens. Lists ONLY
 * installed targets — re-queried on EVERY open via
 * [DestinationShare.queryTargets] (PackageManager, Dispatchers.IO), so
 * installs/uninstalls are reflected immediately.
 *
 * Reuses the caller's destination lat/lng + address state (no duplicated
 * storage). Material3, 48dp+ rows, no `!!`, Arabic empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationShareSheet(
    lat: Double,
    lng: Double,
    label: String,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var loading by remember { mutableStateOf(true) }
    var targets by remember { mutableStateOf<List<DestinationShare.Target>>(emptyList()) }

    // Re-query on every sheet open (fresh composition per open — the
    // caller shows this conditionally, so LaunchedEffect re-runs).
    LaunchedEffect(lat, lng) {
        loading = true
        val found = withContext(Dispatchers.IO) {
            runCatching {
                DestinationShare.queryTargets(context, lat, lng, label)
            }.getOrDefault(emptyList())
        }
        targets = found
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.share_destination),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = label.ifBlank { stringResource(R.string.share_coords, lat.toString(), lng.toString()) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
            )
            when {
                loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }
                targets.isEmpty() -> {
                    // Empty state (Arabic): no maps/ride app installed.
                    Text(
                        text = stringResource(R.string.share_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    LazyColumn {
                        items(targets, key = { it.id }) { target ->
                            ListItem(
                                headlineContent = { Text(target.label) },
                                supportingContent = { Text(target.hint) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable {
                                        val ok = DestinationShare.launch(
                                            context, target, lat, lng, label,
                                        )
                                        if (ok) {
                                            onDismiss()
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.share_open_failed, target.label),
                                                )
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
