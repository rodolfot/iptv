@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.iptv.app.R
import kotlinx.coroutines.delay

/** Feedback visual de um gesto (ícone + nível 0..1 ou texto). */
private data class GestureFeedback(val icon: ImageVector, val level: Float?, val text: String?)

/**
 * Camada de gestos do player em telas touch (#22):
 *  - deslizar na vertical na metade esquerda → brilho (só desta janela);
 *  - deslizar na vertical na metade direita → volume de mídia;
 *  - toque duplo na esquerda/direita → −10s / +10s (desligado no Ao Vivo);
 *  - toque simples → mostra/esconde os controles.
 * Cada gesto mostra um indicador no centro da tela com o nível atual.
 */
@Composable
fun PlayerGestureLayer(
    seekEnabled: Boolean,
    onSeekBy: (Long) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var feedbackTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedbackTick) {
        if (feedbackTick == 0) return@LaunchedEffect
        delay(900)
        feedback = null
    }
    val seekLabelBack = stringResource(R.string.player_gesture_seek_back)
    val seekLabelForward = stringResource(R.string.player_gesture_seek_forward)
    val currentSeekEnabled by rememberUpdatedState(seekEnabled)

    // Brilho começa no valor atual da janela (ou 50% se o sistema decide).
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
    }
    // Volta o brilho para o controle do sistema ao sair do player.
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        listOf(true, false).forEach { isLeft ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(isLeft) {
                        detectTapGestures(
                            onTap = { onToggleControls() },
                            onDoubleTap = {
                                if (currentSeekEnabled) {
                                    onSeekBy(if (isLeft) -SEEK_STEP_MS else SEEK_STEP_MS)
                                    feedback = GestureFeedback(
                                        if (isLeft) Icons.Filled.FastRewind else Icons.Filled.FastForward,
                                        null,
                                        if (isLeft) seekLabelBack else seekLabelForward
                                    )
                                    feedbackTick++
                                }
                            }
                        )
                    }
                    .pointerInput(isLeft) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            // Arrastar a altura inteira da tela = 0% → 100%.
                            val delta = -dragAmount / size.height
                            if (isLeft) {
                                brightness = (brightness + delta).coerceIn(0.02f, 1f)
                                activity?.window?.let { w ->
                                    w.attributes = w.attributes.apply { screenBrightness = brightness }
                                }
                                feedback = GestureFeedback(Icons.Filled.BrightnessMedium, brightness, null)
                            } else if (audio != null) {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val target = (current + delta * max * VOLUME_SENSITIVITY)
                                val rounded = target.toInt().coerceIn(0, max)
                                if (rounded != current) {
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, rounded, 0)
                                }
                                feedback = GestureFeedback(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    rounded.toFloat() / max,
                                    null
                                )
                            }
                            feedbackTick++
                        }
                    }
            )
        }
    }
    feedback?.let { fb ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(fb.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                fb.level?.let { level ->
                    LinearProgressIndicator(
                        progress = { level },
                        modifier = Modifier.width(140.dp).height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
                fb.text?.let { Text(it, color = Color.White, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** Passo do seek por toque duplo e pelas teclas ←/→. */
const val SEEK_STEP_MS = 10_000L

/** Deslizar a tela inteira percorre ~1,5x a escala de volume (ajuste fino). */
private const val VOLUME_SENSITIVITY = 1.5f

/**
 * MediaSession do player em tela cheia (#23). É por ela que chegam os
 * comandos que não passam pela janela do app: botões ⏭/⏮/⏯ do volante (a
 * central traduz o CANBUS em comandos de mídia), controles Bluetooth,
 * fones e o "Ok Google, pausar".
 *
 * No Ao Vivo, ⏭/⏮ trocam de canal ([onNext]/[onPrevious]) — o player do
 * Media3 sozinho tentaria pular para o próximo item da playlist, que num canal
 * ao vivo não existe. Em filme/série seguem o comportamento padrão.
 */
@Composable
fun rememberPlayerMediaSession(
    player: Player,
    isLive: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    val currentIsLive by rememberUpdatedState(isLive)
    val currentNext by rememberUpdatedState(onNext)
    val currentPrevious by rememberUpdatedState(onPrevious)
    DisposableEffect(player) {
        val forwarding = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    super.isCommandAvailable(command)

            override fun seekToNext() {
                if (currentIsLive) currentNext() else super.seekToNext()
            }

            override fun seekToPrevious() {
                if (currentIsLive) currentPrevious() else super.seekToPrevious()
            }

            override fun seekToNextMediaItem() {
                if (currentIsLive) currentNext() else super.seekToNextMediaItem()
            }

            override fun seekToPreviousMediaItem() {
                if (currentIsLive) currentPrevious() else super.seekToPreviousMediaItem()
            }
        }
        val session = runCatching {
            MediaSession.Builder(context, forwarding)
                .setId("tartatv-player-${System.identityHashCode(player)}")
                .build()
        }.getOrNull()
        onDispose { session?.release() }
    }
}

/**
 * Celular: o player em tela cheia gira para paisagem (sensor, nos dois
 * sentidos) e as listas voltam a seguir a orientação do aparelho ao sair
 * (#22). Tablets, TV e carro mantêm a orientação livre.
 */
@Composable
fun LandscapeWhilePlaying(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = previous }
    }
}

/**
 * Android 12+: entra em Picture-in-Picture sozinho ao minimizar o app pelo
 * gesto de Home (transição suave, sem esperar o onUserLeaveHint), mantendo
 * áudio e vídeo (#22). Abaixo do 12, o MainActivity cuida via
 * onUserLeaveHint.
 */
@Composable
fun AutoEnterPip(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    DisposableEffect(enabled) {
        runCatching {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(enabled)
                    .setSeamlessResizeEnabled(true)
                    .build()
            )
        }
        onDispose {
            runCatching {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                )
            }
        }
    }
}

/** Selo "Dolby Atmos · 7.1 · Passthrough" ao lado do título (#18). */
@Composable
fun AudioBadgeChip(badge: AudioBadge, modifier: Modifier = Modifier) {
    val text = badge.text(stringResource(R.string.player_audio_passthrough_badge))
    if (text.isBlank()) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Filled.SurroundSound,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/**
 * Aviso de faixa de áudio sem suporte (anti-mudo, #18): o conteúdo só tem,
 * por exemplo, DTS e o aparelho não decodifica nem repassa. Em vez de vídeo
 * mudo sem explicação, diz o que houve e oferece o player externo.
 */
@Composable
fun UnsupportedAudioBanner(
    codec: String?,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE6000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.player_audio_unsupported, codec ?: "?"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        com.iptv.app.ui.common.TouchableButton(compact = true, onClick = onOpenExternal) {
            Text(stringResource(R.string.player_open_external_short))
        }
    }
}
