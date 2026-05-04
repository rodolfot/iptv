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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.TvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ContinueWatchingViewModel @Inject constructor(
    movieProgressDao: MovieProgressDao,
    private val seriesProgressDao: SeriesProgressDao
) : ViewModel() {
    val movies = movieProgressDao.observeInProgress()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val series = seriesProgressDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingScreen(
    onPlay: (PlayerArgs) -> Unit,
    vm: ContinueWatchingViewModel = hiltViewModel()
) {
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvDim.ScreenPadding, vertical = 12.dp)
    ) {
        if (movies.isEmpty() && series.isEmpty()) {
            Text(
                "Nada em andamento ainda. Comece a assistir um filme ou episódio para vê-lo aqui.",
                style = MaterialTheme.typography.bodyLarge
            )
            return
        }

        if (series.isNotEmpty()) {
            Text(
                "Continuar séries",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)) {
                items(series) { s -> SeriesContinueCard(s, onPlay) }
            }
        }

        if (movies.isNotEmpty()) {
            Text(
                "Continuar filmes",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(TvDim.MoviesGridColumns),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
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
private fun SeriesContinueCard(item: SeriesProgressEntity, onPlay: (PlayerArgs) -> Unit) {
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
