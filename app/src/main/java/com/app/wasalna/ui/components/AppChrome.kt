package com.app.wasalna.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.wasalna.R
import com.app.wasalna.ui.navigation.Screen

/**
 * Unified app chrome — single source of truth for header / footer / drawer.
 *
 * - [WasalnaTopBar]: every screen header (menu OR back + title + subtitle + actions).
 * - [WasalnaBottomBar]: 5-slot footer (menu · home · CENTER start · places · trip history)
 *   (Settings stays reachable from the navigation drawer).
 *   with labels + selected pill, FAB docked in the center slot.
 * - [DrawerSectionTitle] + [WasalnaDrawerItem]: grouped drawer rows with
 *   consistent padding, shape, and selected styling.
 * - [WasalnaBrandHeader]: gradient brand block with app icon + name + plan pill.
 */

// ── Header ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasalnaTopBar(
    title: String,
    subtitle: String? = null,
    showMenu: Boolean = false,
    onMenuClick: () -> Unit = {},
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable (RowScope.() -> Unit)? = null,
    colorful: Boolean = false,
) {
    val gradient = Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
        ),
    )
    val titleColor = if (colorful) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (colorful) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val navIconTint = if (colorful) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.primary
    Surface(
        shadowElevation = if (colorful) 6.dp else 2.dp,
        tonalElevation = if (colorful) 0.dp else 1.dp,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                    )
                    if (subtitle != null) {
                        // Smooth cross-fade when the subtitle swaps (live ETA etc.).
                        AnimatedContent(targetState = subtitle, label = "subtitle") { sub ->
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                val action = when {
                    showMenu -> onMenuClick
                    showBack -> onBackClick
                    else -> null
                }
                if (action != null) {
                    IconButton(onClick = action) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (colorful) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.primaryContainer,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (showMenu) {
                                Icon(
                                    painterResource(R.drawable.ic_svg_menu),
                                    contentDescription = stringResource(R.string.nav_menu_desc),
                                    tint = navIconTint,
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back_home),
                                    tint = navIconTint,
                                )
                            }
                        }
                    }
                }
            },
            actions = { actions?.invoke(this) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (colorful) Color.Transparent else MaterialTheme.colorScheme.surface,
                titleContentColor = titleColor,
                actionIconContentColor = navIconTint,
                navigationIconContentColor = navIconTint,
            ),
            modifier = if (colorful) Modifier.background(gradient) else Modifier,
        )
    }
}

// ── Footer ────────────────────────────────────────────────────────────────

@Composable
fun WasalnaBottomBar(
    currentRoute: String?,
    onMenuClick: () -> Unit,
    onNavigateTab: (String) -> Unit,
    onStartTrip: () -> Unit,
) {
    fun isSelected(route: String) = currentRoute?.let {
        it == route || it.startsWith("$route/") || it.startsWith("$route?")
    } == true

    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Icons-only footer (user order Oct 2026): no labels — 5 equal slots.
    // The start-trip action lives INSIDE the center slot as a 44dp filled
    // disc — proportional to the bar, unlike the old 60dp overlay
    // that jutted above it.
    // Radiating halo (user order Oct 2026): two hollow rings expanding
    // from the disc edge and fading out, staggered 900ms — radar feel.
    // Linear easing keeps the expansion speed constant; Ring restarts at
    // the disc edge while the previous one dissolves outward.
    val halo = rememberInfiniteTransition(label = "startHalo")
    val ring1 by halo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
        ),
        label = "haloRing1",
    )
    val ring2 by halo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 900, easing = LinearEasing),
        ),
        label = "haloRing2",
    )
    NavigationBar(tonalElevation = 3.dp) {
        NavigationBarItem(
            selected = false,
            onClick = onMenuClick,
            icon = {
                Icon(
                    painterResource(R.drawable.ic_svg_menu),
                    contentDescription = stringResource(R.string.nav_menu_desc),
                )
            },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = isSelected(Screen.Home.route),
            onClick = { onNavigateTab(Screen.Home.route) },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_svg_home),
                    contentDescription = stringResource(R.string.nav_home),
                )
            },
            colors = itemColors,
        )
        // Center: filled primary disc with the play arrow + radiating
        // halo behind it. The 64dp stage keeps the expanding rings inside
        // the slot (no overlap with neighbor icons); the disc stays
        // crisp in front while rings dissolve outward.
        NavigationBarItem(
            selected = false,
            onClick = onStartTrip,
            icon = {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HaloRing(progress = ring1)
                    HaloRing(progress = ring2)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.nav_start_trip),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = isSelected(Screen.MyPlaces.route),
            onClick = { onNavigateTab(Screen.MyPlaces.route) },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_svg_place),
                    contentDescription = stringResource(R.string.myplaces_title),
                )
            },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = isSelected(Screen.TripsHistory.route),
            onClick = { onNavigateTab(Screen.TripsHistory.route) },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_svg_history),
                    contentDescription = stringResource(R.string.trips_title),
                )
            },
            colors = itemColors,
        )
    }
}

/**
 * One radiating halo ring: a hollow primary-colored circle expanding
 * from the 44dp disc edge (1f → 1.45f) while fading out. progress 0 = at
 * the disc edge, opaque; progress 1 = fully expanded, invisible.
 */
@Composable
private fun HaloRing(progress: Float) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(1f + progress * 0.45f)
            .alpha((1f - progress) * 0.7f)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
    )
}

// ── Drawer primitives ─────────────────────────────────────────────────────

@Composable
fun DrawerSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
    )
}

@Composable
fun WasalnaDrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    contentDescription: String? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    // Smooth icon tint transition on selection change.
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "drawerIconTint",
    )
    NavigationDrawerItem(
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            when {
                iconRes != null -> Icon(
                    painterResource(iconRes),
                    contentDescription = contentDescription ?: label,
                    tint = iconTint,
                )
                icon != null -> Icon(
                    icon,
                    contentDescription = contentDescription ?: label,
                    tint = iconTint,
                )
            }
        },
        badge = badge,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
fun WasalnaBrandHeader(
    appName: String,
    versionName: String,
    planLabel: String,
    isPremium: Boolean,
    iconContent: @Composable () -> Unit,
) {
    val gradient = Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                iconContent()
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            if (isPremium) Icons.Filled.Diamond else Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Countdown swaps tick every minute — cross-slide the label.
                        AnimatedContent(
                            targetState = planLabel,
                            transitionSpec = {
                                slideInVertically { it } + fadeIn() togetherWith
                                    slideOutVertically { -it } + fadeOut()
                            },
                            label = "planLabel",
                        ) { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Fallback icons when custom drawables are unavailable (previews/tests).
internal val DrawerFallbackIcons = mapOf(
    "home" to Icons.Filled.Home,
    "places" to Icons.Filled.Place,
    "settings" to Icons.Filled.Settings,
    "history" to Icons.Filled.History,
    "menu" to Icons.Filled.Menu,
    "menu_open" to Icons.AutoMirrored.Filled.MenuOpen,
)
