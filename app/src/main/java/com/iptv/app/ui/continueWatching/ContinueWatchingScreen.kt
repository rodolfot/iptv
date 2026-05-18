package com.iptv.app.ui.continueWatching

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.MovieCacheEntity
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.data.db.SeriesCacheEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.data.reco.Recommender
import com.iptv.app.domain.model.toModel
import com.iptv.app.ui.common.EmptyState
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContinueWatchingViewModel @Inject constructor(
    private val movieProgressDao: MovieProgressDao,
    private val seriesProgressDao: SeriesProgressDao,
    private val episodeProgressDao: EpisodeProgressDao,
    private val liveHistoryDao: com.iptv.app.data.db.LiveHistoryDao,
    private val currentProfile: CurrentProfile,
    private val recommender: Recommender,
    private val xtream: com.iptv.app.data.api.XtreamRepository,
    settings: SettingsStore
) : ViewModel() {
    private val profileIdFlow = settings.flow
        .map { currentProfile.id() }
        .distinctUntilChanged()

    val movies = profileIdFlow
        .flatMapLatest { movieProgressDao.observeInProgress(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val series = profileIdFlow
        .flatMapLatest { seriesProgressDao.observeRecent(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentChannels = profileIdFlow
        .flatMapLatest { liveHistoryDao.observeRecent(it, limit = 10) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _movieReco = kotlinx.coroutines.flow.MutableStateFlow<Recommender.MovieRow?>(null)
    val movieReco = _movieReco.asStateFlow()
    private val _seriesReco = kotlinx.coroutines.flow.MutableStateFlow<Recommender.SeriesRow?>(null)
    val seriesReco = _seriesReco.asStateFlow()

    fun refreshRecommendations() {
        viewModelScope.launch {
            _movieReco.value = recommender.moviesFor()
            _seriesReco.value = recommender.seriesFor()
        }
    }

    suspend fun episodePercent(episodeId: String): Int {
        val ep = episodeProgressDao.getById(currentProfile.id(), episodeId) ?: return 0
        if (ep.durationMs <= 0L || ep.watched) return 0
        return ((ep.positionMs * 100L) / ep.durationMs).toInt().coerceIn(0, 100)
    }

    /**
     * Decide qual episódio tocar a partir do card "Continuar séries":
     *  - se o último visto NÃO foi 100% concluído → continua nele;
     *  - se foi concluído → busca o próximo episódio da série (próximo
     *    número na mesma temporada; se acabou, primeiro da próxima);
     *  - fallback: o próprio último episódio.
     */
    suspend fun resolveNextEpisode(item: SeriesProgressEntity): PlayerArgs {
        val pid = currentProfile.id()
        val last = episodeProgressDao.getById(pid, item.lastEpisodeId)
        val needsNext = last?.watched == true
        if (!needsNext) {
            return PlayerArgs(
                kind = PlayerKind.EPISODE,
                streamId = item.lastEpisodeId.toIntOrNull() ?: 0,
                title = item.title,
                containerExtension = null,
                seriesId = item.seriesId,
                seasonNumber = item.lastSeasonNumber,
                episodeId = item.lastEpisodeId,
                posterUrl = item.coverUrl
            )
        }
        // Concluído: buscar o próximo via seriesInfo.
        val next = runCatching {
            val resp = xtream.seriesInfo(item.seriesId)
            val all = resp.normalizedEpisodes()
                .flatMap { (key, list) ->
                    val fb = key.toIntOrNull() ?: 0
                    list.map { it.toModel(item.seriesId, fb) }
                }
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNum }))
            val idx = all.indexOfFirst { it.id == item.lastEpisodeId }
            all.getOrNull(idx + 1)
        }.getOrNull()
        return if (next != null) {
            PlayerArgs(
                kind = PlayerKind.EPISODE,
                streamId = next.id.toIntOrNull() ?: 0,
                title = next.title,
                containerExtension = next.containerExtension,
                seriesId = next.seriesId,
                seasonNumber = next.seasonNumber,
                episodeId = next.id,
                posterUrl = item.coverUrl
            )
        } else {
            // Sem próximo (série terminou) — toca o último mesmo.
            PlayerArgs(
                kind = PlayerKind.EPISODE,
                streamId = item.lastEpisodeId.toIntOrNull() ?: 0,
                title = item.title,
                containerExtension = null,
                seriesId = item.seriesId,
                seasonNumber = item.lastSeasonNumber,
                episodeId = item.lastEpisodeId,
                posterUrl = item.coverUrl
            )
        }
    }
}

@Composable
fun ContinueWatchingScreen(
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit = { _, _, _ -> },
    // Callback separado para filmes recomendados (sem progresso). Em vez de
    // tocar direto como Continue Watching, abre a tela de detalhe com sinopse
    // — o usuário ainda não decidiu se quer ver.
    onOpenMovie: (PlayerArgs) -> Unit = onPlay,
    vm: ContinueWatchingViewModel = hiltViewModel()
) {
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val recentChannels by vm.recentChannels.collectAsState()
    val movieReco by vm.movieReco.collectAsState()
    val seriesReco by vm.seriesReco.collectAsState()
    val dim = rememberTvDim()

    androidx.compose.runtime.LaunchedEffect(movies.size, series.size) {
        vm.refreshRecommendations()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dim.ScreenPadding, vertical = 6.dp)
    ) {
        val nothingToContinue = movies.isEmpty() && series.isEmpty() && recentChannels.isEmpty()
        val noRecos = movieReco == null && seriesReco == null
        if (nothingToContinue && noRecos) {
            EmptyState(
                title = stringResource(R.string.empty_continue_title),
                message = stringResource(R.string.empty_continue_message),
                icon = Icons.Filled.PlayCircle
            )
            return
        }

        // Tamanhos compactos para caber pelo menos duas linhas de cards
        // sem scroll na primeira dobra em TV.
        var hasPriorSection = false
        val sectionTopPadding = { if (hasPriorSection) 8.dp else 0.dp }
        val headerStyle = MaterialTheme.typography.titleSmall
        val rowSpacing = 12.dp

        if (recentChannels.isNotEmpty()) {
            Text(
                stringResource(R.string.recent_channels),
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(recentChannels) { ch ->
                    com.iptv.app.ui.common.PosterCard(
                        title = ch.name,
                        imageUrl = ch.logoUrl,
                        fallbackIcon = Icons.Filled.Tv,
                        overrideWidth = 110.dp,
                        compactTitle = true
                    ) {
                        onPlay(
                            PlayerArgs(
                                kind = PlayerKind.LIVE,
                                streamId = ch.channelId,
                                title = ch.name,
                                containerExtension = null,
                                posterUrl = ch.logoUrl
                            )
                        )
                    }
                }
            }
            hasPriorSection = true
        }

        if (series.isNotEmpty()) {
            Text(
                stringResource(R.string.continue_series),
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(series) { s -> SeriesContinueCard(s, vm, onPlay) }
            }
            hasPriorSection = true
        }

        if (movies.isNotEmpty()) {
            Text(
                stringResource(R.string.continue_movies),
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(movies) { m -> MovieContinueCard(m, onPlay) }
            }
            hasPriorSection = true
        }

        movieReco?.let { row ->
            val header = row.seedTitle?.let {
                stringResource(R.string.recommended_because_movie, it)
            } ?: stringResource(R.string.recommended_top_movies)
            Text(
                header,
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(row.items) { m -> RecommendedMovieCard(m, onOpenMovie) }
            }
            hasPriorSection = true
        }

        seriesReco?.let { row ->
            val header = row.seedTitle?.let {
                stringResource(R.string.recommended_because_series, it)
            } ?: stringResource(R.string.recommended_top_series)
            Text(
                header,
                style = headerStyle,
                modifier = Modifier.padding(top = sectionTopPadding(), bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                items(row.items) { s -> RecommendedSeriesCard(s, onOpenSeries) }
            }
            hasPriorSection = true
        }
    }
}

// Pôsteres do Início em 110dp (220x ratio 2:3 = altura ~165dp). Antes era
// 150dp e a segunda linha caía abaixo da dobra em telas TV 1080p.
private val HomePosterWidth = 110.dp

@Composable
private fun MovieContinueCard(item: MovieProgressEntity, onPlay: (PlayerArgs) -> Unit) {
    val percent = if (item.durationMs > 0) (item.positionMs * 100 / item.durationMs).toInt().coerceIn(0, 100) else 0
    Column {
        PosterCard(
            title = item.title,
            imageUrl = item.posterUrl,
            fallbackIcon = Icons.Filled.Movie,
            overrideWidth = HomePosterWidth
        ) {
            onPlay(
                PlayerArgs(
                    kind = PlayerKind.MOVIE,
                    streamId = item.movieId,
                    title = item.title,
                    containerExtension = item.containerExtension,
                    posterUrl = item.posterUrl,
                    categoryId = item.categoryId,
                    startPositionMs = item.positionMs
                )
            )
        }
        ProgressStripe(percent)
    }
}

@Composable
private fun SeriesContinueCard(
    item: SeriesProgressEntity,
    vm: ContinueWatchingViewModel,
    onPlay: (PlayerArgs) -> Unit
) {
    var percent by androidx.compose.runtime.remember(item.lastEpisodeId) {
        androidx.compose.runtime.mutableStateOf(0)
    }
    androidx.compose.runtime.LaunchedEffect(item.lastEpisodeId) {
        percent = vm.episodePercent(item.lastEpisodeId)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column {
        PosterCard(
            title = "${item.title}\nT${item.lastSeasonNumber}E${item.lastEpisodeNum}",
            imageUrl = item.coverUrl,
            fallbackIcon = Icons.Filled.Tv,
            overrideWidth = HomePosterWidth
        ) {
            // Resolve em background para não bloquear a UI — se o último
            // episódio foi 100% concluído, vai para o próximo da série.
            scope.launch { onPlay(vm.resolveNextEpisode(item)) }
        }
        ProgressStripe(percent)
    }
}

@Composable
private fun RecommendedMovieCard(item: MovieCacheEntity, onPlay: (PlayerArgs) -> Unit) {
    PosterCard(
        title = item.name,
        imageUrl = item.posterUrl,
        fallbackIcon = Icons.Filled.Movie,
        overrideWidth = HomePosterWidth
    ) {
        onPlay(
            PlayerArgs(
                kind = PlayerKind.MOVIE,
                streamId = item.streamId,
                title = item.name,
                containerExtension = item.containerExtension,
                posterUrl = item.posterUrl,
                categoryId = item.categoryId
            )
        )
    }
}

@Composable
private fun RecommendedSeriesCard(
    item: SeriesCacheEntity,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit
) {
    PosterCard(
        title = item.name,
        imageUrl = item.coverUrl,
        fallbackIcon = Icons.Filled.Tv,
        overrideWidth = HomePosterWidth
    ) {
        onOpenSeries(item.seriesId, item.name, item.coverUrl)
    }
}

@Composable
private fun ProgressStripe(percent: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent / 100f)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.CenterStart)
        )
    }
}
