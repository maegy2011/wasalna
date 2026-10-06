package com.app.wasalna.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.wasalna.ui.screens.HomeScreen
import com.app.wasalna.ui.screens.map.MapScreen
import com.app.wasalna.ui.screens.PaywallScreen
import com.app.wasalna.ui.screens.SettingsScreen
import com.app.wasalna.ui.screens.permissions.PermissionsScreen
import com.app.wasalna.ui.screens.splash.SplashScreen

/**
 * Phase 3: Splash gates on onboarding state from DataStore.
 * - First launch → Splash → Permissions → Home (popping back-stack inclusively).
 * - Returning user (onboarding done) → Splash → Home directly.
 */
@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigatePermissions = {
                    navController.navigate(Screen.Permissions.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(
                // hiltViewModel() is the default param — explicit here for clarity.
                viewModel = hiltViewModel(),
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenMap = { navController.navigate(Screen.Map.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenPaywall = { navController.navigate(Screen.Paywall.route) },
            )
        }
        composable(Screen.Map.route) {
            MapScreen(
                viewModel = hiltViewModel(),
                onSaved = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
            )
        }
        composable(Screen.Settings.route) { SettingsScreen() }
        composable(Screen.Paywall.route) { PaywallScreen() }
    }
}
