package com.app.wasalna.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.wasalna.domain.model.TransportMode

/**
 * Transit design system (Oct 2026): vehicle → minimalist line icon.
 * Outlined default, filled when selected/active. Green tint.
 * RTL-safe (icon only), no strings, no behavior change.
 */
@Composable
fun TransitModeIcon(
    mode: TransportMode,
    selected: Boolean = false,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val imageVector = when (mode) {
        TransportMode.METRO -> if (selected) Icons.Filled.Train else Icons.Outlined.Train
        TransportMode.TRAIN -> if (selected) Icons.Filled.Train else Icons.Outlined.Train
        TransportMode.MONORAIL -> if (selected) Icons.Filled.Tram else Icons.Outlined.Tram
        TransportMode.TRANSIT -> if (selected) Icons.Filled.DirectionsBus else Icons.Outlined.DirectionsBus
        TransportMode.WALK -> if (selected) Icons.Filled.DirectionsWalk else Icons.Outlined.DirectionsWalk
        TransportMode.BICYCLE -> if (selected) Icons.Filled.DirectionsBike else Icons.Outlined.DirectionsBike
        TransportMode.CAR -> if (selected) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar
        TransportMode.MOTORCYCLE -> if (selected) Icons.Filled.TwoWheeler else Icons.Outlined.TwoWheeler
    }
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(size),
    )
}
