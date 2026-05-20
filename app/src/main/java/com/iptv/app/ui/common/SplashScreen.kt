package com.iptv.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlin.math.PI
import kotlin.math.sin

/**
 * Splash leve com a animação wave do nome "TartaTV". Mostrado enquanto o
 * RootViewModel ainda não emitiu o primeiro estado do DataStore — em TVs
 * lentas isso leva ~1-2s e antes mostrava uma tela preta.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WaveBrand()
        }
    }
}

@Composable
fun WaveBrand() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "splash-wave")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splash-progress"
    )
    val text = "TartaTV"
    Row(verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { index, ch ->
            val phase = (progress * 2.0 * PI) - (index * (PI / 3.0))
            val wave = sin(phase).toFloat()
            val rise = (-wave).coerceAtLeast(0f)
            val translationY = -rise * 18f
            val alpha = 0.55f + 0.45f * rise
            Text(
                ch.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .graphicsLayer { this.translationY = translationY }
                    .alpha(alpha),
            )
        }
    }
}
