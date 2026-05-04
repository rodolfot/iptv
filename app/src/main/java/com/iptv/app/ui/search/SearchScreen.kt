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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.model.Series
import com.iptv.app.domain.model.toModel
import com.iptv.app.ui.common.ChannelCard
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
    private val repo: XtreamRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()

    private var allChannels: List<LiveChannel> = emptyList()
    private var allMovies: List<Movie> = emptyList()
    private var allSeries: List<Series> = emptyList()

    private var queryJob: Job? = null

    fun loadCatalog() {
        if (_state.value.catalogReady || _state.value.loadingCatalog) return
        _state.value = _state.value.copy(loadingCatalog = true, error = null)
        viewModelScope.launch {
            runCatching {
                val live = repo.liveStreams(null).map { it.toModel() }
                val movies = repo.vodStreams(null).map { it.toModel() }
                val series = repo.series(null).map { it.toModel() }
                Triple(live, movies, series)
            }.onSuccess { (live, movies, series) ->
                allChannels = live
                allMovies = movies
                allSeries = series
                _state.value = _state.value.copy(loadingCatalog = false, catalogReady = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(loadingCatalog = false, error = e.message)
            }
        }
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
            val needle = q.lowercase()
            val channels = if (filter == SearchFilter.ALL || filter == SearchFilter.LIVE)
                allChannels.asSequence().filter { it.name.lowercase().contains(needle) }.take(80).toList()
            else emptyList()
            val movies = if (filter == SearchFilter.ALL || filter == SearchFilter.MOVIE)
                allMovies.asSequence().filter { it.name.lowercase().contains(needle) }.take(80).toList()
            else emptyList()
            val series = if (filter == SearchFilter.ALL || filter == SearchFilter.SERIES)
                allSeries.asSequence().filter { it.name.lowercase().contains(needle) }.take(80).toList()
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
            FilterChip("Tudo", filter == SearchFilter.ALL) { filter = SearchFilter.ALL }
            FilterChip("Canais", filter == SearchFilter.LIVE) { filter = SearchFilter.LIVE }
            FilterChip("Filmes", filter == SearchFilter.MOVIE) { filter = SearchFilter.MOVIE }
            FilterChip("Séries", filter == SearchFilter.SERIES) { filter = SearchFilter.SERIES }
        }

        when {
            state.loadingCatalog -> Text("Carregando catálogo...")
            state.error != null -> Text("Erro: ${state.error}", color = MaterialTheme.colorScheme.error)
            query.trim().length < 2 -> Text(
                "Digite pelo menos 2 letras para buscar.",
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
                        if (enabled) "Buscar canais, filmes ou séries..."
                        else "Carregando catálogo...",
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
        Text("Nada encontrado.", style = MaterialTheme.typography.bodyLarge)
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
