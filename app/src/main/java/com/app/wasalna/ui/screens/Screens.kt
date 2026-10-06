package com.app.wasalna.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Wasalna وصلنا", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    PlaceholderScreen(text = "Permissions — location & notifications (Phase 2)")
}

@Composable
fun HomeScreen(onOpenMap: () -> Unit, onOpenSettings: () -> Unit, onOpenPaywall: () -> Unit) {
    PlaceholderScreen(text = "Home — alarms list (Phase 3)")
}

@Composable
fun MapScreen() {
    PlaceholderScreen(text = "Map — destination picker (Phase 3)")
}

@Composable
fun SettingsScreen() {
    PlaceholderScreen(text = "Settings — elderly mode & units (Phase 4)")
}

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
