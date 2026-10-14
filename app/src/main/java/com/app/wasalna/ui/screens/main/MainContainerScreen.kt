package com.app.wasalna.ui.screens.main

import android.content.Intent
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.wasalna.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.wasalna.ui.components.DrawerSectionTitle
import com.app.wasalna.ui.components.WasalnaBottomBar
import com.app.wasalna.ui.components.WasalnaBrandHeader
import com.app.wasalna.ui.components.WasalnaDrawerItem
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
    // Live follow (user order Oct 2026): the active trip, if any — a
    // fresh start jumps straight to its TripRoute view (pending flag
    // covers the async service→DB race: the row lands a beat later).
    val liveTrip by planVm.activeTrip.collectAsState()
    var pendingLiveTrip by remember { mutableStateOf(false) }
    fun openLiveTrip() {
        val t = liveTrip
        if (t != null) {
            innerNav.navigate(Screen.TripRoute.routeFor(t.id))
        } else {
            pendingLiveTrip = true
        }
    }
    LaunchedEffect(liveTrip) {
        val t = liveTrip
        if (pendingLiveTrip && t != null) {
            pendingLiveTrip = false
            innerNav.navigate(Screen.TripRoute.routeFor(t.id))
        }
    }
    var planNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            planNow = System.currentTimeMillis()
        }
    }

    val backStack by innerNav.currentBackStackEntryAsState()
    val currentHierarchy = backStack?.destination?.hierarchy
    // Elderly simplification (user order Oct 2026): First Class tab hidden.
    val elderly = com.app.wasalna.ui.theme.LocalElderlyMode.current

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
            // Dedupe: args differ per tap, so SingleTop alone can't match —
            // pop any stale Map copy first or back walks dead map entries.
            popUpTo(
                Screen.Map.route + "?lat={lat}&lng={lng}&mode={mode}&name={name}",
            ) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Smart back (user order Oct 2026): remembers the visit history and
    // returns to the LAST screen instead of fixed destinations. Drawer
    // closes first; pushed screens pop naturally; at a root the previous
    // visited destination opens; Home requires a double-press to exit.
    val visitHistory = remember { mutableStateListOf<String>() }
    var lastBackPress by remember { mutableLongStateOf(0L) }
    val currentRoute = backStack?.destination?.route
    LaunchedEffect(currentRoute) {
        currentRoute?.let { route ->
            if (visitHistory.lastOrNull() != route) {
                visitHistory.add(route)
                if (visitHistory.size > 20) visitHistory.removeAt(0)
            }
        }
    }
    fun smartBack() {
        if (drawerState.isOpen) {
            closeDrawer()
            return
        }
        if (innerNav.popBackStack()) return
        val cur = innerNav.currentDestination?.route
        while (visitHistory.isNotEmpty() && visitHistory.last() == cur) {
            visitHistory.removeLast()
        }
        val target = visitHistory.removeLastOrNull()
        if (target != null) {
            navigateTab(target)
        } else if (cur != Screen.Home.route) {
            navigateTab(Screen.Home.route)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2_000) {
                (context as? Activity)?.finish()
            } else {
                lastBackPress = now
                Toast.makeText(
                    context,
                    context.getString(R.string.common_back_exit),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                context.getString(
                    R.string.drawer_share_text,
                    com.app.wasalna.utils.AppLinks.SHARE_URL,
                ),
            )
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.drawer_share_title)))
    }

    fun rateApp() {
        Toast.makeText(context, context.getString(R.string.drawer_rate_toast), Toast.LENGTH_SHORT).show()
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
            // Unified footer — labels + selected pill + center start-trip FAB.
            // Full-screen Map matches no tab; fall back to Home so the pill
            // never looks fully deselected.
            val barRoute = when {
                currentRoute?.startsWith(Screen.Map.route) == true -> Screen.Home.route
                else -> currentRoute
            }
            WasalnaBottomBar(
                currentRoute = barRoute,
                onMenuClick = { openDrawer() },
                onNavigateTab = ::navigateTab,
                onStartTrip = { openMapFullScreen() },
            )
        },
    ) { scaffoldPadding ->
        ModalNavigationDrawer(
            gesturesEnabled = !isOnMapRoute,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.padding(top = 64.dp),
                ) {
                    // Single VM + single 60s ticker live at the container top —
                    // the header receives the computed values (no duplicates).
                    DrawerBrandHeader(
                        planLabel = planLabel(planPremium, planTrialEnd, planNow),
                        isPremium = planPremium,
                    )
                    DrawerSectionTitle(text = stringResource(R.string.drawer_section_general))
                    WasalnaDrawerItem(
                        label = stringResource(R.string.nav_home),
                        selected = isSelected(Screen.Home.route),
                        onClick = { closeDrawer(); navigateTab(Screen.Home.route) },
                        iconRes = R.drawable.ic_svg_home,
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.myplaces_title),
                        selected = isSelected(Screen.MyPlaces.route),
                        onClick = { closeDrawer(); navigateTab(Screen.MyPlaces.route) },
                        iconRes = R.drawable.ic_svg_place,
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.settings_title),
                        selected = isSelected(Screen.Settings.route),
                        onClick = { closeDrawer(); navigateTab(Screen.Settings.route) },
                        iconRes = R.drawable.ic_svg_settings,
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.offline_title),
                        selected = isSelected(Screen.OfflineMaps.route),
                        onClick = { closeDrawer(); navigateTab(Screen.OfflineMaps.route) },
                        iconRes = R.drawable.ic_svg_map,
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.trips_title),
                        selected = isSelected(Screen.TripsHistory.route),
                        onClick = { closeDrawer(); navigateTab(Screen.TripsHistory.route) },
                        iconRes = R.drawable.ic_svg_history,
                        badge = {
                            if (successfulTrips > 0) {
                                Text(
                                    text = successfulTrips.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.paywall_title),
                        selected = isSelected(Screen.Paywall.route),
                        onClick = { closeDrawer(); navigateTab(Screen.Paywall.route) },
                        icon = Icons.Filled.Diamond,
                        badge = {
                            if (!planPremium) {
                                Text(
                                    text = "⬆ " + stringResource(R.string.drawer_upgrade),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    DrawerSectionTitle(text = stringResource(R.string.drawer_impact))
                    WasalnaDrawerItem(
                        label = stringResource(R.string.impact_trips, successfulTrips),
                        selected = false,
                        onClick = { closeDrawer(); navigateTab(Screen.TripsHistory.route) },
                        iconRes = R.drawable.ic_svg_favorite,
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.impact_hours, "%.1f".format(timeSavedHours)),
                        selected = false,
                        onClick = { closeDrawer(); navigateTab(Screen.TripsHistory.route) },
                        iconRes = R.drawable.ic_svg_schedule,
                    )
                    DrawerSectionTitle(text = stringResource(R.string.drawer_section_more))
                    WasalnaDrawerItem(
                        label = stringResource(R.string.common_share),
                        selected = false,
                        onClick = { closeDrawer(); shareApp() },
                        iconRes = R.drawable.ic_svg_share,
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.drawer_rate),
                        selected = false,
                        onClick = { closeDrawer(); rateApp() },
                        iconRes = R.drawable.ic_svg_star,
                    )
                    WasalnaDrawerItem(
                        label = stringResource(R.string.drawer_contact),
                        selected = false,
                        onClick = { closeDrawer(); contactUs() },
                        iconRes = R.drawable.ic_svg_email,
                    )
                }
            },
        ) {
            // Smart back owns the system back button everywhere in the
            // container (Map's own handler takes precedence while its
            // search/sheet states are open — see MapScreen).
            BackHandler(onBack = ::smartBack)
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
                        onBack = { smartBack() },
                        onAddDestination = { openMapFullScreen() },
                        viewModel = hiltViewModel(),
                        onTripStarted = ::openLiveTrip,
                        onOpenPaywall = { navigateTab(Screen.Paywall.route) },
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
                        onBack = { smartBack() },
                        onTripStarted = ::openLiveTrip,
                        onOpenPaywall = { navigateTab(Screen.Paywall.route) },
                        tripName = entry.arguments?.getString("name")?.ifBlank { null },
                        tripLat = entry.arguments?.getString("lat")?.toDoubleOrNull(),
                        tripLng = entry.arguments?.getString("lng")?.toDoubleOrNull(),
                        tripMode = entry.arguments?.getString("mode")?.ifBlank { null },
                    )
                }
                composable(Screen.TripsHistory.route) {
                    TripsHistoryScreen(
                        onBack = { smartBack() },
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
                        onBack = { smartBack() },
                    )
                }
                composable(Screen.Paywall.route) { PaywallScreen(onMenuClick = { openDrawer() }) }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onBack = { smartBack() },
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
                        onBack = { smartBack() },
                        onOpenOfflineRouting = { navigateTab(Screen.OfflineRouting.route) },
                    )
                }
                composable(Screen.OfflineRouting.route) {
                    OfflineRoutingScreen(
                        onBack = { smartBack() },
                    )
                }
            }
        }
    }
}

/**
 * Drawer brand header — gradient brand block (AppChrome) with the real
 * launcher icon + name + version + live plan pill.
 */
@Composable
private fun DrawerBrandHeader(planLabel: String, isPremium: Boolean) {
    val context = LocalContext.current
    val appName = stringResource(id = R.string.app_name)
    val versionName = com.app.wasalna.BuildConfig.VERSION_NAME
    val appIconBitmap = remember {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val px = 192
            val bmp = android.graphics.Bitmap.createBitmap(
                px, px, android.graphics.Bitmap.Config.ARGB_8888,
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
            bmp
        }.getOrNull()
    }
    WasalnaBrandHeader(
        appName = appName,
        versionName = versionName,
        planLabel = planLabel,
        isPremium = isPremium,
        iconContent = {
            appIconBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } ?: Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        },
    )
}
