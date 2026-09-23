@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Tracks
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.cache.toDomain
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
import com.iptv.app.domain.sort.sorted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val items: List<PlayableItem> = emptyList(),
    val currentIndex: Int = 0,
    val countdownSeconds: Int = 0,
    val title: String = "",
    /** True quando o conteúdo atual é um canal Ao Vivo (habilita zapping). */
    val isLive: Boolean = false,
    /** Canais irmãos (mesma categoria) para zapping esquerda/direita. */
    val liveSiblings: List<LiveNav> = emptyList(),
    /** Índice do canal atual em [liveSiblings], ou -1 se desconhecido. */
    val liveIndex: Int = -1,
    /** streamId do canal Ao Vivo atual (para override de formato), ou -1. */
    val liveStreamId: Int = -1,
    /** Filmes irmãos (mesma categoria/filtro) para o botão "Próximo". */
    val movieSiblings: List<PlayerArgs> = emptyList(),
    /** Índice do filme atual em [movieSiblings], ou -1 se desconhecido. */
    val movieIndex: Int = -1
)

/** Item mínimo para navegação entre canais Ao Vivo via D-pad. */
data class LiveNav(val streamId: Int, val name: String)

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
    private val liveHistoryDao: com.iptv.app.data.db.LiveHistoryDao,
    private val categoryCache: com.iptv.app.data.db.CategoryCacheDao,
    private val seriesCache: com.iptv.app.data.db.SeriesCacheDao,
    private val settingsStore: com.iptv.app.data.prefs.SettingsStore,
    private val currentProfile: CurrentProfile,
    private val movieQueue: MovieQueue
) : ViewModel() {

    /** Configurações observáveis pelo player (decoder, legenda, áudio-only…). */
    val settings = settingsStore.flow.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        com.iptv.app.data.prefs.AppSettings()
    )

    /** True quando a categoria é marcada como adulta — conteúdo protegido não
     *  deve entrar nos históricos (canais recentes / continuar assistindo). */
    private suspend fun isAdultCategory(
        type: com.iptv.app.domain.model.ContentType,
        categoryId: String?
    ): Boolean {
        if (categoryId.isNullOrBlank()) return false
        return categoryCache.isAdult(type, categoryId) == true
    }
    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    fun load(args: PlayerArgs) {
        viewModelScope.launch {
            when (args.kind) {
                PlayerKind.LIVE -> {
                    val cached = liveCache.getById(args.streamId)
                    // Timeshift (tv_archive) é um pedido específico — não entra no
                    // fluxo de zapping nem carrega os canais irmãos.
                    if (args.timeshiftStartMs > 0L) {
                        val url = repo.timeshiftUrl(
                            streamId = args.streamId,
                            startMs = args.timeshiftStartMs,
                            durationMin = args.timeshiftDurationMin
                        )
                        _state.value = PlayerUiState(
                            items = listOf(PlayableItem(url, args.title)),
                            title = args.title
                        )
                        return@launch
                    }
                    // Canais irmãos da mesma categoria, na mesma ordem da lista
                    // (ordenação escolhida pelo usuário), para o zapping com
                    // esquerda/direita seguir a numeração que ele vê na tela.
                    val liveSort = settingsStore.flow.first().liveSort
                    val siblings = cached?.categoryId
                        ?.let { catId -> liveCache.getByCategory(catId) }
                        ?.map { it.toDomain() }
                        ?.sorted(liveSort)
                        ?.map { LiveNav(it.id, it.name) }
                        ?: emptyList()
                    val index = siblings.indexOfFirst { it.streamId == args.streamId }
                    playLive(args.streamId, args.title, siblings, index)
                }
                PlayerKind.MOVIE -> {
                    // Fila publicada pela tela de filmes (mesma ordem da lista).
                    // Se o filme atual não estiver nela (veio da busca, favoritos,
                    // continuar assistindo…) o índice fica -1 e o botão Próximo
                    // não aparece.
                    val siblings = movieQueue.items
                    val index = siblings.indexOfFirst { it.streamId == args.streamId }
                    playMovie(args, siblings, index)
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

    /** Constrói a URL do canal, atualiza o estado e registra no histórico.
     *  Usado tanto no load inicial quanto no zapping. */
    private suspend fun playLive(
        streamId: Int,
        title: String,
        siblings: List<LiveNav>,
        liveIndex: Int
    ) {
        val cached = liveCache.getById(streamId)
        // Formato efetivo: override do canal tem prioridade sobre o padrão.
        val s = settingsStore.flow.first()
        val hls = (s.streamFormatOverrides[streamId] ?: s.streamFormat) ==
            com.iptv.app.data.prefs.StreamFormat.HLS
        // Canal importado via M3U traz a URL direta; senão monta a do Xtream.
        val url = cached?.streamUrl ?: repo.liveStreamUrl(streamId, hls = hls)
        _state.value = PlayerUiState(
            items = listOf(PlayableItem(url, title)),
            title = title,
            isLive = true,
            liveSiblings = siblings,
            liveIndex = liveIndex,
            liveStreamId = streamId
        )
        // Histórico: marca este canal como o mais recente assistido, para
        // alimentar a seção "Canais recentes" na Início. trim() logo depois
        // pra manter no máximo 10 — sem isso zapping pesado deixaria a tabela
        // com centenas de linhas. Conteúdo adulto/protegido NÃO entra no
        // histórico (a pedido do usuário).
        if (!isAdultCategory(com.iptv.app.domain.model.ContentType.LIVE, cached?.categoryId)) {
            runCatching {
                val pid = currentProfile.id()
                liveHistoryDao.upsert(
                    com.iptv.app.data.db.LiveHistoryEntity(
                        profileId = pid,
                        channelId = streamId,
                        name = title,
                        logoUrl = cached?.logoUrl,
                        categoryId = cached?.categoryId
                    )
                )
                liveHistoryDao.trim(pid, keep = 10)
            }
        }
    }

    /** Avança (+1) ou volta (-1) um canal dentro da categoria atual, com
     *  wrap-around nas pontas. No-op se não houver canais irmãos. */
    fun zapLive(delta: Int) {
        val s = _state.value
        if (!s.isLive || s.liveSiblings.size < 2 || s.liveIndex < 0) return
        val newIndex = (s.liveIndex + delta).mod(s.liveSiblings.size)
        val target = s.liveSiblings[newIndex]
        viewModelScope.launch {
            playLive(target.streamId, target.name, s.liveSiblings, newIndex)
        }
    }

    /** Monta o item de filme (URL + posição de retomada) e publica no estado,
     *  preservando a fila de irmãos para o botão Próximo. Usado no load inicial
     *  e ao saltar entre filmes. */
    private suspend fun playMovie(args: PlayerArgs, siblings: List<PlayerArgs>, index: Int) {
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
            title = args.title,
            movieSiblings = siblings,
            movieIndex = index
        )
    }

    /** Salta para o filme seguinte (+1) ou anterior (-1) da fila atual. No-op nas
     *  pontas (sem wrap) e quando não há fila. */
    fun stepMovie(delta: Int) {
        val s = _state.value
        if (s.movieIndex < 0 || s.movieSiblings.isEmpty()) return
        val target = s.movieSiblings.getOrNull(s.movieIndex + delta) ?: return
        viewModelScope.launch { playMovie(target, s.movieSiblings, s.movieIndex + delta) }
    }

    /** Troca o formato (HLS/TS) do canal Ao Vivo atual, salva como override e
     *  recarrega a reprodução com a nova URL. */
    fun setLiveFormat(format: com.iptv.app.data.prefs.StreamFormat) {
        val s = _state.value
        val id = s.liveStreamId
        if (!s.isLive || id < 0) return
        viewModelScope.launch {
            settingsStore.setChannelStreamFormat(id, format)
            playLive(id, s.title, s.liveSiblings, s.liveIndex)
        }
    }

    fun setAudioOnly(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAudioOnly(enabled) }
    }

    fun setSubtitleScale(percent: Int) {
        viewModelScope.launch { settingsStore.setSubtitleScalePercent(percent) }
    }

    fun setSubtitleStyle(style: com.iptv.app.data.prefs.SubtitleStyle) {
        viewModelScope.launch { settingsStore.setSubtitleStyle(style) }
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
                    // Série em categoria adulta não entra no "Continuar assistindo".
                    val seriesCat = seriesCache.categoryIdOf(item.seriesId)
                    if (isAdultCategory(com.iptv.app.domain.model.ContentType.SERIES, seriesCat)) {
                        return@launch
                    }
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
                    // Filme em categoria adulta não entra no "Continuar assistindo".
                    if (isAdultCategory(com.iptv.app.domain.model.ContentType.MOVIE, item.movieCategory)) {
                        return@launch
                    }
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
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val holder = LocalPlaybackHolder.current
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryAttempts by remember { mutableStateOf(0) }
    var isReconnecting by remember { mutableStateOf(false) }
    // Buffering inicial: enquanto o ExoPlayer não saiu de STATE_BUFFERING
    // pela primeira vez. Usado pra mostrar a animação TartaTV no overlay.
    // Reset a cada novo `args` (mídia diferente) e a cada novo `state.items`
    // — entrar num novo filme/canal sempre mostra o splash até o player
    // chegar em STATE_READY.
    var isInitialBuffering by remember(args) { mutableStateOf(true) }
    // Guard contra STATE_ENDED reemitido em sequência (alguns drivers).
    var lastEndedIndex by remember { mutableStateOf(-1) }
    // Capturado aqui (composable) porque o listener do ExoPlayer abaixo não
    // pode chamar stringResource — só é lido depois de esgotar as tentativas
    // de reconexão do STATE_ENDED sem próximo item (ver onPlaybackStateChanged).
    val liveStreamEndedMsg = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_live_stream_ended)
    // Faixas (áudio/legenda/vídeo) do item atual — alimentam o seletor de
    // áudio aberto pelo painel de opções (engrenagem).
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var showTracks by remember { mutableStateOf(false) }

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

    // Ao saltar de filme (botão Próximo), o `args` da navegação não muda — a
    // troca é dirigida pelo `state`. Mantém o mini-player apontando para o filme
    // certo e reexibe o splash de conexão do novo filme.
    LaunchedEffect(state.movieIndex) {
        if (state.movieIndex >= 0) {
            state.movieSiblings.getOrNull(state.movieIndex)?.let { holder?.args?.value = it }
            isInitialBuffering = true
        }
    }

    // Enable PiP for this screen, restore previous state on dispose.
    DisposableEffect(Unit) {
        val activity = context as? com.iptv.app.MainActivity
        val previous = activity?.pipEnabled
        activity?.pipEnabled = true
        onDispose { activity?.pipEnabled = previous ?: false }
    }

    // Decoder escolhido nas Configurações — aplicado na construção do player.
    // Se o modo mudar com um player já ativo no holder, vale no próximo cold start.
    val decoderMode = settings.decoderMode
    val exo = remember(holder, decoderMode) {
        val factory = buildRenderersFactory(context, decoderMode)
        holder?.ensurePlayer(context, factory, decoderMode.name)?.apply {
            repeatMode = Player.REPEAT_MODE_OFF
        } ?: newStreamingPlayer(context, factory).apply { playWhenReady = true }
    }
    // Reconexão do Ao Vivo: até LIVE_MAX_RETRIES tentativas com intervalo
    // crescente (1s, 2s, 3s, 3s…). Antes eram 3 tentativas imediatas, que se
    // esgotavam em 1-2s num servidor momentaneamente lento; e como o
    // prepare() era chamado sem voltar à borda ao vivo, uma queda longa caía
    // em BehindLiveWindow de novo e o overlay parecia travado.
    val retryScope = androidx.compose.runtime.rememberCoroutineScope()
    val retryJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val reconnectLive: (String) -> Unit = remember(exo) {
        { failureMessage ->
            retryJob[0]?.cancel()
            if (retryAttempts < LIVE_MAX_RETRIES) {
                retryAttempts++
                isReconnecting = true
                val waitMs = retryAttempts.coerceAtMost(3) * 1_000L
                retryJob[0] = retryScope.launch {
                    kotlinx.coroutines.delay(waitMs)
                    // prepare() só age em IDLE — o vigia de travamento chama
                    // isto com o player ainda em BUFFERING.
                    if (exo.playbackState != Player.STATE_IDLE) exo.stop()
                    if (exo.isCurrentMediaItemLive) exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.playWhenReady = true
                }
            } else {
                isReconnecting = false
                playbackError = failureMessage
            }
        }
    }
    // Modo rádio / áudio-only: desliga o renderer de vídeo (economiza banda e
    // CPU em canais de rádio). Reativo — o toggle aplica na hora.
    LaunchedEffect(exo, settings.audioOnly) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, settings.audioOnly)
            .build()
    }
    // Manter a CPU/Wi-Fi acordados durante streaming — sem isso, o sistema
    // adormece após ~10min com tela ativa e o playback para.
    DisposableEffect(exo) {
        exo.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
        onDispose { /* holder libera o player no dispose do Activity */ }
    }
    // Mantém a TELA ligada enquanto o player está em primeiro plano —
    // o launcher da TV ativa screensaver/standby após X minutos de
    // "inatividade" mesmo com o vídeo rodando, porque não há input.
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Avoid re-preparing when returning from a minimized session: we'd lose position.
    var preparedFor by remember { mutableStateOf<List<PlayableItem>?>(null) }
    LaunchedEffect(state.items) {
        if (state.items.isEmpty()) return@LaunchedEffect
        if (preparedFor == state.items) return@LaunchedEffect
        // Mídia nova (inclui zapping): orçamento de reconexão zerado e overlay
        // de conexão com cronômetro até o primeiro frame.
        if (preparedFor != null) {
            retryJob[0]?.cancel()
            retryAttempts = 0
            isReconnecting = false
            playbackError = null
            isInitialBuffering = true
        }
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

    // Banner "próximo episódio em Xs" — só faz sentido para séries (há um
    // próximo item na playlist). Em filme/canal, o segundo not-null nunca
    // dispara o countdown porque hasNextMediaItem=false.
    var nextUpRemaining by remember { mutableStateOf(0) }
    LaunchedEffect(state.items) {
        if (state.items.isEmpty()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(500L)
            val dur = exo.duration
            val pos = exo.currentPosition
            if (dur <= 0L || !exo.hasNextMediaItem()) {
                nextUpRemaining = 0
                continue
            }
            val remainingMs = dur - pos
            nextUpRemaining = if (remainingMs in 1..10_000L) {
                ((remainingMs + 999L) / 1000L).toInt().coerceIn(1, 10)
            } else 0
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

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(error: PlaybackException) {
                // Para canais Live: reconecta (até LIVE_MAX_RETRIES) antes de
                // mostrar erro definitivo. Antes qualquer falha imediata
                // (ex.: lentidão do provider) mostrava "Não foi possível
                // reproduzir" sem o usuário ter chance de reconectar.
                val isLive = state.items.getOrNull(exo.currentMediaItemIndex)
                    ?.let { it.episodeId == null && it.movieId < 0 } ?: false
                if (isLive) {
                    reconnectLive(friendlyPlaybackError(error))
                } else {
                    isReconnecting = false
                    playbackError = friendlyPlaybackError(error)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Alguns streams Xtream (HLS sem #EXT-X-ENDLIST) chegam em
                // STATE_ENDED sem o ExoPlayer disparar a transição automática
                // para o próximo item — episódios ficam parados na tela
                // preta. Força o salto manualmente quando há próximo na
                // playlist.
                //
                // Guard contra duplo skip: alguns drivers podem reemitir
                // STATE_ENDED antes do seek concluir. Marca o índice no qual
                // já tratamos o ENDED e ignora reemissões para o mesmo item.
                if (playbackState == Player.STATE_ENDED && exo.hasNextMediaItem()) {
                    val idx = exo.currentMediaItemIndex
                    if (idx != lastEndedIndex) {
                        lastEndedIndex = idx
                        exo.seekToNextMediaItem()
                        exo.playWhenReady = true
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    // Ao Vivo não tem "próximo item" — sem este tratamento, um
                    // manifesto HLS que momentaneamente parece "terminado"
                    // (hiccup do encoder do provedor, atraso no refresh do
                    // #EXT-X-ENDLIST) deixava o canal congelado/pausado
                    // sozinho, sem nenhum erro disparado e sem chance de
                    // reconectar. Mesmo fluxo de retry do onPlayerError.
                    val isLive = state.items.getOrNull(exo.currentMediaItemIndex)
                        ?.let { it.episodeId == null && it.movieId < 0 } ?: false
                    if (isLive) reconnectLive(liveStreamEndedMsg)
                }
                if (playbackState == Player.STATE_READY) {
                    // Conectou: zera o contador, tira overlay de reconexão e
                    // libera o guard de STATE_ENDED para o próximo item.
                    retryAttempts = 0
                    isReconnecting = false
                    isInitialBuffering = false
                    playbackError = null
                    lastEndedIndex = -1
                }
            }

        }
        exo.addListener(listener)
        tracks = exo.currentTracks
        onDispose {
            retryJob[0]?.cancel()
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

    // Vigia de travamento (Ao Vivo): carregando por LIVE_STALL_TIMEOUT_S sem
    // chegar nenhum dado novo = conexão presa. Trata como queda e reconecta —
    // antes o overlay podia ficar parado indefinidamente sem nenhum erro para
    // disparar a reconexão.
    LaunchedEffect(exo, state.isLive) {
        if (!state.isLive) return@LaunchedEffect
        var lastBuffered = Long.MIN_VALUE
        var stalledSec = 0
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            val waiting = exo.playbackState == Player.STATE_BUFFERING &&
                exo.playWhenReady && playbackError == null
            val buffered = exo.bufferedPosition
            if (!waiting || buffered != lastBuffered) {
                lastBuffered = buffered
                stalledSec = 0
                continue
            }
            if (++stalledSec >= LIVE_STALL_TIMEOUT_S) {
                stalledSec = 0
                lastBuffered = Long.MIN_VALUE
                reconnectLive(liveStreamEndedMsg)
            }
        }
    }

    // Cronômetro do carregamento: segundos desde que o canal começou a
    // conectar (ou reconectar) sem ter mostrado vídeo ainda. Continua contando
    // na passagem de "conectando" para "reconectando".
    val showingLoader = (isInitialBuffering || isReconnecting) && playbackError == null
    var loadingElapsedSec by remember { mutableStateOf(0L) }
    LaunchedEffect(showingLoader) {
        if (!showingLoader) return@LaunchedEffect
        val startedAt = android.os.SystemClock.elapsedRealtime()
        loadingElapsedSec = 0L
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            loadingElapsedSec = (android.os.SystemClock.elapsedRealtime() - startedAt) / 1_000L
        }
    }

    // TV desligada / app em segundo plano. Antes o player só era pausado e, ao
    // religar a TV, a tela voltava congelada no último quadro: o canal ao vivo
    // tinha ficado para trás da janela do servidor e nada retomava sozinho.
    // Agora o Ao Vivo solta a conexão ao parar e volta sintonizado na borda
    // ao vivo; filme/episódio salva a posição e retoma de onde estava.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(exo, lifecycleOwner) {
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            val activity = context as? android.app.Activity
            val inPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                activity?.isInPictureInPictureMode == true
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!inPip) {
                    resumeOnStart = exo.playWhenReady || isInitialBuffering || isReconnecting
                    retryJob[0]?.cancel()
                    state.items.getOrNull(exo.currentMediaItemIndex)?.let {
                        vm.saveProgress(it, exo.currentPosition, exo.duration.coerceAtLeast(0L))
                    }
                    if (state.isLive) exo.stop() else exo.pause()
                }
                Lifecycle.Event.ON_START -> if (resumeOnStart && exo.mediaItemCount > 0) {
                    resumeOnStart = false
                    if (state.isLive) {
                        retryAttempts = 0
                        isReconnecting = false
                        playbackError = null
                        isInitialBuffering = true
                        if (exo.playbackState != Player.STATE_IDLE) exo.stop()
                        exo.seekToDefaultPosition()
                        exo.prepare()
                    } else if (exo.playbackState == Player.STATE_IDLE) {
                        exo.prepare()
                    }
                    exo.playWhenReady = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    // True when the user has the focus on the Back overlay button. Without
    // this, the root `onPreviewKeyEvent` would swallow the OK press and toggle
    // play/pause instead of letting the focused button fire.
    var overlayHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) overlayHasFocus = false
    }
    // Visibilidade independente do botão Voltar: 5s desde a última interação.
    // Antes ele acompanhava o controller do PlayerView que, em alguns casos,
    // ficava visível indefinidamente.
    var backVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    // ↑/↓ no vídeo levam o foco para o 1º botão da barra (Voltar). Antes o
    // foco ficava sempre no vídeo e não havia "ponteiro" visível para chegar
    // em Voltar/Opções/Próximo — só um ↓ acidental às vezes entrava na barra.
    val overlayFirstButton = remember { FocusRequester() }
    var focusOverlayRequest by remember { mutableStateOf(0) }
    LaunchedEffect(focusOverlayRequest) {
        if (focusOverlayRequest == 0) return@LaunchedEffect
        backVisible = true
        // Espera a barra compor antes de pedir o foco.
        kotlinx.coroutines.delay(50)
        runCatching { overlayFirstButton.requestFocus() }
    }
    LaunchedEffect(interactionTick) {
        backVisible = true
        kotlinx.coroutines.delay(5000)
        backVisible = false
        if (overlayHasFocus) {
            overlayHasFocus = false
            runCatching { focusRequester.requestFocus() }
        }
    }
    // ExoPlayer's built-in timeout doesn't fire while the player is paused or
    // buffering, and didn't fire either when the focus parked on one of the
    // overlay buttons. Force-hide after a longer idle so Voltar/Faixas don't
    // linger forever. When the user is interacting with the overlay we wait
    // longer (8s vs 5s), but we still eventually hide and steal focus back to
    // the root so the buttons disappear.
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    LaunchedEffect(controlsVisible, overlayHasFocus) {
        if (controlsVisible) {
            val timeoutMs = if (overlayHasFocus) 8000L else 5000L
            kotlinx.coroutines.delay(timeoutMs)
            playerViewRef.value?.hideController()
            if (overlayHasFocus) {
                overlayHasFocus = false
                runCatching { focusRequester.requestFocus() }
            }
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
            // Esses botões de pular ±5s do controller nativo nunca surtiam
            // efeito em filmes (provavelmente porque o ExoPlayer espera
            // `setSeekParameters`/MediaItem com duração estável). O seek por
            // D-pad esquerda/direita já cobre o caso — escondemos os ícones.
            setShowRewindButton(false)
            setShowFastForwardButton(false)
            // Esconder controles nativos que o usuário não consegue focar
            // pelo D-pad — o Box exterior captura o OK antes do PlayerView
            // receber, então engrenagem/subtitle/multi-window ficam
            // inacessíveis. Removemos pra não confundir.
            setShowSubtitleButton(false)
            setShowMultiWindowTimeBar(false)
            setShowVrButton(false)
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            // Engrenagem (settings / playback speed) do controller nativo
            // não é alcançável via D-pad porque o Box exterior captura o OK
            // antes — esconder o view diretamente após o inflate.
            post {
                runCatching {
                    findViewById<android.view.View?>(
                        androidx.media3.ui.R.id.exo_settings
                    )?.visibility = android.view.View.GONE
                }
            }
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

    // --- Controles avançados (aspect ratio, velocidade, sleep timer, opções) ---
    var showOptions by remember { mutableStateOf(false) }
    val resizeModes = remember {
        listOf(
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        )
    }
    var resizeModeIndex by rememberSaveable { mutableStateOf(0) }
    val speeds = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }
    var speedIndex by rememberSaveable { mutableStateOf(2) }
    var sleepMinutes by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(resizeModeIndex, playerView) {
        playerView.resizeMode = resizeModes[resizeModeIndex]
    }
    LaunchedEffect(speedIndex, exo) {
        exo.setPlaybackSpeed(speeds[speedIndex])
    }
    LaunchedEffect(settings.subtitleScalePercent, settings.subtitleStyle, playerView) {
        applySubtitleStyle(playerView, settings.subtitleScalePercent, settings.subtitleStyle)
    }
    // Sleep timer: pausa a reprodução após o tempo escolhido.
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            kotlinx.coroutines.delay(sleepMinutes * 60_000L)
            exo.pause()
            sleepMinutes = 0
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
                // Qualquer tecla reinicia o timer do botão Voltar.
                interactionTick++
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
                        // Quando um botão da overlay (Voltar/Faixas) está
                        // focado, esquerda/direita deve mover entre os botões,
                        // não fazer seek. Só as teclas físicas de mídia
                        // (MediaRewind/MediaFastForward) sempre fazem seek.
                        if (overlayHasFocus && evt.key == Key.DirectionLeft) {
                            false
                        } else if (state.isLive && evt.key == Key.DirectionLeft) {
                            // Ao Vivo: esquerda volta um canal (seek não faz
                            // sentido em stream contínuo).
                            vm.zapLive(-1)
                            true
                        } else {
                            playerView.showController()
                            exo.seekTo((exo.currentPosition - seekStepMs).coerceAtLeast(0L))
                            true
                        }
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (overlayHasFocus && evt.key == Key.DirectionRight) {
                            false
                        } else if (state.isLive && evt.key == Key.DirectionRight) {
                            // Ao Vivo: direita avança um canal.
                            vm.zapLive(1)
                            true
                        } else {
                            playerView.showController()
                            val target = exo.currentPosition + seekStepMs
                            val dur = exo.duration
                            exo.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                            true
                        }
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (overlayHasFocus) {
                            if (evt.key == Key.DirectionDown) {
                                // Descer da barra devolve o foco ao vídeo
                                // (OK volta a pausar/retomar).
                                runCatching { focusRequester.requestFocus() }
                                true
                            } else false
                        } else {
                            playerView.showController()
                            focusOverlayRequest++
                            true
                        }
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
                            if (overlayHasFocus) {
                                false
                            } else if (state.isLive) {
                                vm.zapLive(-1)
                                true
                            } else {
                                playerView.showController()
                                exo.seekTo((exo.currentPosition - seekStepMs).coerceAtLeast(0L))
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (overlayHasFocus) {
                                false
                            } else if (state.isLive) {
                                vm.zapLive(1)
                                true
                            } else {
                                playerView.showController()
                                val target = exo.currentPosition + seekStepMs
                                val dur = exo.duration
                                exo.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                                true
                            }
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
        if (backVisible) {
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
                com.iptv.app.ui.common.TouchableButton(
                    onClick = onMinimize,
                    modifier = Modifier.focusRequester(overlayFirstButton)
                ) {
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.back),
                        tint = Color.White
                    )
                }
                // Engrenagem: abre o painel de opções do player (aspect ratio,
                // velocidade, legendas, áudio-only, formato, player externo…).
                com.iptv.app.ui.common.TouchableButton(onClick = { showOptions = true }) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Tune,
                        contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_options),
                        tint = Color.White
                    )
                }
                // Próximo filme da categoria/filtro atual. Só aparece quando há
                // um próximo na fila publicada pela tela de filmes.
                val hasNextMovie = state.movieIndex in 0 until state.movieSiblings.lastIndex
                if (hasNextMovie) {
                    com.iptv.app.ui.common.TouchableButton(onClick = {
                        // Salva a posição do filme atual antes de trocar para
                        // que ele entre no "Continuar assistindo" corretamente.
                        state.items.getOrNull(exo.currentMediaItemIndex)?.let {
                            vm.saveProgress(it, exo.currentPosition, exo.duration.coerceAtLeast(0L))
                        }
                        vm.stepMovie(1)
                    }) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_next_movie),
                            tint = Color.White
                        )
                    }
                }
                if (titleVisible) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            }
        }
        if (showOptions) {
            PlayerOptionsDialog(
                state = state,
                settings = settings,
                resizeModeIndex = resizeModeIndex,
                onCycleResizeMode = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size },
                speed = speeds[speedIndex],
                onCycleSpeed = { speedIndex = (speedIndex + 1) % speeds.size },
                sleepMinutes = sleepMinutes,
                onCycleSleep = {
                    val opts = listOf(0, 15, 30, 60, 90)
                    sleepMinutes = opts[(opts.indexOf(sleepMinutes).coerceAtLeast(0) + 1) % opts.size]
                },
                onToggleAudioOnly = { vm.setAudioOnly(!settings.audioOnly) },
                onCycleSubtitleStyle = {
                    val all = com.iptv.app.data.prefs.SubtitleStyle.values()
                    vm.setSubtitleStyle(all[(all.indexOf(settings.subtitleStyle) + 1) % all.size])
                },
                onSubtitleScale = { vm.setSubtitleScale(it) },
                onToggleLiveFormat = {
                    val current = settings.streamFormatOverrides[state.liveStreamId] ?: settings.streamFormat
                    vm.setLiveFormat(
                        if (current == com.iptv.app.data.prefs.StreamFormat.HLS)
                            com.iptv.app.data.prefs.StreamFormat.TS
                        else com.iptv.app.data.prefs.StreamFormat.HLS
                    )
                },
                onOpenExternal = {
                    val url = state.items.getOrNull(exo.currentMediaItemIndex)?.url
                    if (url != null) launchExternalPlayer(context, url, state.title, settings.externalPlayerPackage)
                    showOptions = false
                },
                tracks = tracks,
                onOpenTracks = {
                    showOptions = false
                    showTracks = true
                },
                onDismiss = { showOptions = false }
            )
        }
        if (showTracks) {
            TrackPickerDialog(
                player = exo,
                tracks = tracks,
                onDismiss = { showTracks = false }
            )
        }
        // Banner pequeno no canto inferior direito durante os últimos 10s
        // antes da transição automática. O usuário pediu o aviso pra não
        // ser pego de surpresa quando um episódio acaba.
        if (nextUpRemaining > 0) {
            val nextIdx = exo.nextMediaItemIndex
            val nextItem = state.items.getOrNull(nextIdx)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(20.dp)
                    .background(
                        Color(0xCC000000),
                        androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.iptv.app.R.string.player_next_in,
                            nextUpRemaining
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    nextItem?.title?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
        // Overlay enquanto o vídeo está carregando pela primeira vez: mostra
        // a animação "TartaTV" + spinner. Antes usávamos `SplashScreen()`
        // que é fillMaxSize com background opaco — o overlay cobria a tela
        // toda e o spinner ficava fora dela. Agora `WaveBrand()` é só o
        // wordmark embutível dentro do Column do overlay.
        if (isInitialBuffering && !isReconnecting && playbackError == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.iptv.app.ui.common.WaveBrand()
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    // Mensagem explícita: alguns canais demoram vários segundos
                    // pra conectar e o usuário ficava encarando tela preta
                    // achando que o app travou.
                    Text(
                        androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_connecting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    // Nome do canal: no zapping o overlay também aparece e o
                    // usuário precisa saber para onde está indo.
                    if (state.title.isNotBlank()) {
                        Text(
                            state.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp)
                        )
                    }
                    LoadingStopwatch(loadingElapsedSec)
                }
            }
        }
        // Modo rádio / áudio-only: sem vídeo, mostra a marca + título no centro
        // pra tela não ficar preta. Só quando já conectou (não sobrepõe o splash).
        if (settings.audioOnly && !isInitialBuffering && playbackError == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Radio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(state.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_audio_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        if (isReconnecting) {
            // Overlay de reconexão: tentativa atual/máximo + cronômetro. Sem
            // ele o usuário ficava encarando tela preta sem saber se o app
            // travou.
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.iptv.app.R.string.player_reconnecting,
                            retryAttempts,
                            LIVE_MAX_RETRIES
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    LoadingStopwatch(loadingElapsedSec)
                }
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
                        androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_cannot_play_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    // Live: mensagem extra orientando o usuário a contatar o
                    // dono da lista — depois de 3 falhas geralmente é o
                    // provider, não o app.
                    val isLive = state.items.getOrNull(exo.currentMediaItemIndex)
                        ?.let { it.episodeId == null && it.movieId < 0 } ?: false
                    if (isLive) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_contact_provider),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        TouchableButton(onClick = {
                            // Tentar de novo manualmente: reseta contador e
                            // tenta preparar o player novamente (na borda ao
                            // vivo, se for canal).
                            retryAttempts = 0
                            playbackError = null
                            isReconnecting = false
                            isInitialBuffering = true
                            if (exo.playbackState != Player.STATE_IDLE) exo.stop()
                            if (exo.isCurrentMediaItemLive) exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.playWhenReady = true
                        }) { Text(androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_retry)) }
                        TouchableButton(onClick = {
                            holder?.release()
                            onClose()
                        }) { Text(androidx.compose.ui.res.stringResource(com.iptv.app.R.string.back)) }
                    }
                }
            }
        }
    }
}

