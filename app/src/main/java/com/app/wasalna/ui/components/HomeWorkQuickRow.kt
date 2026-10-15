package com.app.wasalna.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.app.wasalna.R
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.app.wasalna.domain.model.CustomShortcut
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
    // Premium custom shortcuts (user order Oct 2026): user-named cards
    // beside Home & Work. Creation is premium-gated (onUpgradeClick opens
    // the paywall); tap/edit on a card behave exactly like Home/Work.
    customShortcuts: List<CustomShortcut> = emptyList(),
    isPremium: Boolean = false,
    canAddMore: Boolean = true,
    onCustomClick: (CustomShortcut) -> Unit = {},
    onCustomEdit: (CustomShortcut) -> Unit = {},
    onAddCustom: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
) {
    // Single-row horizontal carousel (user order Oct 2026): Home + Work +
    // customs + add-card all in one LazyRow, scrollable both directions
    // with animated chevron arrows on both edges.
    val rowState = rememberLazyListState()
    val rowScope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE")
    val edgeBack = rowState.canScrollBackward
    @Suppress("UNUSED_VARIABLE")
    val edgeFwd = rowState.canScrollForward
    // Chevron nudge animation: arrows slide ±4dp in a loop so users
    // notice the row scrolls both ways.
    val nudge = rememberInfiniteTransition(label = "shortcut-arrow-nudge")
        .animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "nudge",
        )
    val totalItems = 2 + customShortcuts.size + (if (canAddMore || !isPremium) 1 else 0)
    val showArrows = totalItems > 2
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 36.dp),
        ) {
            item(key = "home") {
                QuickPlaceCard(
                    title = stringResource(R.string.quick_home),
                    subtitle = homeAddress.ifBlank { stringResource(R.string.quick_tap_hint) },
                    iconContentDescription = stringResource(R.string.quick_home_desc),
                    onClick = onHomeClick,
                    onLongClick = onHomeEdit,
                    onEditClick = onHomeEdit,
                    modifier = Modifier.width(220.dp),
                )
            }
            item(key = "work") {
                QuickPlaceCard(
                    title = stringResource(R.string.quick_work),
                    subtitle = workAddress.ifBlank { stringResource(R.string.quick_tap_hint) },
                    iconContentDescription = stringResource(R.string.quick_work_desc),
                    onClick = onWorkClick,
                    onLongClick = onWorkEdit,
                    onEditClick = onWorkEdit,
                    modifier = Modifier.width(220.dp),
                    isWork = true,
                )
            }
            itemsIndexed(customShortcuts, key = { i, c -> "custom-$i-" + c.name }) { _, shortcut ->
                QuickPlaceCard(
                    title = shortcut.name.ifBlank { stringResource(R.string.custom_untitled) },
                    subtitle = shortcut.address.ifBlank { stringResource(R.string.quick_tap_hint) },
                    iconContentDescription = shortcut.name,
                    onClick = { onCustomClick(shortcut) },
                    onLongClick = { onCustomEdit(shortcut) },
                    onEditClick = { onCustomEdit(shortcut) },
                    modifier = Modifier.width(220.dp),
                    isCustom = true,
                )
            }
            if (canAddMore || !isPremium) {
                item(key = "add") {
                    CustomAddCard(
                        isPremium = isPremium,
                        onClick = { if (isPremium) onAddCustom() else onUpgradeClick() },
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
        }
        // Left edge arrow — scrolls backward.
        if (showArrows) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = {
                    rowScope.launch {
                        val target = (rowState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                        rowState.animateScrollToItem(target)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = nudge.value.dp)
                    .size(32.dp),
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Right edge arrow — scrolls forward.
        if (showArrows) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = {
                    rowScope.launch {
                        val target = rowState.firstVisibleItemIndex + 1
                        rowState.animateScrollToItem(target)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-nudge.value).dp)
                    .size(32.dp),
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Premium add-shortcut card (user order Oct 2026): ＋ for members,
 * lock + First-Class label for free users (tap opens the paywall).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomAddCard(
    isPremium: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isPremium) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (isPremium) Icons.Filled.Add else Icons.Filled.Lock,
                contentDescription = stringResource(R.string.custom_add),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    if (isPremium) R.string.custom_add
                    else R.string.custom_locked,
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (!isPremium) {
                Icon(
                    Icons.Filled.Diamond,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
    // Custom shortcuts (user order Oct 2026, premium): non-null
    // initialName shows the name field + routes saves through onSaveNamed;
    // onDelete removes the whole card (custom only — Home/Work pass null).
    initialName: String? = null,
    onSaveNamed: ((name: String, lat: Double?, lng: Double?, address: String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var address by remember(title) { mutableStateOf(initialAddress) }
    var latText by remember(title) { mutableStateOf(initialLat?.toString() ?: "") }
    var lngText by remember(title) { mutableStateOf(initialLng?.toString() ?: "") }
    var name by remember(title) { mutableStateOf(initialName ?: "") }
    // Capture-current-location (user order Oct 2026): one tap fills
    // lat/lng from the live GPS fix — no manual typing. Self-contained
    // FusedLocationProviderClient lookup with permission guard,
    // progress state, and a localized error line. Never throws.
    val context = LocalContext.current
    val captureScope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    fun captureCurrent() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            captureError = context.getString(R.string.quick_location_no_perm)
            return
        }
        if (capturing) return
        capturing = true
        captureError = null
        captureScope.launch {
            val fix = runCatching {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(8_000L) {
                        LocationServices.getFusedLocationProviderClient(context)
                            .lastLocation.await()
                    }
                }
            }.getOrNull()
            capturing = false
            if (fix == null) {
                captureError = context.getString(R.string.quick_location_none)
            } else {
                latText = fix.latitude.toString()
                lngText = fix.longitude.toString()
            }
        }
    }
    // رسالة تأكيد قبل مسح نقطة محفوظة: الحفظ بإحداثيات فارغة وكان
    // يوجد نقطة من قبل = مسح لها — يُطلب التأكيد أولًا (امسح).
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Smart Back 2.0 draft guard (user order Oct 2026): system/dialog
    // back with unsaved edits asks first — typed coords are never lost
    // silently. Clean dialogs dismiss immediately, no friction.
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val isDirty = address != initialAddress ||
        latText != (initialLat?.toString() ?: "") ||
        lngText != (initialLng?.toString() ?: "") ||
        (initialName != null && name != initialName)
    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true
        else onDismiss()
    }
    fun persist(lat: Double?, lng: Double?) {
        val saver = onSaveNamed
        if (saver != null && initialName != null) saver(name.trim(), lat, lng, address.trim())
        else onSave(lat, lng, address.trim())
    }
    fun attemptSave() {
        val clearsExisting = (initialLat != null || initialLng != null) &&
            latText.toDoubleOrNull() == null && lngText.toDoubleOrNull() == null
        if (clearsExisting) showClearConfirm = true
        else {
            persist(latText.toDoubleOrNull(), lngText.toDoubleOrNull())
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
                persist(null, null)
                showClearConfirm = false
                onDismiss()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
    if (showDiscardConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.quick_discard_title),
            message = stringResource(R.string.quick_discard_msg),
            confirmLabel = stringResource(R.string.quick_discard_confirm),
            isDestructive = true,
            onConfirm = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismiss = { showDiscardConfirm = false },
        )
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.custom_delete_title, name.ifBlank { title }),
            message = stringResource(R.string.custom_delete_msg, name.ifBlank { title }),
            confirmLabel = stringResource(R.string.custom_delete_confirm),
            isDestructive = true,
            onConfirm = {
                onDelete?.invoke()
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    AlertDialog(
        onDismissRequest = ::requestDismiss,
        title = { Text(stringResource(R.string.quick_set_title, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Custom shortcuts (premium): the card name lives here.
                if (initialName != null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.custom_name_label)) },
                        singleLine = true,
                    )
                }
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
                OutlinedButton(
                    onClick = ::captureCurrent,
                    enabled = !capturing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (capturing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(
                        stringResource(
                            if (capturing) R.string.quick_locating
                            else R.string.quick_use_current,
                        ),
                    )
                }
                captureError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Custom shortcuts only: whole-card delete (Home/Work
                // keep the clear-on-empty flow instead).
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(
                            stringResource(R.string.custom_delete_confirm),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = ::requestDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
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
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWork: Boolean = false,
    isCustom: Boolean = false,
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
                when {
                    isWork -> Icons.Filled.Work
                    isCustom -> Icons.Filled.Star
                    else -> Icons.Filled.Home
                },
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
            // Discoverable edit (user order Oct 2026): long-press still
            // works, but most users never find it — this visible pencil
            // opens the same set-location dialog in one obvious tap.
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.quick_edit_place),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
