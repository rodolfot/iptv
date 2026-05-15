package com.iptv.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlin.math.max
import kotlinx.coroutines.launch

/**
 * Lightweight pull-to-refresh that works without Material3 1.3+ (which is gated
 * by a newer Compose BOM). Wraps any scrollable child via NestedScroll: when the
 * scroller is at top and the user drags down, we accumulate offset; release past
 * the trigger threshold calls onRefresh.
 */
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { 72.dp.toPx() }
    val maxDragPx = with(density) { 120.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isRefreshing, enabled) {
        // Only show the indicator when this composable is the one driving the
        // refresh (i.e. the platform actually has a pull gesture). On TV we
        // pass enabled=false, so we never want a phantom spinner from
        // background reloads.
        if (isRefreshing && enabled) offset.animateTo(triggerPx) else offset.animateTo(0f)
    }

    val connection = remember(enabled, isRefreshing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || isRefreshing) return Offset.Zero
                // While the indicator is partially visible, scroll-up consumes offset first.
                if (available.y < 0 && offset.value > 0f) {
                    val consume = max(available.y, -offset.value)
                    scope.launch { offset.snapTo(offset.value + consume) }
                    return Offset(0f, consume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!enabled || isRefreshing) return Offset.Zero
                // When the child can't consume more downward drag, we take it.
                if (available.y > 0) {
                    val damped = available.y * 0.55f
                    val next = (offset.value + damped).coerceAtMost(maxDragPx)
                    scope.launch { offset.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enabled || isRefreshing) return Velocity.Zero
                if (offset.value >= triggerPx) {
                    onRefresh()
                } else if (offset.value > 0f) {
                    offset.animateTo(0f)
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(connection)) {
        Box(modifier = Modifier.graphicsLayer { translationY = offset.value }) {
            content()
        }
        if (offset.value > 0f || isRefreshing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { max(offset.value, 0f).toDp() })
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}