/** Tentativas de reconexão automática de um canal ao vivo antes do erro. */
private const val LIVE_MAX_RETRIES = 10

/** Segundos "carregando" sem chegar dado novo até considerar a conexão presa. */
private const val LIVE_STALL_TIMEOUT_S = 20

/** Cronômetro mm:ss exibido enquanto o canal conecta/reconecta. */
@Composable
private fun LoadingStopwatch(elapsedSec: Long) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.Timer,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            String.format(java.util.Locale.ROOT, "%02d:%02d", elapsedSec / 60, elapsedSec % 60),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 6.dp)
        )
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

/** Aplica tamanho + estilo de legenda escolhidos nas Configurações ao PlayerView. */
private fun applySubtitleStyle(
    view: PlayerView,
    scalePercent: Int,
    style: com.iptv.app.data.prefs.SubtitleStyle
) {
    val sub = view.subtitleView ?: return
    sub.setFractionalTextSize(
        androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (scalePercent / 100f)
    )
    val white = android.graphics.Color.WHITE
    val transparent = android.graphics.Color.TRANSPARENT
    val black = android.graphics.Color.BLACK
    val caption = when (style) {
        com.iptv.app.data.prefs.SubtitleStyle.DEFAULT -> androidx.media3.ui.CaptionStyleCompat(
            white, transparent, transparent,
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, black, null
        )
        com.iptv.app.data.prefs.SubtitleStyle.WHITE_ON_BLACK -> androidx.media3.ui.CaptionStyleCompat(
            white, 0xCC000000.toInt(), transparent,
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE, black, null
        )
        com.iptv.app.data.prefs.SubtitleStyle.YELLOW -> androidx.media3.ui.CaptionStyleCompat(
            android.graphics.Color.YELLOW, transparent, transparent,
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, black, null
        )
        com.iptv.app.data.prefs.SubtitleStyle.OUTLINE -> androidx.media3.ui.CaptionStyleCompat(
            white, transparent, transparent,
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, black, null
        )
    }
    sub.setStyle(caption)
    sub.setApplyEmbeddedStyles(false)
}

