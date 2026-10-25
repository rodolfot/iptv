package com.iptv.app.ads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.ui.common.TouchableButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class AdsViewModel @Inject constructor(
    val ads: AdsManager
) : ViewModel()

/**
 * Banner discreto da versão gratuita. Não é focável — o D-pad passa direto
 * por ele, sem desviar a navegação nem cobrir botões —, ocupa no máximo 56dp
 * e some sozinho para Premium, sem configuração de anúncios ou sem banner
 * para esta posição. No toque (celular/tablet) abre o link do anunciante.
 */
@Composable
fun AdBanner(placement: AdPlacement, modifier: Modifier = Modifier, vm: AdsViewModel = hiltViewModel()) {
    val spec by remember(placement) { vm.ads.banner(placement) }.collectAsState(initial = null)
    val banner = spec ?: return
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .focusProperties { canFocus = false }
            .then(
                if (banner.clickUrl != null) Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(banner.clickUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.ad_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        AsyncImage(
            model = banner.imageUrl,
            contentDescription = banner.text,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(48.dp).weight(1f, fill = banner.text == null)
        )
        banner.text?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Intervalo comercial em tela cheia (pre-roll/post-roll). Usa um ExoPlayer
 * próprio e descartável — o player do conteúdo não é tocado, então zapping,
 * retomada e reconexão continuam como estavam. Chama [onFinished] ao terminar,
 * ao pular, em erro ou se o anúncio não começar em [startTimeoutMs].
 */
@Composable
fun AdBreak(
    ad: VastAd,
    ads: AdsManager,
    onFinished: () -> Unit,
    startTimeoutMs: Long = 6_000L
) {
    val context = LocalContext.current
    val player = remember(ad) { ExoPlayer.Builder(context.applicationContext).build() }
    var elapsedMs by remember(ad) { mutableLongStateOf(0L) }
    var started by remember(ad) { mutableStateOf(false) }
    var done by remember(ad) { mutableStateOf(false) }
    val finish: (String?) -> Unit = { event ->
        if (!done) {
            done = true
            event?.let { ads.ping(ad.tracking[it].orEmpty()) }
            onFinished()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !started) {
                    started = true
                    ads.markShown()
                    ads.ping(ad.impressions)
                    ads.ping(ad.tracking["start"].orEmpty())
                }
                if (state == Player.STATE_ENDED) finish("complete")
            }

            override fun onPlayerError(error: PlaybackException) = finish("error")
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(ad.mediaUrl))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // Progresso, quartis e timeout de início.
    LaunchedEffect(player) {
        val quartiles = listOf(0.25 to "firstQuartile", 0.5 to "midpoint", 0.75 to "thirdQuartile")
        val fired = mutableSetOf<String>()
        var waited = 0L
        while (!done) {
            delay(250)
            if (!started) {
                waited += 250
                if (waited >= startTimeoutMs) finish(null)
                continue
            }
            elapsedMs = player.currentPosition
            val duration = player.duration.takeIf { it > 0 } ?: ad.durationMs
            if (duration > 0) {
                quartiles.forEach { (fraction, name) ->
                    if (name !in fired && elapsedMs >= duration * fraction) {
                        fired += name
                        ads.ping(ad.tracking[name].orEmpty())
                    }
                }
            }
        }
    }

    val skipFocus = remember { FocusRequester() }
    val canSkip = ad.skipOffsetMs != null && elapsedMs >= ad.skipOffsetMs
    LaunchedEffect(canSkip) {
        if (canSkip) runCatching { skipFocus.requestFocus() }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        val duration = (player.duration.takeIf { it > 0 } ?: ad.durationMs).coerceAtLeast(0L)
        val remainingSec = ((duration - elapsedMs).coerceAtLeast(0L) + 999) / 1000
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xAA000000))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                if (duration > 0) stringResource(R.string.ad_playing_countdown, remainingSec)
                else stringResource(R.string.ad_label),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
        if (ad.skipOffsetMs != null) {
            val waitSec = ((ad.skipOffsetMs - elapsedMs).coerceAtLeast(0L) + 999) / 1000
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)) {
                if (canSkip) {
                    TouchableButton(
                        onClick = { finish("skip") },
                        modifier = Modifier.focusRequester(skipFocus)
                    ) { Text(stringResource(R.string.ad_skip)) }
                } else {
                    Text(
                        stringResource(R.string.ad_skip_in, waitSec),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xAA000000))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
