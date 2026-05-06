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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.EpisodeProgressEntity
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
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
    val startPositionMs: Long = 0L,
    val movieId: Int = -1,
    val moviePoster: String? = null,
    val movieContainer: String? = null,
    val movieCategory: String? = null,
    val seriesTitle: String? = null,
    val seriesCover: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val progressDao: EpisodeProgressDao,
    private val movieProgressDao: MovieProgressDao,
    private val seriesProgressDao: SeriesProgressDao
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    fun load(args: PlayerArgs) {
        viewModelScope.launch {
            when (args.kind) {
                PlayerKind.LIVE -> {
                    val url = if (args.timeshiftStartMs > 0L) {
                        repo.timeshiftUrl(
                            streamId = args.streamId,
                            startMs = args.timeshiftStartMs,
                            durationMin = args.timeshiftDurationMin
                        )
                    } else {
                        repo.liveStreamUrl(args.streamId, hls = false)
                    }
                    _state.value = PlayerUiState(
                        items = listOf(PlayableItem(url, args.title)),
                        title = args.title
                    )
                }
                PlayerKind.MOVIE -> {
                    val url = repo.movieStreamUrl(args.streamId, args.containerExtension)
                    val savedResume = movieProgressDao.getById(args.streamId)
                        ?.takeIf { !it.watched }?.positionMs ?: 0L
                    val resumeMs = if (args.startPositionMs > 0L) args.startPositionMs else savedResume
                    _state.value = PlayerUiState(
                        items = listOf(
                            PlayableItem(
                                url = url,
                                title = args.title,
                                startPositionMs = resumeMs,
                                movieId = args.streamId,
                                moviePoster = args.posterUrl,
                                movieContainer = args.containerExtension,
                                movieCategory = args.categoryId
                            )
                        ),
                        title = args.title
                    )
                }
                PlayerKind.EPISODE -> {
                    runCatching { repo.seriesInfo(args.seriesId) }.onSuccess { info ->
                        val seriesTitle = info.info?.name ?: args.title
                        val seriesCover = info.info?.cover ?: args.posterUrl
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
                                items = listOf(
                                    PlayableItem(
                                        url = url,
                                        title = args.title,
                                        episodeId = args.episodeId,
                                        seriesId = args.seriesId,
                                        startPositionMs = args.startPositionMs,
                                        seriesTitle = seriesTitle,
                                        seriesCover = seriesCover
                                    )
                                ),
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
                                episodeNum = e.episodeNum,
                                seriesTitle = seriesTitle,
                                seriesCover = seriesCover
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

    fun saveProgress(item: PlayableItem, position: Long, duration: Long) {
        if (position <= 0L) return
        val watched = duration > 0 && position >= duration - 30_000L
        viewModelScope.launch {
            when {
                !item.episodeId.isNullOrBlank() && item.seriesId >= 0 -> {
                    progressDao.upsert(
                        EpisodeProgressEntity(
                            episodeId = item.episodeId,
                            seriesId = item.seriesId,
                            seasonNumber = item.seasonNumber,
                            episodeNum = item.episodeNum,
                            positionMs = position,
                            durationMs = duration,
                            watched = watched
                        )
                    )
                    seriesProgressDao.upsert(
                        SeriesProgressEntity(
                            seriesId = item.seriesId,
                            title = item.seriesTitle ?: item.title,
                            coverUrl = item.seriesCover,
                            lastEpisodeId = item.episodeId,
                            lastSeasonNumber = item.seasonNumber,
                            lastEpisodeNum = item.episodeNum
                        )
                    )
                }
                item.movieId >= 0 -> {
                    if (watched) {
                        movieProgressDao.delete(item.movieId)
                    } else {
                        movieProgressDao.upsert(
                            MovieProgressEntity(
                                movieId = item.movieId,
                                title = item.title,
                                posterUrl = item.moviePoster,
                                containerExtension = item.movieContainer,
                                categoryId = item.movieCategory,
                                positionMs = position,
                                durationMs = duration,
                                watched = false
                            )
                        )
                    }
                }
            }
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
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }
    var trackPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(args) { vm.load(args) }

    // Enable PiP for this screen, restore previous state on dispose.
    DisposableEffect(Unit) {
        val activity = context as? com.iptv.app.MainActivity
        val previous = activity?.pipEnabled
        activity?.pipEnabled = true
        onDispose { activity?.pipEnabled = previous ?: false }
    }

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

    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isEmpty()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            val item = state.items.getOrNull(exo.currentMediaItemIndex) ?: continue
            val pos = exo.currentPosition
            val dur = exo.duration.coerceAtLeast(0L)
            if (exo.isPlaying && pos > 0L) {
                vm.saveProgress(item, pos, dur)
            }
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = exo.currentMediaItemIndex
                vm.onTransition(idx)
                playbackError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = friendlyPlaybackError(error)
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
        }
        exo.addListener(listener)
        onDispose {
            val item = state.items.getOrNull(exo.currentMediaItemIndex)
            if (item != null) {
                vm.saveProgress(
                    item,
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
        if (currentTracks != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) {
                Button(onClick = { trackPickerOpen = true }) {
                    Text(androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_tracks))
                }
            }
        }
        if (trackPickerOpen) {
            currentTracks?.let { tracks ->
                TrackPickerDialog(
                    player = exo,
                    tracks = tracks,
                    onDismiss = { trackPickerOpen = false }
                )
            }
        }
        playbackError?.let { msg ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Não foi possível reproduzir",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                    )
                    Button(onClick = onClose) { Text(androidx.compose.ui.res.stringResource(com.iptv.app.R.string.back)) }
                }
            }
        }
    }
}

private fun friendlyPlaybackError(error: PlaybackException): String {
    val cause = error.cause
    val msg = (cause?.message ?: error.message ?: "").lowercase()
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED -> {
            if ("hevc" in msg || "exceeds capabilities" in msg || "no_exceeds_capabilities" in msg) {
                "Este conteúdo está em formato 4K/HDR (HEVC) que não é suportado por este dispositivo. Tente uma versão SD/HD ou rode no aparelho de TV."
            } else {
                "Formato de vídeo não suportado por este dispositivo."
            }
        }
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "Falha de conexão com o servidor. Verifique a internet."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "O servidor recusou a transmissão (HTTP). Pode ser limite de conexões ou conteúdo indisponível."
        else -> "Erro ao reproduzir: ${error.errorCodeName}."
    }
}
