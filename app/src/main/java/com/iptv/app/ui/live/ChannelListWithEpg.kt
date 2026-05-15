@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil.compose.AsyncImage
import com.iptv.app.data.db.EpgProgrammeEntity
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vertical list of channels with a side panel showing the EPG of the focused
 * channel. UX:
 *  - Move the focus through the list -> the right panel updates with the
 *    schedule of the highlighted channel (current programme + upcoming).
 *  - Press OK -> opens the player immediately (canais ao vivo não têm tela
 *    intermediária de detalhe — o usuário só quer assistir).
 *  - Long-press OK -> toggles favorite for the focused channel.
 */
@Composable
fun ChannelListWithEpg(
    vm: HomeViewModel,
    channels: List<LiveChannel>,
    epgNow: Map<String, EpgProgrammeEntity>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    onPlay: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    var focusedIndex by remember(channels) { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val favorites by vm.favorites.collectAsState()
    val snackbar = com.iptv.app.ui.common.LocalSnackbar.current
    val favoriteAddedMsg = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.snack_favorite_added)
    val favoriteRemovedMsg = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.snack_favorite_removed)

    // Dispara quando a lista passa de vazia para preenchida. Sem isso, o
    // primeiro LaunchedEffect(channels) acontecia com a lista ainda vazia
    // (recompôs antes do ViewModel emitir) e o foco caia no primeiro
    // focusable da árvore — a TabRow (Início).
    val channelsReady = channels.isNotEmpty()
    LaunchedEffect(channelsReady) {
        if (channelsReady) {
            // Aguarda um frame para garantir que o FocusRequester já foi
            // anexado ao composable da primeira linha.
            kotlinx.coroutines.delay(50)
            runCatching { firstFocus.requestFocus() }
        }
    }

    val focusedChannel = channels.getOrNull(focusedIndex)

    Row(modifier = modifier.fillMaxSize()) {
        // Lista compacta à esquerda; player grande à direita (padrão dos
        // apps de IPTV como o Smarters Player). Antes a proporção estava
        // invertida — lista enorme e player apertado.
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(end = 8.dp, bottom = 12.dp),
            modifier = Modifier.width(340.dp).fillMaxHeight()
        ) {
            items(items = channels, key = { it.id }) { channel ->
                val index = channels.indexOf(channel)
                val locked = isCategoryAdult && !isParentalUnlocked
                val now = channel.epgChannelId?.let { epgNow[it] }
                val isFavorite = favorites.any {
                    it.type == com.iptv.app.domain.model.ContentType.LIVE && it.itemId == channel.id
                }
                ChannelRow(
                    channel = channel,
                    nowPlaying = now?.title,
                    nowProgress = now?.let {
                        val span = (it.stopMs - it.startMs).coerceAtLeast(1)
                        ((System.currentTimeMillis() - it.startMs).toFloat() / span)
                            .coerceIn(0f, 1f)
                    },
                    locked = locked,
                    isFavorite = isFavorite,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) focusedIndex = index },
                    onClick = { onPlay(channel) },
                    onLongClick = {
                        val wasFavorite = isFavorite
                        vm.toggleFavorite(
                            com.iptv.app.data.db.FavoriteEntity(
                                profileId = "",
                                type = com.iptv.app.domain.model.ContentType.LIVE,
                                itemId = channel.id,
                                name = channel.name,
                                logoUrl = channel.logoUrl,
                                categoryId = channel.categoryId,
                                containerExtension = null
                            )
                        )
                        snackbar?.show(if (wasFavorite) favoriteRemovedMsg else favoriteAddedMsg)
                    }
                )
            }
        }

        EpgPanel(
            channel = focusedChannel,
            vm = vm,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    nowPlaying: String?,
    nowProgress: Float?,
    locked: Boolean,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Linha compacta no estilo dos apps de IPTV: número | logo | nome.
    // EPG completo aparece no painel grande à direita.
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.num != null) {
                Text(
                    channel.num.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Filled.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            if (isFavorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(start = 4.dp)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun EpgPanel(
    channel: LiveChannel?,
    vm: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val schedule by produceState<List<EpgProgrammeEntity>>(initialValue = emptyList(), channel?.id) {
        value = if (channel == null) emptyList() else vm.epgScheduleFor(channel.epgChannelId)
    }

    Box(
        modifier = modifier.padding(start = 12.dp)
    ) {
        if (channel == null) {
            Text(
                "Sem canal selecionado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
            return@Box
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // Player de prévia ocupa todo o topo do painel (16:9).
            ChannelPreviewPlayer(channel = channel, vm = vm)

            // Faixa abaixo do player: nome do canal + LIVE badge.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (schedule.isEmpty()) {
                Text(
                    "Sem programação disponível",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(schedule.take(20)) { p ->
                    EpgEntry(
                        programme = p,
                        isCurrent = isLive(p)
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ChannelPreviewPlayer(channel: LiveChannel, vm: HomeViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exo = androidx.compose.runtime.remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            volume = 0f // silencioso por padrão; o canal focado é só prévia
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { exo.release() }
    }
    // Debounce: só carrega a stream após 600ms parado no canal — D-pad
    // rápido não desperdiça requests.
    androidx.compose.runtime.LaunchedEffect(channel.id) {
        kotlinx.coroutines.delay(600)
        val url = vm.previewUrl(channel.id) ?: return@LaunchedEffect
        exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
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

@Composable
private fun EpgEntry(programme: EpgProgrammeEntity, isCurrent: Boolean) {
    val time = remember(programme.startMs, programme.stopMs) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        "${fmt.format(Date(programme.startMs))} – ${fmt.format(Date(programme.stopMs))}"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            programme.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!programme.description.isNullOrBlank()) {
            Text(
                programme.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun isLive(p: EpgProgrammeEntity): Boolean {
    val now = System.currentTimeMillis()
    return now in p.startMs..p.stopMs
}
