package com.iptv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.TouchableButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.data.api.VodInfoDetail
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.FavoriteDao
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.domain.model.ContentType
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieDetailUiState(
    val loading: Boolean = false,
    val info: VodInfoDetail? = null,
    val resumeMs: Long = 0L,
    val isFavorite: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val movieProgressDao: MovieProgressDao,
    private val favoriteDao: FavoriteDao
) : ViewModel() {
    private val _state = MutableStateFlow(MovieDetailUiState())
    val state = _state.asStateFlow()

    fun load(streamId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.vodInfo(streamId) }
                .onSuccess { resp ->
                    val resume = movieProgressDao.getById(streamId)
                        ?.takeIf { !it.watched }
                        ?.positionMs ?: 0L
                    _state.value = _state.value.copy(
                        loading = false,
                        info = resp.info,
                        resumeMs = resume,
                        isFavorite = isFavorite(streamId)
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                }
        }
    }

    private suspend fun isFavorite(id: Int): Boolean = try {
        favoriteDao.observeAll().first()
            .any { it.type == ContentType.MOVIE && it.itemId == id }
    } catch (_: Throwable) { false }

    fun toggleFavorite(args: PlayerArgs) {
        viewModelScope.launch {
            val current = _state.value.isFavorite
            if (current) {
                favoriteDao.delete(ContentType.MOVIE, args.streamId)
            } else {
                favoriteDao.insert(
                    FavoriteEntity(
                        type = ContentType.MOVIE,
                        itemId = args.streamId,
                        name = args.title,
                        logoUrl = args.posterUrl,
                        categoryId = args.categoryId,
                        containerExtension = args.containerExtension
                    )
                )
            }
            _state.value = _state.value.copy(isFavorite = !current)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    args: PlayerArgs,
    onPlay: (PlayerArgs) -> Unit,
    onBack: () -> Unit,
    vm: MovieDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val dim = rememberTvDim()
    LaunchedEffect(args.streamId) { vm.load(args.streamId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TouchableButton(onClick = onBack) { Text(stringResource(R.string.back)) }

        val (posterW, posterH) = when (dim.formFactor) {
            com.iptv.app.ui.common.FormFactor.Phone -> 140.dp to 210.dp
            com.iptv.app.ui.common.FormFactor.Tablet -> 200.dp to 300.dp
            com.iptv.app.ui.common.FormFactor.Tv -> 280.dp to 420.dp
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone) 12.dp else 24.dp)) {
            Box(
                modifier = Modifier
                    .width(posterW)
                    .height(posterH)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (args.posterUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.Movie, contentDescription = null)
                } else {
                    AsyncImage(model = args.posterUrl, contentDescription = args.title)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(args.title, style = MaterialTheme.typography.headlineMedium)
                state.info?.let { info ->
                    info.releasedate?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_release, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    info.rating?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_rating, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    info.duration?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_duration, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    info.genre?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_genre, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    info.director?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_director, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    info.cast?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.movie_cast, it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.error?.let {
                    Text(stringResource(R.string.error_prefix, it), color = MaterialTheme.colorScheme.error)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (state.resumeMs > 0L) {
                        TouchableButton(onClick = { onPlay(args.copy(startPositionMs = state.resumeMs)) }) {
                            Text(stringResource(R.string.movie_resume, formatTime(state.resumeMs)))
                        }
                        TouchableButton(onClick = { onPlay(args.copy(startPositionMs = 0L)) }) {
                            Text(stringResource(R.string.movie_restart))
                        }
                    } else {
                        TouchableButton(onClick = { onPlay(args) }) {
                            Text(stringResource(R.string.movie_play))
                        }
                    }
                    TouchableButton(onClick = { vm.toggleFavorite(args) }) {
                        Text(stringResource(
                            if (state.isFavorite) R.string.remove_favorite else R.string.add_favorite
                        ))
                    }
                }
            }
        }

        state.info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.synopsis),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(plot, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
