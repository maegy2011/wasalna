package com.app.wasalna.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.wasalna.ui.screens.main.MainContainerScreen
import com.app.wasalna.ui.screens.permissions.PermissionsScreen
import com.app.wasalna.ui.screens.splash.SplashScreen

/**
 * Phase 7: root graph — Splash → Permissions → Main (tabs container).
 * Main route clears the onboarding back-stack inclusively.
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
                    navController.navigate(Screen.Main.route) {
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
                viewModel = hiltViewModel(),
                onDone = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Main.route) {
            MainContainerScreen()
        }
    }
}
