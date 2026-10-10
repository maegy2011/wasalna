package com.app.wasalna.ui.components

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Map UX fixes: Home & Work quick-trip row.
 *
 * Shown ONLY on search-mode UI (Map search overlay) and the My Places
 * screen — never on the plain Map view. Tap starts a quick trip
 * (callers gate free-plan interstitial ads first). Long-press (or the
 * edit affordance) opens the set-location dialog.
 *
 * Material3 + 8dp grid + 48dp minimum touch targets, no `!!`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeWorkQuickRow(
    homeAddress: String,
    workAddress: String,
    onHomeClick: () -> Unit,
    onWorkClick: () -> Unit,
    onHomeEdit: () -> Unit,
    onWorkEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickPlaceCard(
            title = stringResource(R.string.quick_home),
            subtitle = homeAddress.ifBlank { stringResource(R.string.quick_tap_hint) },
            iconContentDescription = stringResource(R.string.quick_home_desc),
            onClick = onHomeClick,
            onLongClick = onHomeEdit,
            modifier = Modifier.weight(1f),
        )
        QuickPlaceCard(
            title = stringResource(R.string.quick_work),
            subtitle = workAddress.ifBlank { stringResource(R.string.quick_tap_hint) },
            iconContentDescription = stringResource(R.string.quick_work_desc),
            onClick = onWorkClick,
            onLongClick = onWorkEdit,
            modifier = Modifier.weight(1f),
            isWork = true,
        )
    }
}

/**
 * Set-location dialog shared by search-mode and My Places callers.
 * Blank lat/lng clears the saved point (address kept as label).
 */
@Composable
fun QuickPlaceEditDialog(
    title: String,
    initialAddress: String,
    initialLat: Double?,
    initialLng: Double?,
    onDismiss: () -> Unit,
    onSave: (lat: Double?, lng: Double?, address: String) -> Unit,
) {
    var address by remember(title) { mutableStateOf(initialAddress) }
    var latText by remember(title) { mutableStateOf(initialLat?.toString() ?: "") }
    var lngText by remember(title) { mutableStateOf(initialLng?.toString() ?: "") }
    // رسالة تأكيد قبل مسح نقطة محفوظة: الحفظ بإحداثيات فارغة وكان
    // يوجد نقطة من قبل = مسح لها — يُطلب التأكيد أولًا (امسح).
    var showClearConfirm by remember { mutableStateOf(false) }
    fun attemptSave() {
        val clearsExisting = (initialLat != null || initialLng != null) &&
            latText.toDoubleOrNull() == null && lngText.toDoubleOrNull() == null
        if (clearsExisting) showClearConfirm = true
        else {
            onSave(latText.toDoubleOrNull(), lngText.toDoubleOrNull(), address.trim())
            onDismiss()
        }
    }
    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.quick_clear_title, title),
            message = stringResource(R.string.quick_clear_msg, title),
            confirmLabel = stringResource(R.string.quick_clear_confirm),
            isDestructive = true,
            onConfirm = {
                onSave(null, null, address.trim())
                showClearConfirm = false
                onDismiss()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_set_title, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.common_address_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text(stringResource(R.string.common_latitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text(stringResource(R.string.common_longitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.quick_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { attemptSave() }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickPlaceCard(
    title: String,
    subtitle: String,
    iconContentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWork: Boolean = false,
) {
    Card(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (isWork) Icons.Filled.Work else Icons.Filled.Home,
                contentDescription = iconContentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
