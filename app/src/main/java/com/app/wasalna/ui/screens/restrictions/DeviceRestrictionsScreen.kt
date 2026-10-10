package com.app.wasalna.ui.screens.restrictions

import androidx.compose.ui.res.stringResource
import com.app.wasalna.R

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.utils.AutoStartUtils

/**
 * Phase 10.4.3.6: strict-OEM guidance (Xiaomi/Huawei/Oppo/Vivo/Honor…).
 * Shown once in the entry flow; re-openable from Settings. Auto-skips when
 * the device is lenient or the user already confirmed.
 */
@Composable
fun DeviceRestrictionsScreen(
    onDone: () -> Unit = {},
    viewModel: DeviceRestrictionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val needsGuidance by viewModel.needsGuidance.collectAsState()

    // No guidance needed → move on immediately (entry-flow skip).
    LaunchedEffect(needsGuidance) {
        if (!needsGuidance) onDone()
    }
    if (!needsGuidance) return

    val maker = AutoStartUtils.displayName()

    fun openDeviceSettings() {
        val opened = runCatching {
            AutoStartUtils.openAutoStartSettings(context)
        }.getOrDefault(false)
        if (!opened) {
            Toast.makeText(
                context,
                context.getString(R.string.restrictions_settings_toast),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun confirmAndLeave() {
        viewModel.markSeen()
        onDone()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.BatteryAlert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.restrictions_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.restrictions_body, maker),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { openDeviceSettings() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.restrictions_open))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmAndLeave() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.restrictions_done))
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { confirmAndLeave() }) {
                Text(stringResource(R.string.common_skip))
            }
        }
    }
}
