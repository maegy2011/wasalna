package com.app.wasalna.ui.navigation

/** All app destinations. Phase 1: placeholders only. */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Permissions : Screen("permissions")
    data object Home : Screen("home")
    data object Map : Screen("map")
    data object Settings : Screen("settings")
    data object Paywall : Screen("paywall")
}
