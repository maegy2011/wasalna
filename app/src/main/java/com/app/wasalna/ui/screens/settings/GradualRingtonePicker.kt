package com.app.wasalna.ui.screens.settings

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app alarm-tone picker for the gradual alarm: lists every available
 * TYPE_ALARM ringtone (RingtoneManager cursor, read on Dispatchers.IO).
 * Tap previews the tone; Confirm persists the URI string in DataStore.
 * When the device exposes no alarm tones, falls back to a small bundled
 * list (system default alarm + notification) so the picker never opens
 * empty. Preview playback is released on dismiss (no leaks).
 */
private data class ToneOption(
    val title: String,
    val uri: Uri,
)

@Composable
fun GradualRingtonePickerDialog(
    currentUri: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var tones by remember { mutableStateOf<List<ToneOption>?>(null) }
    var selected by remember(currentUri) { mutableStateOf(currentUri) }

    LaunchedEffect(Unit) {
        tones = loadAlarmTones(context)
        if (selected.isBlank()) {
            selected = tones?.firstOrNull()?.uri?.toString().orEmpty()
        }
    }

    // Stop any preview when the dialog leaves the composition.
    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
        }
    }

    AlertDialog(
        onDismissRequest = {
            stopPreview()
            onDismiss()
        },
        title = { Text(stringResource(R.string.settings_gradual_ringtone)) },
        text = {
            val list = tones
            if (list == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(list, key = { it.uri.toString() }) { tone ->
                        val uriString = tone.uri.toString()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    selected = uriString
                                    playPreview(context, tone.uri)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected == uriString,
                                onClick = {
                                    selected = uriString
                                    playPreview(context, tone.uri)
                                },
                            )
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            Text(
                                text = tone.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected == uriString) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    stopPreview()
                    onConfirm(selected)
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    stopPreview()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private suspend fun loadAlarmTones(context: Context): List<ToneOption> =
    withContext(Dispatchers.IO) {
        val found = mutableListOf<ToneOption>()
        runCatching {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM)
            }
            val cursor = manager.cursor
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val title = runCatching {
                        cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    }.getOrNull().orEmpty().ifBlank { "Tone ${found.size + 1}" }
                    val uri = runCatching {
                        manager.getRingtoneUri(cursor.position)
                    }.getOrNull()
                    if (uri != null) {
                        found.add(ToneOption(title = title, uri = uri))
                    }
                }
                runCatching { cursor.close() }
            }
        }.onFailure {
            Log.w("GradualRingtone", "alarm cursor failed: ${it.message}")
        }
        if (found.isEmpty()) {
            // Bundled calm fallbacks so the picker never opens empty.
            val fallbackDefault = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (fallbackDefault != null) {
                found.add(ToneOption(title = "نغمة النظام الافتراضية", uri = fallbackDefault))
            }
            val fallbackNotif = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (fallbackNotif != null && fallbackNotif != fallbackDefault) {
                found.add(ToneOption(title = "نغمة إشعار هادئة", uri = fallbackNotif))
            }
        }
        found
    }

// Single shared preview player — stopped before every new preview and on
// dialog dismiss, so taps never stack sounds.
private var previewPlayer: android.media.Ringtone? = null

private fun playPreview(context: Context, uri: Uri) {
    runCatching { previewPlayer?.stop() }
    previewPlayer = null
    previewPlayer = runCatching {
        RingtoneManager.getRingtone(context, uri)?.also { it.play() }
    }.onFailure {
        Log.w("GradualRingtone", "preview failed: ${it.message}")
    }.getOrNull()
}

private fun stopPreview() {
    runCatching { previewPlayer?.stop() }
    previewPlayer = null
}
