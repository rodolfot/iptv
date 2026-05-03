package com.iptv.app.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.EpisodeProgressEntity
import com.iptv.app.domain.model.Episode
import com.iptv.app.domain.model.toModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val items: List<PlayableItem> = emptyList(),
    val currentIndex: Int = 0,
    val countdownSeconds: Int = 0,
    val title: String = ""
)

data class PlayableItem(
    val url: String,
    val title: String,
    val episodeId: String? = null,
    val seriesId: Int = -1,
    val seasonNumber: Int = -1,
    val episodeNum: Int = -1,
    val startPositionMs: Long = 0L
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val progressDao: EpisodeProgressDao
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    fun load(args: PlayerArgs) {
        viewModelScope.launch {
            when (args.kind) {
                PlayerKind.LIVE -> {
                    val url = repo.liveStreamUrl(args.streamId, hls = false)
                    _state.value = PlayerUiState(
                        items = listOf(PlayableItem(url, args.title)),
                        title = args.title
                    )
                }
                PlayerKind.MOVIE -> {
                    val url = repo.movieStreamUrl(args.streamId, args.containerExtension)
                    _state.value = PlayerUiState(
                        items = listOf(PlayableItem(url, args.title, startPositionMs = args.startPositionMs)),
                        title = args.title
                    )
                }
                PlayerKind.EPISODE -> {
                    runCatching { repo.seriesInfo(args.seriesId) }.onSuccess { info ->
                        val seasons = info.seasons?.sortedBy { it.seasonNumber ?: 0 } ?: emptyList()
                        val orderedEpisodes = mutableListOf<Episode>()
                        val episodesMap = info.episodes ?: emptyMap()
                        val keys = episodesMap.keys.mapNotNull { it.toIntOrNull() }.sorted()
                        keys.forEach { sk ->
                            episodesMap[sk.toString()]
                                ?.sortedBy { it.episodeNum ?: 0 }
                                ?.forEach { e -> orderedEpisodes.add(e.toModel(args.seriesId, sk)) }
                        }
                        if (orderedEpisodes.isEmpty()) {
                            // fallback: single episode by id
                            val url = repo.episodeStreamUrl(args.episodeId, args.containerExtension)
                            _state.value = PlayerUiState(
                                items = listOf(PlayableItem(url, args.title, episodeId = args.episodeId, seriesId = args.seriesId, startPositionMs = args.startPositionMs)),
                                title = args.title
                            )
                            return@onSuccess
                        }
                        val items = orderedEpisodes.map { e ->
                            PlayableItem(
                                url = repo.episodeStreamUrl(e.id, e.containerExtension),
                                title = "T${e.seasonNumber}E${e.episodeNum} • ${e.title}",
                                episodeId = e.id,
                                seriesId = e.seriesId,
                                seasonNumber = e.seasonNumber,
                                episodeNum = e.episodeNum
                            )
                        }
                        val startIndex = items.indexOfFirst { it.episodeId == args.episodeId }.coerceAtLeast(0)
                        val resumeMs = progressDao.getById(args.episodeId)?.takeIf { !it.watched }?.positionMs ?: 0L
                        _state.value = PlayerUiState(
                            items = items.mapIndexed { idx, it ->
                                if (idx == startIndex) it.copy(startPositionMs = resumeMs) else it
                            },
                            currentIndex = startIndex,
                            title = items.getOrNull(startIndex)?.title ?: args.title
                        )
                    }
                }
            }
        }
    }

    fun onTransition(index: Int) {
        val item = _state.value.items.getOrNull(index) ?: return
        _state.value = _state.value.copy(currentIndex = index, title = item.title)
    }

    fun saveProgress(episodeId: String?, seriesId: Int, season: Int, episodeNum: Int, position: Long, duration: Long) {
        if (episodeId.isNullOrBlank() || seriesId < 0) return
        viewModelScope.launch {
            val watched = duration > 0 && position >= duration - 30_000L
            progressDao.upsert(
                EpisodeProgressEntity(
                    episodeId = episodeId,
                    seriesId = seriesId,
                    seasonNumber = season,
                    episodeNum = episodeNum,
                    positionMs = position,
                    durationMs = duration,
                    watched = watched
                )
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    args: PlayerArgs,
    onClose: () -> Unit,
    vm: PlayerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(args) { vm.load(args) }

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(state.items) {
        if (state.items.isNotEmpty()) {
            val mediaItems = state.items.map { MediaItem.fromUri(it.url) }
            exo.setMediaItems(mediaItems, state.currentIndex, state.items.getOrNull(state.currentIndex)?.startPositionMs ?: 0L)
            exo.prepare()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = exo.currentMediaItemIndex
                vm.onTransition(idx)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val item = state.items.getOrNull(exo.currentMediaItemIndex) ?: return
                    vm.saveProgress(
                        item.episodeId,
                        item.seriesId,
                        item.seasonNumber,
                        item.episodeNum,
                        exo.currentPosition,
                        exo.duration.coerceAtLeast(0L)
                    )
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            val item = state.items.getOrNull(exo.currentMediaItemIndex)
            if (item != null) {
                vm.saveProgress(
                    item.episodeId,
                    item.seriesId,
                    item.seasonNumber,
                    item.episodeNum,
                    exo.currentPosition,
                    exo.duration.coerceAtLeast(0L)
                )
            }
            exo.removeListener(listener)
            exo.release()
        }
    }

    BackHandler { onClose() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )
        Column(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Text(state.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}
