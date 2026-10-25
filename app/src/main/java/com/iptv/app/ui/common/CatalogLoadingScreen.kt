package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
import kotlinx.coroutines.delay

/**
 * Loader exibido durante o primeiro download/refresh do catálogo, com a
 * identidade 4.0 (#30): marca no anel luminoso girando; debaixo, um contador
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
                stringResource(R.string.catalog_loading_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                stringResource(R.string.catalog_loading_seconds, seconds),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
