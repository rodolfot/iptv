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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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
    private val currentProfile: CurrentProfile,
    private val recommender: Recommender,
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingScreen(
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit = { _, _, _ -> },
    vm: ContinueWatchingViewModel = hiltViewModel()
) {
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
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
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
    ) {
        val nothingToContinue = movies.isEmpty() && series.isEmpty()
        val noRecos = movieReco == null && seriesReco == null
        if (nothingToContinue && noRecos) {
            EmptyState(
                title = stringResource(R.string.empty_continue_title),
                message = stringResource(R.string.empty_continue_message),
                icon = Icons.Filled.PlayCircle
            )
            return
        }

        if (series.isNotEmpty()) {
            Text(
                stringResource(R.string.continue_series),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                items(series) { s -> SeriesContinueCard(s, vm, onPlay) }
            }
        }

        if (movies.isNotEmpty()) {
            Text(
                stringResource(R.string.continue_movies),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            // Continue Watching for movies tops out around 20 items, so we render
            // them as a horizontal row instead of a grid: keeps the screen
            // scrollable as a whole while staying inside a Column.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                items(movies) { m -> MovieContinueCard(m, onPlay) }
            }
        }

        movieReco?.let { row ->
            Text(
                stringResource(R.string.recommended_because_movie, row.seedTitle),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                items(row.items) { m -> RecommendedMovieCard(m, onPlay) }
            }
        }

        seriesReco?.let { row ->
            Text(
                stringResource(R.string.recommended_because_series, row.seedTitle),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing)) {
                items(row.items) { s -> RecommendedSeriesCard(s, onOpenSeries) }
            }
        }
    }
}

@Composable
private fun MovieContinueCard(item: MovieProgressEntity, onPlay: (PlayerArgs) -> Unit) {
    val percent = if (item.durationMs > 0) (item.positionMs * 100 / item.durationMs).toInt().coerceIn(0, 100) else 0
    Column {
        PosterCard(
            title = item.title,
            imageUrl = item.posterUrl,
            fallbackIcon = Icons.Filled.Movie
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
    Column {
        PosterCard(
            title = "${item.title}\nT${item.lastSeasonNumber}E${item.lastEpisodeNum}",
            imageUrl = item.coverUrl,
            fallbackIcon = Icons.Filled.Tv
        ) {
            onPlay(
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
            )
        }
        ProgressStripe(percent)
    }
}

@Composable
private fun RecommendedMovieCard(item: MovieCacheEntity, onPlay: (PlayerArgs) -> Unit) {
    PosterCard(
        title = item.name,
        imageUrl = item.posterUrl,
        fallbackIcon = Icons.Filled.Movie
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
        fallbackIcon = Icons.Filled.Tv
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
