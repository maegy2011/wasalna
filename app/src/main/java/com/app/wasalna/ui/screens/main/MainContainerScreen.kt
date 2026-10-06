package com.app.wasalna.ui.screens.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.wasalna.ui.navigation.Screen
import com.app.wasalna.ui.screens.paywall.PaywallScreen
import com.app.wasalna.ui.screens.home.HomeScreen
import com.app.wasalna.ui.screens.map.MapScreen
import com.app.wasalna.ui.screens.myplaces.MyPlacesScreen
import com.app.wasalna.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Phase 9-2-fix: Main container — drawer + bottom navigation + inner NavHost.
 *
 * - NO shared TopAppBar (removed to fix duplicated headers): each screen
 *   (Home / My Places / First Class / Settings / Map) owns its own header.
 * - Bottom bar: Home → HomeScreen, My Places → MyPlacesScreen,
 *   First Class → PaywallScreen.
 * - MapScreen is NO LONGER a bottom tab — full-screen destination reached
 *   via the "Add" buttons (Home FABCTA / MyPlaces FAB).
 * - Inner NavHost keeps tab state via launchSingleTop + restoreState/saveState.
 * - Settings lives in the inner graph (drawer entry, hidden from bottom bar).
 */
@Composable
fun MainContainerScreen() {
    val innerNav = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val backStack by innerNav.currentBackStackEntryAsState()
    val currentHierarchy = backStack?.destination?.hierarchy

    fun isSelected(route: String): Boolean =
        currentHierarchy?.any { it.route == route } == true

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    // Bottom bar is hidden on the full-screen Map destination.
    val showBottomBar = !isSelected(Screen.Map.route)

    fun navigateTab(route: String) {
        innerNav.navigate(route) {
            popUpTo(innerNav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openMapFullScreen() {
        innerNav.navigate(Screen.Map.route) {
            launchSingleTop = true
        }
    }

    fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Try Wasalna — never miss your stop! https://mohamedadel.cfd")
        }
        context.startActivity(Intent.createChooser(send, "Share Wasalna"))
    }

    fun rateApp() {
        Toast.makeText(context, "Rate us on Google Play — coming soon!", Toast.LENGTH_SHORT).show()
    }

    fun contactUs() {
        val mail = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:support@wasalna.app?subject=Wasalna%20Support")
        }
        runCatching { context.startActivity(mail) }
            .onFailure {
                Toast.makeText(context, "support@wasalna.app", Toast.LENGTH_LONG).show()
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚏 Wasalna",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Plan: The Ride (Free)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = isSelected(Screen.Settings.route),
                    onClick = {
                        closeDrawer()
                        navigateTab(Screen.Settings.route)
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text("Share") },
                    selected = false,
                    onClick = {
                        closeDrawer()
                        shareApp()
                    },
                    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text("Rate") },
                    selected = false,
                    onClick = {
                        closeDrawer()
                        rateApp()
                    },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text("Contact") },
                    selected = false,
                    onClick = {
                        closeDrawer()
                        contactUs()
                    },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = isSelected(Screen.Home.route),
                            onClick = { navigateTab(Screen.Home.route) },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = isSelected(Screen.MyPlaces.route),
                            onClick = { navigateTab(Screen.MyPlaces.route) },
                            icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                            label = { Text("My Places") },
                        )
                        NavigationBarItem(
                            selected = isSelected(Screen.Paywall.route),
                            onClick = { navigateTab(Screen.Paywall.route) },
                            icon = { Icon(Icons.Filled.Diamond, contentDescription = null) },
                            label = { Text("First Class") },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = innerNav,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onMenuClick = { openDrawer() },
                        onOpenMap = { openMapFullScreen() },
                        onOpenPaywall = { navigateTab(Screen.Paywall.route) },
                        viewModel = hiltViewModel(),
                    )
                }
                composable(Screen.MyPlaces.route) {
                    MyPlacesScreen(
                        onMenuClick = { openDrawer() },
                        onAddDestination = { openMapFullScreen() },
                        viewModel = hiltViewModel(),
                    )
                }
                composable(Screen.Map.route) {
                    MapScreen(
                        viewModel = hiltViewModel(),
                        onSaved = { navigateTab(Screen.MyPlaces.route) },
                        onBack = { navigateTab(Screen.MyPlaces.route) },
                    )
                }
                composable(Screen.Paywall.route) { PaywallScreen(onMenuClick = { openDrawer() }) }
                composable(Screen.Settings.route) {
                    SettingsScreen(onBack = { navigateTab(Screen.Home.route) })
                }
            }
        }
    }
}
