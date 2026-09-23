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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.iptv.app.ui.player.newStreamingPlayer

/**
 * ExoPlayer das prévias (PIP da grade do Ao Vivo, painel da lista com EPG e
 * [PreviewPlayer]).
 *
 * Comportamento:
 *  - Áudio ligado (a prévia é funcional, não decorativa) e buffer pequeno.
 *  - Retry automático até 5x quando o stream falha (provedores Xtream
 *    derrubam conexão ao trocar rápido).
 *  - Para o stream quando o app sai de primeiro plano (TV desligada, botão
 *    Home) e volta ao vivo ao retornar. Antes a prévia continuava baixando e
 *    decodificando com a TV desligada, segurando conexão e memória — depois de
 *    alguns liga/desliga a TV ficava lenta até forçar a parada do app.
 *  - Libera o ExoPlayer no onDispose — sem isso o áudio continuaria ao sair
 *    da seção.
 */
@Composable
fun rememberPreviewExoPlayer(): ExoPlayer {
    val context = LocalContext.current
    val exo = remember {
        newStreamingPlayer(context, preview = true).apply {
            volume = 1f
            playWhenReady = true
        }
    }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            private var attempts = 0
            override fun onPlayerError(error: PlaybackException) {
                if (attempts >= 5) return
                attempts++
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                }
                exo.prepare()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) attempts = 0
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(exo, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exo.stop()
                Lifecycle.Event.ON_START -> if (exo.mediaItemCount > 0 &&
                    exo.playbackState == Player.STATE_IDLE
                ) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return exo
}

/**
 * Prévia 16:9 de uma URL arbitrária (usada em Favoritos). Debounce de 600ms
 * ao mudar `streamUrl` — D-pad rápido não dispara request por item
 * intermediário. `streamUrl` null limpa a prévia.
 */
@Composable
fun PreviewPlayer(
    streamUrl: String?,
    modifier: Modifier = Modifier
) {
    val exo = rememberPreviewExoPlayer()
    LaunchedEffect(streamUrl) {
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
