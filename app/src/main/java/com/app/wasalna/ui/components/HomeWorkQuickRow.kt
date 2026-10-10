package com.app.wasalna.ui.components

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
            title = "Home",
            subtitle = homeAddress.ifBlank { "Tap to start · hold to set" },
            iconContentDescription = "Home quick trip",
            onClick = onHomeClick,
            onLongClick = onHomeEdit,
            modifier = Modifier.weight(1f),
        )
        QuickPlaceCard(
            title = "Work",
            subtitle = workAddress.ifBlank { "Tap to start · hold to set" },
            iconContentDescription = "Work quick trip",
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
            title = "مسح نقطة $title؟",
            message = "لديك نقطة $title محفوظة — الحفظ فارغًا سيمسحها وستحتاج لإدخالها من جديد.",
            confirmLabel = "امسح",
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
        title = { Text("Set $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address label") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tip: search or tap the map first, then long-press here to prefill.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { attemptSave() }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
