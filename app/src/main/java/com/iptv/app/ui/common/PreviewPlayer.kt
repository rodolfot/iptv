package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Player de prévia compartilhado pelas telas de Canais ao Vivo e Favoritos.
 *
 * Comportamento:
 *  - Debounce de 600ms ao mudar `streamUrl` — D-pad rápido não dispara
 *    request por canal/filme intermediário.
 *  - Áudio sempre ligado (a prévia é funcional, não decorativa).
 *  - Retry automático até 5x quando o stream falha (provedores Xtream
 *    derrubam conexão ao trocar rápido).
 *  - Libera o ExoPlayer no onDispose — sem isso o áudio continuaria ao
 *    sair da seção.
 *
 * `streamUrl` null significa "limpar preview" (sem mídia).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PreviewPlayer(
    streamUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exo = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            volume = 1f
            playWhenReady = true
        }
    }
    DisposableEffect(exo) {
        val listener = object : androidx.media3.common.Player.Listener {
            private var attempts = 0
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (attempts >= 5) return
                attempts++
                exo.prepare()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    attempts = 0
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    LaunchedEffect(streamUrl) {
        // Debounce: só carrega após 600ms parado na mesma URL.
        kotlinx.coroutines.delay(600)
        if (streamUrl == null) {
            exo.stop()
            exo.clearMediaItems()
            return@LaunchedEffect
        }
        exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(streamUrl))
        exo.prepare()
        exo.playWhenReady = true
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
