package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R
import kotlinx.coroutines.delay

/**
 * Full-screen blocking loader shown the first time the catalog is being downloaded
 * (or whenever the user requests a manual refresh while the cache is empty).
 * Displays a seconds counter so the user can see something is happening.
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                stringResource(R.string.catalog_loading_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                stringResource(R.string.catalog_loading_subtitle),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.catalog_loading_seconds, seconds),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
