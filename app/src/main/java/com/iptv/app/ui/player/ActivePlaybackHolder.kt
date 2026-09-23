@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory

/**
 * Activity-scoped holder for the currently active ExoPlayer. Exists so the
 * PlayerScreen can be dismissed (back gesture) without releasing playback,
 * and so a mini-player can keep showing the same instance while the user
 * navigates the rest of the app.
 *
 * The holder owns the ExoPlayer: it's created lazily on first playback and
 * released only when the activity goes away or the user explicitly closes
 * the mini-player. The PlayerScreen and the mini-player both read/write
 * through this holder instead of creating their own players.
 */
class ActivePlaybackHolder {
    var player: ExoPlayer? = null
        private set

    /** Latest PlayerArgs that produced the current playback. Drives the mini-player UI. */
    var args = mutableStateOf<PlayerArgs?>(null)

    /** True when the user backed out of PlayerScreen but kept playback alive. */
    var minimized = mutableStateOf(false)

    /** Modo de decoder usado para construir o player atual. Se mudar nas
     *  Configurações, o próximo cold start recria o player com a nova fábrica. */
    var decoderSignature: String? = null
        private set

    fun ensurePlayer(
        context: Context,
        renderersFactory: RenderersFactory? = null,
        signature: String? = null
    ): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        val created = newStreamingPlayer(context, renderersFactory).apply { playWhenReady = true }
        player = created
        decoderSignature = signature
        return created
    }

    fun release() {
        player?.release()
        player = null
        decoderSignature = null
        args.value = null
        minimized.value = false
    }
}

val LocalPlaybackHolder = compositionLocalOf<ActivePlaybackHolder?> { null }
