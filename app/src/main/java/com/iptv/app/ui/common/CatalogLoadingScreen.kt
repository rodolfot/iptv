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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Loader exibido durante o primeiro download/refresh do catálogo. Letras de
 * "TartaTV" pulam em sequência (efeito wave) e brilham; debaixo, um contador
 * de segundos mostra que o app não travou.
 */
@Composable
fun CatalogLoadingScreen() {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        while (true) {
            seconds = (System.currentTimeMillis() - started) / 1000
            delay(1000)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WaveBrand(text = "TartaTV")
            Text(
                stringResource(R.string.catalog_loading_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.catalog_loading_seconds, seconds),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Brand wordmark animado: cada letra sobe e desce em fase deslocada,
 * gerando uma "onda" que percorre o nome. Cor varia entre primary e
 * onSurface para dar um brilho sutil.
 */
@Composable
private fun WaveBrand(text: String) {
    val transition = rememberInfiniteTransition(label = "wave-brand")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-progress"
    )
    Row(verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { index, ch ->
            // Cada letra tem um offset de fase proporcional ao seu índice —
            // a onda parece "andar" por elas.
            val phase = (progress * 2.0 * PI) - (index * (PI / 3.0))
            val wave = sin(phase).toFloat() // -1..1
            val rise = (-wave).coerceAtLeast(0f) // só puxa pra cima
            val translationY = -rise * 18f // px
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
