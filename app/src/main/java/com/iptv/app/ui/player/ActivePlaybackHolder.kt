package com.iptv.app.ui.player

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.exoplayer.ExoPlayer

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

    fun ensurePlayer(context: Context): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        val created = ExoPlayer.Builder(context.applicationContext).build().apply {
            playWhenReady = true
        }
        player = created
        return created
    }

    fun release() {
        player?.release()
        player = null
        args.value = null
        minimized.value = false
    }
}

val LocalPlaybackHolder = compositionLocalOf<ActivePlaybackHolder?> { null }
