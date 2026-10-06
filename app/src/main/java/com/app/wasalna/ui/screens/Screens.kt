package com.app.wasalna.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Phase 3: Splash + Permissions moved to dedicated packages
// (ui.screens.splash / ui.screens.permissions) with Hilt ViewModels.
// Phase 5: Home moved to ui.screens.home (HomeScreen + HomeViewModel).
// Phase 7: Settings moved to ui.screens.settings (SettingsScreen + SettingsViewModel).

@Composable
fun PaywallScreen() {
    PlaceholderScreen(text = "Paywall — Pro upgrade (Phase 5)")
}

@Composable
private fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
