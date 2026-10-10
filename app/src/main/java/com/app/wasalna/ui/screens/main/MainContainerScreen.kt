package com.app.wasalna.ui.screens.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Email
import android.net.Uri
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.wasalna.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.ui.navigation.Screen
import com.app.wasalna.ui.screens.home.HomeViewModel
import com.app.wasalna.ui.screens.restrictions.DeviceRestrictionsScreen
import com.app.wasalna.ui.screens.offline.OfflineMapsScreen
import com.app.wasalna.ui.screens.offline.OfflineRoutingScreen
import com.app.wasalna.ui.screens.paywall.PaywallScreen
import com.app.wasalna.ui.screens.trips.TripRouteScreen
import com.app.wasalna.ui.screens.trips.TripsHistoryScreen
import com.app.wasalna.ui.screens.home.HomeScreen
import com.app.wasalna.ui.screens.map.MapScreen
import com.app.wasalna.ui.screens.myplaces.MyPlacesScreen
import com.app.wasalna.ui.screens.settings.SettingsScreen
import com.app.wasalna.utils.planLabel
import kotlinx.coroutines.delay
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

    // Phase 10.4.1: live plan header (trial countdown ticks every minute).
    val planVm: HomeViewModel = hiltViewModel()
    val planPremium by planVm.isPremium.collectAsState()
    val planTrialEnd by planVm.trialEndTimeMillis.collectAsState()
    // Phase 10.4.2.1: drawer impact stats.
    val successfulTrips by planVm.successfulTrips.collectAsState()
    val timeSavedHours by planVm.timeSavedHours.collectAsState()
    var planNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            planNow = System.currentTimeMillis()
        }
    }

    val backStack by innerNav.currentBackStackEntryAsState()
    val currentHierarchy = backStack?.destination?.hierarchy

    fun isSelected(route: String): Boolean =
        currentHierarchy?.any { it.route == route } == true

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    // Phase 10-Fix: bottom bar stays visible on every destination (tabs +
    // full-screen Map) so navigation never disappears. NavHost lives INSIDE
    // the Scaffold padding below.
    fun navigateTab(route: String) {
        innerNav.navigate(route) {
            popUpTo(innerNav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /**
     * Phase 10.4.3.1: null = blank picker; a trip carries its destination
     * + mode so the map opens on the live visualization.
     */
    fun openMapFullScreen(trip: TripHistory? = null) {
        val route = if (trip == null) {
            Screen.Map.route
        } else {
            Screen.Map.route +
                "?lat=${trip.destLat}&lng=${trip.destLng}" +
                "&mode=${trip.transportMode.name}" +
                "&name=${Uri.encode(trip.locationName)}"
        }
        innerNav.navigate(route) {
            launchSingleTop = true
        }
    }

    fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Try Wasalna — never miss your stop! ${com.app.wasalna.utils.AppLinks.SHARE_URL}")
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

    // Map UX fixes: left-edge drawer auto-open — map gestures near the
    // left edge triggered the drawer swipe. While on the Map route the
    // drawer opens ONLY via explicit UI (no hamburger lives on Map, back
    // navigates away), so edge swipe is disabled there.
    val isOnMapRoute = currentHierarchy?.any { it.route?.startsWith(Screen.Map.route) == true } == true
    // Drawer-below-header-and-above-navbar layout: the outer Scaffold owns
    // the bottom NavigationBar OUTSIDE the ModalNavigationDrawer, so the
    // navbar stays visible + tappable while the drawer is open. The drawer
    // (scrim + sheet) lives only in the Scaffold content area, and the
    // sheet is inset by one M3 Small TopAppBar height (64dp) so its top
    // edge sits BELOW the per-screen TopAppBar instead of covering it.
    // (Per-screen headers live inside the NavHost content, so the scrim
    // still dims them while open — tap there closes the drawer per M3.)
    Scaffold(
        bottomBar = {
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
                    NavigationBarItem(
                        selected = isSelected(Screen.TripsHistory.route),
                        onClick = { navigateTab(Screen.TripsHistory.route) },
                        icon = {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = "فتح سجل الرحلات",
                            )
                        },
                        label = { Text("رحلاتي") },
                    )
                    // Drawer entry from the bottom bar (works on every tab,
                    // incl. Map where edge-swipe is locked). Opens the drawer
                    // via the existing scope — no navigation, no state change.
                    NavigationBarItem(
                        selected = false,
                        onClick = { openDrawer() },
                        icon = {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "فتح القائمة الجانبية",
                            )
                        },
                        label = { Text("القائمة") },
                    )
                }
        },
    ) { scaffoldPadding ->
        ModalNavigationDrawer(
            gesturesEnabled = !isOnMapRoute,
            drawerState = drawerState,
            drawerContent = {
            ModalDrawerSheet(
                // 64dp = M3 Small TopAppBar height: sheet top edge sits
                // just below the per-screen header.
                modifier = Modifier.padding(top = 64.dp),
            ) {
                DrawerBrandHeader()
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = planLabel(planPremium, planTrialEnd, planNow),
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
                    label = { Text("Trip History") },
                    selected = isSelected(Screen.TripsHistory.route),
                    onClick = {
                        closeDrawer()
                        navigateTab(Screen.TripsHistory.route)
                    },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                // Phase 10.4.2.1: Your Impact — live stats from TripDao.
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your Impact",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Peace of mind: $successfulTrips trips") },
                    selected = false,
                    onClick = {
                        closeDrawer()
                        navigateTab(Screen.TripsHistory.route)
                    },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = {
                        Text("Time saved: ${"%.1f".format(timeSavedHours)} hrs")
                    },
                    selected = false,
                    onClick = {
                        closeDrawer()
                        navigateTab(Screen.TripsHistory.route)
                    },
                    icon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
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
            NavHost(
                navController = innerNav,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onMenuClick = { openDrawer() },
                        onOpenMap = { trip -> openMapFullScreen(trip) },
                        // Phase 10.4.3.8: widget tap opens the live route map.
                        onOpenTripRoute = { tripId ->
                            innerNav.navigate(Screen.TripRoute.routeFor(tripId))
                        },
                        onOpenPaywall = { navigateTab(Screen.Paywall.route) },
                        viewModel = hiltViewModel(),
                    )
                }
                composable(Screen.MyPlaces.route) {
                    MyPlacesScreen(
                        onBack = { navigateTab(Screen.Home.route) },
                        onAddDestination = { openMapFullScreen() },
                        viewModel = hiltViewModel(),
                    )
                }
                // Phase 10.4.3.1: optional live-trip args (Home widget tap).
                composable(
                    route = Screen.Map.route + "?lat={lat}&lng={lng}&mode={mode}&name={name}",
                    arguments = listOf(
                        navArgument("lat") { defaultValue = "" },
                        navArgument("lng") { defaultValue = "" },
                        navArgument("mode") { defaultValue = "" },
                        navArgument("name") { defaultValue = "" },
                    ),
                ) { entry ->
                    MapScreen(
                        viewModel = hiltViewModel(),
                        onSaved = { navigateTab(Screen.MyPlaces.route) },
                        onBack = { navigateTab(Screen.MyPlaces.route) },
                        tripName = entry.arguments?.getString("name")?.ifBlank { null },
                        tripLat = entry.arguments?.getString("lat")?.toDoubleOrNull(),
                        tripLng = entry.arguments?.getString("lng")?.toDoubleOrNull(),
                        tripMode = entry.arguments?.getString("mode")?.ifBlank { null },
                    )
                }
                composable(Screen.TripsHistory.route) {
                    TripsHistoryScreen(
                        onBack = { navigateTab(Screen.Home.route) },
                        // Phase 10.4.3.8: past trip tap opens its route map.
                        onOpenTripRoute = { tripId ->
                            innerNav.navigate(Screen.TripRoute.routeFor(tripId))
                        },
                    )
                }
                // Phase 10.4.3.8: live/recorded route for one trip.
                composable(
                    route = Screen.TripRoute.PATTERN,
                    arguments = listOf(
                        navArgument(Screen.TripRoute.ARG) { type = NavType.LongType },
                    ),
                ) {
                    TripRouteScreen(
                        onBack = { innerNav.popBackStack() },
                    )
                }
                composable(Screen.Paywall.route) { PaywallScreen(onMenuClick = { openDrawer() }) }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onBack = { navigateTab(Screen.Home.route) },
                        onOpenPaywall = { navigateTab(Screen.Paywall.route) },
                        onOpenOfflineMaps = { navigateTab(Screen.OfflineMaps.route) },
                        onOpenRestrictions = { navigateTab(Screen.DeviceRestrictions.route) },
                    )
                }
                // Phase 10.4.3.6: revisit entry for the OEM guidance.
                composable(Screen.DeviceRestrictions.route) {
                    DeviceRestrictionsScreen(
                        onDone = { navigateTab(Screen.Settings.route) },
                    )
                }
                composable(Screen.OfflineMaps.route) {
                    OfflineMapsScreen(
                        onBack = { navigateTab(Screen.Settings.route) },
                        onOpenOfflineRouting = { navigateTab(Screen.OfflineRouting.route) },
                    )
                }
                composable(Screen.OfflineRouting.route) {
                    OfflineRoutingScreen(
                        onBack = { navigateTab(Screen.OfflineMaps.route) },
                    )
                }
            }
        }
    }
}

/**
 * Drawer brand header — ONE row: reused launcher artwork + app name on the
 * same line, app version (BuildConfig.VERSION_NAME) on the next line
 * beneath. Row + start-padding are RTL-aware (mirrors in Arabic locale);
 * the name comes from strings.xml so the Arabic locale shows the Arabic
 * name. No new assets: the launcher foreground vector is shown on the
 * launcher background color inside a 48dp (8dp-grid, 48dp-min) badge.
 */
@Composable
private fun DrawerBrandHeader() {
    val appName = stringResource(id = R.string.app_name)
    val versionName = com.app.wasalna.BuildConfig.VERSION_NAME
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(id = R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Aligned under the app name (badge 48dp + gap 8dp); start =
            // RTL-aware, mirrors automatically in Arabic.
            modifier = Modifier.padding(start = 56.dp),
        )
    }
}
