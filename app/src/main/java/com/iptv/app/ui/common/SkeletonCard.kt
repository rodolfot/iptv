package com.iptv.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Card vazio (placeholder) com efeito shimmer pra disfarçar tempo de
 * carregamento de listas. Usado quando o cache está vazio e o catálogo
 * ainda está sendo baixado.
 */
@Composable
fun SkeletonPosterCard(width: androidx.compose.ui.unit.Dp = 110.dp) {
    val alpha by shimmerAlpha()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
    }
}

@Composable
fun SkeletonRow(itemCount: Int = 8, cardWidth: androidx.compose.ui.unit.Dp = 110.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(itemCount) { SkeletonPosterCard(cardWidth) }
    }
}

@Composable
fun SkeletonSection(label: String, modifier: Modifier = Modifier) {
    val alpha by shimmerAlpha()
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Label do "section header"
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
        SkeletonRow()
    }
}

@Composable
private fun shimmerAlpha(): androidx.compose.runtime.State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer-alpha"
    )
}
