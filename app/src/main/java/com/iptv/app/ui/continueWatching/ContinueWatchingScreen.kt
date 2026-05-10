package com.iptv.app.ui.continueWatching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
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
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ContinueWatchingViewModel @Inject constructor(
    movieProgressDao: MovieProgressDao,
    private val seriesProgressDao: SeriesProgressDao,
    private val episodeProgressDao: EpisodeProgressDao
) : ViewModel() {
    val movies = movieProgressDao.observeInProgress()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val series = seriesProgressDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun episodePercent(episodeId: String): Int {
        val ep = episodeProgressDao.getById(episodeId) ?: return 0
        if (ep.durationMs <= 0L || ep.watched) return 0
        return ((ep.positionMs * 100L) / ep.durationMs).toInt().coerceIn(0, 100)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingScreen(
    onPlay: (PlayerArgs) -> Unit,
    vm: ContinueWatchingViewModel = hiltViewModel()
) {
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val dim = rememberTvDim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
    ) {
        if (movies.isEmpty() && series.isEmpty()) {
            Text(
                stringResource(R.string.continue_empty),
                style = MaterialTheme.typography.bodyLarge
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(dim.MoviesGridColumns),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
            ) {
                items(movies) { m -> MovieContinueCard(m, onPlay) }
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
