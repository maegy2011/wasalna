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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.wasalna.R
import com.app.wasalna.ui.navigation.Screen

/**
 * Unified app chrome — single source of truth for header / footer / drawer.
 *
 * - [WasalnaTopBar]: every screen header (menu OR back + title + subtitle + actions).
 * - [WasalnaBottomBar]: 5-slot footer (menu · home · CENTER start · places · settings)
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
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            when {
                showMenu -> IconButton(onClick = onMenuClick) {
                    Icon(
                        painterResource(R.drawable.ic_svg_menu),
                        contentDescription = stringResource(R.string.nav_menu_desc),
                    )
                }
                showBack -> IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back_home),
                    )
                }
            }
        },
        actions = { actions?.invoke(this) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
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
    Box(modifier = Modifier.fillMaxWidth()) {
        NavigationBar {
            NavigationBarItem(
                selected = false,
                onClick = onMenuClick,
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_svg_menu),
                        contentDescription = stringResource(R.string.nav_menu_desc),
                    )
                },
                label = { Text(stringResource(R.string.nav_menu)) },
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
                label = { Text(stringResource(R.string.nav_home)) },
                colors = itemColors,
            )
            // Center slot: spacer under the overlaid FAB.
            NavigationBarItem(
                selected = false,
                onClick = onStartTrip,
                icon = { Spacer(modifier = Modifier.size(24.dp)) },
                label = { Text(stringResource(R.string.nav_start_trip)) },
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
                label = { Text(stringResource(R.string.myplaces_title)) },
                colors = itemColors,
            )
            NavigationBarItem(
                selected = isSelected(Screen.Settings.route),
                onClick = { onNavigateTab(Screen.Settings.route) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_svg_settings),
                        contentDescription = stringResource(R.string.settings_title),
                    )
                },
                label = { Text(stringResource(R.string.settings_title)) },
                colors = itemColors,
            )
        }
        FloatingActionButton(
            onClick = onStartTrip,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(60.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.nav_start_trip),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

// ── Drawer primitives ─────────────────────────────────────────────────────

@Composable
fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
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
                )
                icon != null -> Icon(icon, contentDescription = contentDescription ?: label)
            }
        },
        badge = badge,
        shape = RoundedCornerShape(12.dp),
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
            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                iconContent()
            }
            Spacer(modifier = Modifier.width(12.dp))
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
                        if (isPremium) {
                            Icon(
                                Icons.Filled.Diamond,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = planLabel,
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

// Fallback icons when custom drawables are unavailable (previews/tests).
internal val DrawerFallbackIcons = mapOf(
    "home" to Icons.Filled.Home,
    "places" to Icons.Filled.Place,
    "settings" to Icons.Filled.Settings,
    "history" to Icons.Filled.History,
    "menu" to Icons.Filled.Menu,
    "menu_open" to Icons.AutoMirrored.Filled.MenuOpen,
)
