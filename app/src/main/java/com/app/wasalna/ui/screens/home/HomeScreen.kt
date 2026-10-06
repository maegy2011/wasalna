package com.app.wasalna.ui.screens.home

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.ui.ads.AdBanner
import com.app.wasalna.ui.ads.InterstitialHelper
import kotlinx.coroutines.launch

/**
 * Phase 9-2-fix: Home is now a welcoming landing screen.
 * Owns its TopAppBar (no shared header) — greeting + empty-state
 * illustration + massive "Add New Destination" button (→ MapScreen).
 * The saved-places list moved to MyPlacesScreen.
 * Phase 8 freemium gate (2 places max) still applies on add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit = {},
    onOpenMap: () -> Unit,
    onOpenPaywall: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun handleAddDestination() {
        // Phase 8 freemium: free plan allows 2 places max.
        if (!isPremium && locations.size >= 2) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Free plan allows 2 places. Upgrade to First Class!",
                )
            }
            Toast.makeText(
                context,
                "Free plan allows 2 places. Upgrade to First Class!",
                Toast.LENGTH_LONG,
            ).show()
            onOpenPaywall()
            return
        }
        if (!isPremium) {
            InterstitialHelper.showInterstitial(context) {
                onOpenMap()
            }
        } else {
            onOpenMap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wasalna") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Phase 8: banner above BottomAppBar (BottomAppBar lives in MainContainer).
            if (!isPremium) {
                AdBanner()
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "No destinations yet",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome to Wasalna! 🚏",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (locations.isEmpty()) {
                    "No destinations yet — add your first stop and never miss it again."
                } else {
                    "You have ${locations.size} saved place(s). Add another or manage them in My Places."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { handleAddDestination() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = "Add New Destination",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
