package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.data.cache.CatalogCacheRepository
import kotlinx.coroutines.delay

/**
 * Loader exibido durante o primeiro download do catálogo, com a identidade
 * 4.0 (#30): marca no anel luminoso girando; debaixo, a fase atual com barra
 * de progresso (#32) e o contador de segundos — nunca só o spinner.
 */
@Composable
fun CatalogLoadingScreen(progress: CatalogCacheRepository.BootstrapProgress? = null) {
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
            .background(BrandBackground)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BrandLoader()
            Text(
                when (progress?.phase) {
                    "categories" -> stringResource(
                        R.string.catalog_phase_categories,
                        (progress.current + 1).coerceAtMost(progress.total),
                        progress.total
                    )
                    "live" -> stringResource(R.string.catalog_phase_live)
                    else -> stringResource(R.string.catalog_loading_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            // 3 passos de categorias + 1 de canais = 4 etapas; a de canais é
            // a mais demorada, então a barra fica em 75% enquanto ela roda.
            val step = when (progress?.phase) {
                "categories" -> progress.current
                "live" -> 3 + progress.current
                else -> 0
            }
            LinearProgressIndicator(
                progress = { step / 4f },
                modifier = Modifier.width(260.dp),
                color = Brand.Sky,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
            Text(
                stringResource(R.string.catalog_loading_seconds, seconds),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
