package com.app.wasalna.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.R

/**
 * Phase 10.13-exp: Google account card — EXPERIMENTAL ONLY.
 *
 * Debug-style labeling (AssistChip "تجريبي" + local-only note) so users
 * know this is experimental. Stores only display name + email in
 * DataStore; shows Arabic errors via Toast; never crashes (all sign-in
 * paths are runCatching-guarded in [AccountViewModel]).
 */
@Composable
fun AccountSettingsSection(
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val displayName by viewModel.accountDisplayName.collectAsState()
    val email by viewModel.accountEmail.collectAsState()
    val error by viewModel.error.collectAsState()

    error?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        runCatching { viewModel.onSignInResult(result.data) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_account_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_account_sub),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.settings_account_experimental_badge)) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_account_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (displayName.isBlank() && email.isBlank()) {
                Button(
                    onClick = {
                        runCatching {
                            val intent = viewModel.signInIntent()
                            if (intent != null) signInLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_account_signin))
                }
            } else {
                Text(
                    text = stringResource(
                        R.string.settings_account_signed_as,
                        displayName.ifBlank { email },
                        email.ifBlank { displayName },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { runCatching { viewModel.signOut() } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        Icons.Filled.Logout,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_account_signout))
                }
            }
        }
    }
}
