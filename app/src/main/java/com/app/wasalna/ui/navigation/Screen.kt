package com.app.wasalna.ui.navigation

/** All app destinations. Phase 7: top-level is Splash → Permissions → Main. */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Permissions : Screen("permissions")
    data object Main : Screen("main")
    data object Home : Screen("home")
    data object MyPlaces : Screen("my_places")
    data object Map : Screen("map")
    data object Settings : Screen("settings")
    data object Paywall : Screen("paywall")
    // Phase 10.4.2.1: Current / Future / Past trip history.
    data object TripsHistory : Screen("trips_history")
    // Phase 10.4.3.2: premium offline tile downloads.
    data object OfflineMaps : Screen("offline_maps")
}
