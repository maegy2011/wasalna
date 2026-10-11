package com.app.wasalna.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.app.wasalna.ui.theme.LocalElderlyMode
import com.app.wasalna.ui.theme.LocalWasalnaDimens
import kotlin.math.roundToInt

/**
 * Transit design system (Oct 2026): large swipe-to-stop alarm slider.
 * Prevents accidental dismissals — requires a deliberate 80% drag.
 * Green thumb on a muted track, label fades as the thumb moves,
 * snap-back animation when released early. Haptic on completion.
 * RTL-aware (drag direction flips), elderly sizes scale up.
 * No `!!`, all motion respects reduced-motion via elderly flag.
 */
@Composable
fun SwipeToStopSlider(
    onStop: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalWasalnaDimens.current
    val elderly = LocalElderlyMode.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragFrac by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableFloatStateOf(0f) }
    // When settling (snap-back), animate the displayed fraction.
    val displayed by animateFloatAsState(
        targetValue = dragFrac + settling,
        animationSpec = tween(if (elderly) 150 else 300, easing = FastOutSlowInEasing),
        label = "swipe-settle",
    )
    val shown = if (settling != 0f) displayed else dragFrac

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.slideHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val maxPx = with(density) {
            (maxWidth - dimens.thumbDiameter - 8.dp).toPx().coerceAtLeast(1f)
        }
        val thumbDiameter = dimens.thumbDiameter
        // Label fades as the thumb advances.
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha((1f - shown * 1.4f).coerceIn(0f, 1f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(maxPx, isRtl) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragFrac >= 0.8f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStop()
                            } else {
                                // Snap back: animate from current to zero.
                                settling = -dragFrac
                                dragFrac = 0f
                                settling = 0f
                            }
                        },
                        onDragCancel = {
                            settling = -dragFrac
                            dragFrac = 0f
                            settling = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val delta = if (isRtl) -dragAmount else dragAmount
                            dragFrac = (dragFrac + delta / maxPx).coerceIn(0f, 1f)
                        },
                    )
                }
                .padding(4.dp),
        ) {
            val offsetPx = (shown * maxPx).roundToInt()
            val xOffset = if (isRtl) -offsetPx else offsetPx
            Box(
                modifier = Modifier
                    .offset { IntOffset(xOffset, 0) }
                    .size(thumbDiameter)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
