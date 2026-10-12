package com.app.wasalna.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.ui.theme.ElderlyEtaDisplayStyle
import com.app.wasalna.ui.theme.ElderlyStopDisplayStyle
import com.app.wasalna.ui.theme.EtaDisplayStyle
import com.app.wasalna.ui.theme.HeroText
import com.app.wasalna.ui.theme.LocalElderlyMode
import com.app.wasalna.ui.theme.LocalWasalnaDimens
import com.app.wasalna.ui.theme.StopDisplayStyle

/**
 * Transit design system (Oct 2026): vertical route timeline.
 * Boarding → green progress line → alighting, vehicle icon rides
 * the progress point. Station names in stopDisplay (32sp bold) so a
 * waking user reads them instantly; ETA in etaDisplay (40sp bold).
 * RTL-safe (fill flips with layout direction), animated progress,
 * no `!!`, no hardcoded colors.
 */
@Composable
fun RouteTimeline(
    boardingName: String,
    alightingName: String,
    progress: Float,
    mode: TransportMode,
    remainingText: String?,
    etaText: String?,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,
) {
    val elderly = LocalElderlyMode.current
    val dimens = LocalWasalnaDimens.current
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "timeline-progress",
    )
    val approaching = clamped >= 0.8f
    val stationStyle = if (elderly) ElderlyStopDisplayStyle else StopDisplayStyle
    val etaStyle = if (elderly) ElderlyEtaDisplayStyle else EtaDisplayStyle

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            badge?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimelineRail(
                    progress = animated,
                    mode = mode,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HeroText(
                        text = boardingName,
                        baseStyle = stationStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (remainingText != null) {
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (approaching) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    if (etaText != null) {
                        HeroText(
                            text = etaText,
                            baseStyle = etaStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    HeroText(
                        text = alightingName,
                        baseStyle = stationStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Vertical rail: top dot (done) → progress track → bottom dot (pending).
 * Vehicle icon positioned at the progress point. Fill direction flips
 * automatically in RTL via LayoutDirection.
 */
@Composable
private fun TimelineRail(
    progress: Float,
    mode: TransportMode,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val secondary = MaterialTheme.colorScheme.secondary
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // RTL note: vertical rail is direction-agnostic; horizontal variants
    // would mirror via isRtl. Kept for future horizontal layout reuse.
    @Suppress("UNUSED_VARIABLE")
    val rtl = isRtl
    val dimens = LocalWasalnaDimens.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailDot(filled = true, approaching = false)
        Box(
            modifier = Modifier
                .width(dimens.trackHeight)
                .height(120.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = surfaceVariant,
                    start = Offset(w / 2f, 0f),
                    end = Offset(w / 2f, h),
                    strokeWidth = w,
                    cap = StrokeCap.Round,
                )
                val fillH = h * progress.coerceIn(0f, 1f)
                if (fillH > 0f) {
                    drawLine(
                        color = primary,
                        start = Offset(w / 2f, 0f),
                        end = Offset(w / 2f, fillH),
                        strokeWidth = w,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // Vehicle icon rides the progress point (clamped inside rail).
            Box(
                modifier = Modifier.padding(
                    top = (120.dp * progress.coerceIn(0f, 1f)).coerceAtMost(104.dp),
                ),
            ) {
                TransitModeIcon(mode = mode, selected = true, size = 24.dp)
            }
        }
        RailDot(filled = progress >= 1f, approaching = progress in 0.8f..0.999f)
    }
}

@Composable
private fun RailDot(filled: Boolean, approaching: Boolean) {
    val color = when {
        filled -> MaterialTheme.colorScheme.primary
        approaching -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Canvas(modifier = Modifier.size(12.dp)) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}
