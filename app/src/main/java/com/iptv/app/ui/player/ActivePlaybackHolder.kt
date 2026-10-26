@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.Player
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
        screenAwake = ScreenAwakeLock(context).also(created::addListener)
        player = created
        decoderSignature = signature
        return created
    }

    private var screenAwake: ScreenAwakeLock? = null

    fun release() {
        screenAwake?.release()
        screenAwake = null
        AudioOutputOverride.forcePcm = false
        player?.release()
        player = null
        decoderSignature = null
        args.value = null
        minimized.value = false
    }
}

/**
 * Mantém a tela ligada enquanto o vídeo toca — em tela cheia, no miniplayer
 * e no PiP. FLAG_KEEP_SCREEN_ON na janela não basta: em TVs TCL/Google TV a
 * janela do app fica "encoberta" por uma camada do sistema (mHoldScreenWindow
 * nulo) e, no PiP, a Activity está pausada — a TV contava como inatividade e
 * abria o aviso de desligamento. O wake lock de tela vale enquanto
 * isPlaying for true e é solto na pausa, no fim ou ao liberar o player.
 */
@Suppress("DEPRECATION") // SCREEN_BRIGHT_WAKE_LOCK: único jeito sem janela em foco
private class ScreenAwakeLock(context: Context) : Player.Listener {
    private val lock = (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "tartatv:playback-screen"
        )
        .apply { setReferenceCounted(false) }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // Timeout de segurança: um vazamento nunca deixa a TV acesa para sempre.
        if (isPlaying) lock.acquire(MAX_HOLD_MS) else release()
    }

    fun release() {
        if (lock.isHeld) lock.release()
    }

    private companion object {
        const val MAX_HOLD_MS = 6 * 60 * 60 * 1000L
    }
}

val LocalPlaybackHolder = compositionLocalOf<ActivePlaybackHolder?> { null }
