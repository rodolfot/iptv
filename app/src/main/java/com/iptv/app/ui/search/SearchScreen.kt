package com.iptv.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.cache.CatalogCacheRepository
import com.iptv.app.data.cache.toDomain
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.model.Series
import com.iptv.app.ui.common.ChannelCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.TvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResults(
    val channels: List<LiveChannel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList()
)

data class SearchUiState(
    val loadingCatalog: Boolean = false,
    val catalogReady: Boolean = false,
    val error: String? = null,
    val results: SearchResults = SearchResults()
)

enum class SearchFilter { ALL, LIVE, MOVIE, SERIES }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val cache: CatalogCacheRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()

    private var queryJob: Job? = null

    fun loadCatalog() {
        if (_state.value.catalogReady || _state.value.loadingCatalog) return
        _state.value = _state.value.copy(loadingCatalog = true, error = null)
        viewModelScope.launch {
            val errors = mutableListOf<Throwable>()
            val needsLive = cache.isStale(CatalogCacheRepository.Scope.LIVE_STREAMS)
            val needsMovies = cache.isStale(CatalogCacheRepository.Scope.MOVIE_STREAMS)
            val needsSeries = cache.isStale(CatalogCacheRepository.Scope.SERIES_LIST)
            if (needsLive) cache.refreshLiveStreams().exceptionOrNull()?.let(errors::add)
            if (needsMovies) cache.refreshMovieStreams().exceptionOrNull()?.let(errors::add)
            if (needsSeries) cache.refreshSeriesList().exceptionOrNull()?.let(errors::add)
            // Even with errors, if there is cached data we let search proceed.
            _state.value = _state.value.copy(
                loadingCatalog = false,
                catalogReady = true,
                error = errors.firstOrNull()?.message
            )
        }
    }

    fun retry() {
        _state.value = SearchUiState()
        loadCatalog()
    }

    fun onQueryChanged(raw: String, filter: SearchFilter) {
        queryJob?.cancel()
        val q = raw.trim()
        if (q.length < 2) {
            _state.value = _state.value.copy(results = SearchResults())
            return
        }
        queryJob = viewModelScope.launch {
            delay(200)
            val channels = if (filter == SearchFilter.ALL || filter == SearchFilter.LIVE)
                cache.searchLive(q).map { it.toDomain() }
            else emptyList()
            val movies = if (filter == SearchFilter.ALL || filter == SearchFilter.MOVIE)
                cache.searchMovies(q).map { it.toDomain() }
            else emptyList()
            val series = if (filter == SearchFilter.ALL || filter == SearchFilter.SERIES)
                cache.searchSeries(q).map { it.toDomain() }
            else emptyList()
            _state.value = _state.value.copy(
                results = SearchResults(channels, movies, series)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (id: Int, title: String, cover: String?) -> Unit,
    vm: SearchViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }

    LaunchedEffect(Unit) { vm.loadCatalog() }
    LaunchedEffect(query, filter) { vm.onQueryChanged(query, filter) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvDim.ScreenPadding, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            enabled = state.catalogReady
        )
        Row(
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(stringResource(R.string.filter_all), filter == SearchFilter.ALL) { filter = SearchFilter.ALL }
            FilterChip(stringResource(R.string.filter_channels), filter == SearchFilter.LIVE) { filter = SearchFilter.LIVE }
            FilterChip(stringResource(R.string.filter_movies), filter == SearchFilter.MOVIE) { filter = SearchFilter.MOVIE }
            FilterChip(stringResource(R.string.filter_series), filter == SearchFilter.SERIES) { filter = SearchFilter.SERIES }
        }

        when {
            state.loadingCatalog -> Text(stringResource(R.string.loading_catalog))
            state.error != null && !state.catalogReady ->
                ErrorState(message = state.error!!, onRetry = { vm.retry() })
            query.trim().length < 2 -> Text(
                stringResource(R.string.search_min_chars),
                style = MaterialTheme.typography.bodyLarge
            )
            else -> ResultsContent(state.results, onPlay, onOpenSeries)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        stringResource(if (enabled) R.string.search_hint else R.string.loading_catalog),
                        color = Color(0x99FFFFFF),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                inner()
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(
            if (selected) "• $label" else label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResultsContent(
    results: SearchResults,
    onPlay: (PlayerArgs) -> Unit,
    onOpenSeries: (Int, String, String?) -> Unit
) {
    val empty = results.channels.isEmpty() && results.movies.isEmpty() && results.series.isEmpty()
    if (empty) {
        Text(stringResource(R.string.nothing_found), style = MaterialTheme.typography.bodyLarge)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (results.channels.isNotEmpty()) {
            ResultRow("Canais", results.channels.size, Icons.Filled.LiveTv) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)) {
                    items(results.channels) { ch ->
                        ChannelCard(
                            title = ch.name,
                            number = ch.num,
                            logoUrl = ch.logoUrl
                        ) {
                            onPlay(
                                PlayerArgs(
                                    kind = PlayerKind.LIVE,
                                    streamId = ch.id,
                                    title = ch.name,
                                    containerExtension = null
                                )
                            )
                        }
                    }
                }
            }
        }
        if (results.movies.isNotEmpty()) {
            ResultRow("Filmes", results.movies.size, Icons.Filled.Movie) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)) {
                    items(results.movies) { m ->
                        PosterCard(
                            title = m.name,
                            imageUrl = m.posterUrl,
                            fallbackIcon = Icons.Filled.Movie
                        ) {
                            onPlay(
                                PlayerArgs(
                                    kind = PlayerKind.MOVIE,
                                    streamId = m.id,
                                    title = m.name,
                                    containerExtension = m.containerExtension,
                                    posterUrl = m.posterUrl,
                                    categoryId = m.categoryId
                                )
                            )
                        }
                    }
                }
            }
        }
        if (results.series.isNotEmpty()) {
            ResultRow("Séries", results.series.size, Icons.Filled.Tv) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)) {
                    items(results.series) { s ->
                        PosterCard(
                            title = s.name,
                            imageUrl = s.coverUrl,
                            fallbackIcon = Icons.Filled.Tv
                        ) {
                            onOpenSeries(s.id, s.name, s.coverUrl)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    count: Int,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text(
                "$label ($count)",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
            )
        }
        content()
    }
}