/** Abre o stream atual num player externo (MX Player, VLC…). Se o pacote
 *  preferido não estiver instalado, cai para o seletor padrão do sistema. */
private fun launchExternalPlayer(
    context: android.content.Context,
    url: String,
    title: String,
    pkg: String?
) {
    fun baseIntent() = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(android.net.Uri.parse(url), "video/*")
        putExtra("title", title)
        // Extras reconhecidos por MX Player / VLC para herdar o título.
        putExtra("secure_uri", true)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val primary = baseIntent().apply { if (!pkg.isNullOrBlank()) setPackage(pkg) }
    val ok = runCatching { context.startActivity(primary); true }.getOrDefault(false)
    if (!ok) {
        runCatching { context.startActivity(baseIntent()) }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerOptionsDialog(
    state: PlayerUiState,
    settings: com.iptv.app.data.prefs.AppSettings,
    resizeModeIndex: Int,
    onCycleResizeMode: () -> Unit,
    speed: Float,
    onCycleSpeed: () -> Unit,
    sleepMinutes: Int,
    onCycleSleep: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onCycleSubtitleStyle: () -> Unit,
    onSubtitleScale: (Int) -> Unit,
    onToggleLiveFormat: () -> Unit,
    onOpenExternal: () -> Unit,
    tracks: Tracks,
    onOpenTracks: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_options),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                // Seleção de faixa de áudio (idioma) e legenda — abre o
                // seletor com todas as faixas do conteúdo atual.
                val offLabel = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.off_label)
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_audio),
                    selectedTrackLabel(tracks, androidx.media3.common.C.TRACK_TYPE_AUDIO) ?: "—",
                    onOpenTracks
                )
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_subtitle),
                    selectedTrackLabel(tracks, androidx.media3.common.C.TRACK_TYPE_TEXT) ?: offLabel,
                    onOpenTracks
                )
                val aspectLabel = when (resizeModeIndex) {
                    1 -> androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_aspect_zoom)
                    2 -> androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_aspect_fill)
                    else -> androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_aspect_fit)
                }
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_aspect),
                    aspectLabel, onCycleResizeMode
                )
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_speed),
                    "${speed}x", onCycleSpeed
                )
                val onTxt = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.on_label)
                val offTxt = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.off_label)
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_audio_only),
                    if (settings.audioOnly) onTxt else offTxt, onToggleAudioOnly
                )
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_subtitle_style),
                    settings.subtitleStyle.name, onCycleSubtitleStyle
                )
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_subtitle_size),
                    "${settings.subtitleScalePercent}%"
                ) {
                    val steps = listOf(75, 100, 125, 150, 175, 200)
                    val next = steps[(steps.indexOf(settings.subtitleScalePercent)
                        .coerceAtLeast(0) + 1) % steps.size]
                    onSubtitleScale(next)
                }
                if (state.isLive) {
                    val fmt = settings.streamFormatOverrides[state.liveStreamId] ?: settings.streamFormat
                    OptionRow(
                        androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_stream_format),
                        fmt.name, onToggleLiveFormat
                    )
                }
                val sleepLabel = if (sleepMinutes == 0) offTxt else "$sleepMinutes min"
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_sleep_timer),
                    sleepLabel, onCycleSleep
                )
                OptionRow(
                    androidx.compose.ui.res.stringResource(com.iptv.app.R.string.player_external),
                    "▶", onOpenExternal
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TouchableButton(onClick = onDismiss) {
                        Text(androidx.compose.ui.res.stringResource(com.iptv.app.R.string.ok))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun OptionRow(label: String, value: String, onClick: () -> Unit) {
    TouchableButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Box(modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
