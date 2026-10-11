package com.app.wasalna.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.wasalna.ui.theme.LocalElderlyMode

/**
 * Transit design system (Oct 2026): pill-shaped status badges.
 * Colored background + white/on-container text, 16dp icon.
 * Variants: ACTIVE (green pulse), APPROACHING (gold), OFFLINE (grey),
 * ALARM (red). LiveRegion polite for screen readers.
 * RTL-safe (Row/Arrangement), 8dp grid, no `!!`.
 */
enum class BadgeKind {
    ACTIVE,
    APPROACHING,
    OFFLINE,
    ALARM,
}

@Composable
fun StatusBadge(
    kind: BadgeKind,
    text: String,
    modifier: Modifier = Modifier,
) {
    val elderly = LocalElderlyMode.current
    val container: Color
    val onContainer: Color
    val icon: ImageVector
    val pulse = kind == BadgeKind.ACTIVE && !elderly
    when (kind) {
        BadgeKind.ACTIVE -> {
            container = MaterialTheme.colorScheme.primary
            onContainer = MaterialTheme.colorScheme.onPrimary
            icon = Icons.Filled.RadioButtonChecked
        }
        BadgeKind.APPROACHING -> {
            container = MaterialTheme.colorScheme.secondary
            onContainer = MaterialTheme.colorScheme.onSecondary
            icon = Icons.Filled.RadioButtonChecked
        }
        BadgeKind.OFFLINE -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
            icon = Icons.Filled.CloudOff
        }
        BadgeKind.ALARM -> {
            container = MaterialTheme.colorScheme.error
            onContainer = MaterialTheme.colorScheme.onError
            icon = Icons.Filled.Error
        }
    }
    var dotAlpha = 1f
    if (pulse) {
        val transition = rememberInfiniteTransition(label = "badge-pulse")
        val animated by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "badge-dot",
        )
        dotAlpha = animated
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pulse) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(dotAlpha)
                        .background(onContainer, CircleShape),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = onContainer,
            )
        }
    }
}
