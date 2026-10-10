package com.app.wasalna.ui.components

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * True-offline-mode header controls (GPS-chip only).
 *
 * - [OfflineModeButton]: cloud/cloud-off IconButton for any TopAppBar. The
 *   icon always reflects the CURRENT mode (cloud = online, cloud-off =
 *   offline); tapping toggles. State comes from the caller (the persisted
 *   DataStore flag exposed as StateFlow — single source of truth, two-way
 *   synced), so every header stays consistent. 48dp minimum touch target,
 *   Arabic contentDescription for accessibility, Material3, no `!!`.
 * - [OfflineModeBanner]: persistent "وضع أوفلاين" indicator chip shown
 *   while offline mode is ON (never a transient toast).
 */
@Composable
fun OfflineModeButton(
    isOffline: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { onToggle(!isOffline) },
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        Icon(
            imageVector = if (isOffline) Icons.Filled.CloudOff else Icons.Filled.Cloud,
            contentDescription = if (isOffline) {
                stringResource(R.string.offline_desc_on)
            } else {
                stringResource(R.string.offline_desc_off)
            },
        )
    }
}

@Composable
fun OfflineModeBanner(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
