package com.iptv.app.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.EpisodeProgressEntity
import com.iptv.app.data.db.LiveCacheDao
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.data.prefs.CurrentProfile
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
    private val seriesProgressDao: SeriesProgressDao,
    private val liveCache: LiveCacheDao,
    private val currentProfile: CurrentProfile
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    fun load(args: PlayerArgs) {
        viewModelScope.launch {
            when (args.kind) {
                PlayerKind.LIVE -> {
                    // If the cached channel carries a concrete streamUrl (M3U import),
                    // play that directly — bypassing Xtream URL construction.
                    val cached = liveCache.getById(args.streamId)
                    val directUrl = cached?.streamUrl
                    val url = when {
                        directUrl != null && args.timeshiftStartMs == 0L -> directUrl
                        args.timeshiftStartMs > 0L -> repo.timeshiftUrl(
                            streamId = args.streamId,
                            startMs = args.timeshiftStartMs,
                            durationMin = args.timeshiftDurationMin
                        )
                        else -> repo.liveStreamUrl(args.streamId, hls = false)
                    }
                    _state.value = PlayerUiState(
                        items = listOf(PlayableItem(url, args.title)),
                        title = args.title
                    )
                }
                PlayerKind.MOVIE -> {
                    val url = repo.movieStreamUrl(args.streamId, args.containerExtension)
                    val savedResume = movieProgressDao.getById(currentProfile.id(), args.streamId)
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
                        // Group by each episode's `season` field, not by the
                        // map key — providers sometimes lump everything under
                        // "0" but still carry the right season per episode.
                        // Same fix as in SeriesDetailViewModel.
                        val episodesMap = info.normalizedEpisodes()
                        val allEpisodes = episodesMap.flatMap { (key, list) ->
                            val fallback = key.toIntOrNull() ?: 0
                            list.map { it.toModel(args.seriesId, fallback) }
                        }
                        val orderedEpisodes = allEpisodes.sortedWith(
                            compareBy({ it.seasonNumber }, { it.episodeNum })
                        )
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
                        val resumeMs = progressDao.getById(currentProfile.id(), args.episodeId)?.takeIf { !it.watched }?.positionMs ?: 0L
                        _state.value = PlayerUiState(
                            items = items.mapIndexed { idx, item ->
                                if (idx == startIndex) item.copy(startPositionMs = resumeMs) else item
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
            val pid = currentProfile.id()
            when {
                !item.episodeId.isNullOrBlank() && item.seriesId >= 0 -> {
                    progressDao.upsert(
                        EpisodeProgressEntity(
                            profileId = pid,
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
                            profileId = pid,
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
                        movieProgressDao.delete(pid, item.movieId)
                    } else {
                        movieProgressDao.upsert(
                            MovieProgressEntity(
                                profileId = pid,
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

@Composable
fun PlayerScreen(
    args: PlayerArgs,
    onClose: () -> Unit,
    vm: PlayerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val holder = LocalPlaybackHolder.current
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }
    var trackPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(args) {
        vm.load(args)
        // Coming back from a minimized state with the same args: reuse playback as-is.
        // Otherwise this is a brand-new playback and the player should restart.
        val reuse = holder?.args?.value == args && holder.player != null
        if (!reuse) {
            // New media: explicitly stop the previous one so prepare below picks up cleanly.
            holder?.player?.stop()
        }
        holder?.args?.value = args
        holder?.minimized?.value = false
    }

    // Enable PiP for this screen, restore previous state on dispose.
    DisposableEffect(Unit) {
        val activity = context as? com.iptv.app.MainActivity
        val previous = activity?.pipEnabled
        activity?.pipEnabled = true
        onDispose { activity?.pipEnabled = previous ?: false }
    }

    val exo = remember(holder) {
        holder?.ensurePlayer(context)?.apply {
            repeatMode = Player.REPEAT_MODE_OFF
        } ?: ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    // Avoid re-preparing when returning from a minimized session: we'd lose position.
    var preparedFor by remember { mutableStateOf<List<PlayableItem>?>(null) }
    LaunchedEffect(state.items) {
        if (state.items.isEmpty()) return@LaunchedEffect
        if (preparedFor == state.items) return@LaunchedEffect
        val mediaItems = state.items.map { MediaItem.fromUri(it.url) }
        exo.setMediaItems(mediaItems, state.currentIndex, state.items.getOrNull(state.currentIndex)?.startPositionMs ?: 0L)
        exo.prepare()
        exo.playWhenReady = true
        preparedFor = state.items
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
                // Auto-advance from one episode to the next happens for free
                // because all episodes are in the same playlist. Force resume
                // in case the user had paused the previous item — they pressed
                // play once, they expect the queue to keep going.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    exo.playWhenReady = true
                }
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
            // Don't release here: the holder owns the player and the mini-player
            // may still need it. Release happens only when the user dismisses the
            // mini-player or the activity is destroyed.
            if (holder == null) {
                exo.release()
            }
        }
    }

    // The mini-player overlay can't be reached with a D-pad, so on TV/Tablet
    // back-from-player should fully release playback instead of leaving an
    // unreachable strip (with audio still playing) at the bottom of the menu.
    val backFormFactor = com.iptv.app.ui.common.rememberTvDim().formFactor
    val keepAliveOnBack = backFormFactor == com.iptv.app.ui.common.FormFactor.Phone

    val onMinimize = {
        if (keepAliveOnBack && holder != null && state.items.isNotEmpty()) {
            holder.minimized.value = true
        } else {
            holder?.release()
        }
        onClose()
    }
    BackHandler { onMinimize() }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty()) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    val dim = com.iptv.app.ui.common.rememberTvDim()
    val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
    // Mirrors the ExoPlayer chrome visibility so our own overlay (Back +
    // Tracks) appears/disappears together with the playback controls instead
    // of always sitting on top of the video.
    var controlsVisible by remember { mutableStateOf(true) }
    // Title overlay fades out 5s after the player opens (and again 5s after
    // controls reappear). The back button stays with the controls, but the
    // title shouldn't linger over the picture.
    var titleVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, state.title) {
        if (controlsVisible) {
            titleVisible = true
            kotlinx.coroutines.delay(5000)
            titleVisible = false
        }
    }
    // True when the user has the focus on one of the overlay buttons (Back,
    // Tracks). Without this, the root `onPreviewKeyEvent` would swallow the OK
    // press and toggle play/pause instead of letting the focused button fire.
    var overlayHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) overlayHasFocus = false
    }
    // ExoPlayer's built-in timeout doesn't fire while the player is paused or
    // buffering, so the chrome can linger forever in those states. Force-hide
    // 5s after each appearance unless the user is interacting via the overlay.
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    LaunchedEffect(controlsVisible, overlayHasFocus) {
        if (controlsVisible && !overlayHasFocus) {
            kotlinx.coroutines.delay(5000)
            playerViewRef.value?.hideController()
        }
    }
    val playerView = remember(isPhone) {
        PlayerView(context).also { playerViewRef.value = it }.apply {
            player = exo
            useController = true
            controllerAutoShow = true
            // Phones expect tap to toggle controls; TV keeps them visible until D-pad fades them.
            controllerHideOnTouch = isPhone
            controllerShowTimeoutMs = if (isPhone) 3000 else 5000
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            isFocusable = true
            isFocusableInTouchMode = true
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    controlsVisible = visibility == android.view.View.VISIBLE
                }
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val seekStepMs = 10_000L
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { evt ->
                if (evt.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Intercept Back before the underlying PlayerView's controller
                // can steal it (otherwise the first Back just dismisses the
                // controller chrome and the user has to press Back twice).
                if (evt.key == Key.Back || evt.key == Key.Escape ||
                    evt.key.nativeKeyCode == KeyEvent.KEYCODE_BACK) {
                    onMinimize()
                    return@onPreviewKeyEvent true
                }
                when (evt.key) {
                    Key.DirectionLeft, Key.MediaRewind -> {
                        playerView.showController()
                        exo.seekTo((exo.currentPosition - seekStepMs).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        playerView.showController()
                        val target = exo.currentPosition + seekStepMs
                        val dur = exo.duration
                        exo.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                        if (overlayHasFocus && evt.key != Key.MediaPlayPause) {
                            // Let the focused overlay button (Back, Tracks)
                            // handle the OK press instead of toggling playback.
                            false
                        } else {
                            playerView.showController()
                            if (exo.isPlaying) exo.pause() else exo.play()
                            true
                        }
                    }
                    else -> when (evt.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            playerView.showController()
                            exo.seekTo((exo.currentPosition - seekStepMs).coerceAtLeast(0L))
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            playerView.showController()
                            val target = exo.currentPosition + seekStepMs
                            val dur = exo.duration
                            exo.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { playerView }
        )
        if (isPhone) {
            // Double-tap left/right thirds to seek ±10s like YouTube/Netflix mobile players.
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    exo.seekTo((exo.currentPosition - seekStepMs).coerceAtLeast(0L))
                                    playerView.showController()
                                }
                            )
                        }
                )
                Box(modifier = Modifier.weight(1f).fillMaxSize())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    val target = exo.currentPosition + seekStepMs
                                    val dur = exo.duration
                                    exo.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                                    playerView.showController()
                                }
                            )
                        }
                )
            }
        }
        if (controlsVisible) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .onFocusChanged { overlayHasFocus = it.hasFocus },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                // Goes through onMinimize() so a back press here still surfaces
                // the mini-player rather than killing playback.
                com.iptv.app.ui.common.TouchableButton(onClick = onMinimize) {
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.back),
                        tint = Color.White
                    )
                }
                if (titleVisible) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            }
        }
        if (currentTracks != null && controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .onFocusChanged { if (it.hasFocus) overlayHasFocus = true }
            ) {
                TouchableButton(onClick = { trackPickerOpen = true }) {
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
                    TouchableButton(onClick = {
                        // Playback error: don't keep the broken stream alive in the mini-player.
                        holder?.release()
                        onClose()
                    }) { Text(androidx.compose.ui.res.stringResource(com.iptv.app.R.string.back)) }
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
