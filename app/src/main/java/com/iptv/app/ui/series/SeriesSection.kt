package com.iptv.app.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Episode
import com.iptv.app.domain.model.Season
import com.iptv.app.domain.model.toModel
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.TvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetail(
    val seriesId: Int,
    val title: String,
    val seasons: List<Season>,
    val episodesBySeason: Map<Int, List<Episode>>
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val repo: XtreamRepository
) : ViewModel() {
    private val _state = MutableStateFlow<SeriesDetail?>(null)
    val state = _state.asStateFlow()

    fun load(id: Int, title: String) {
        viewModelScope.launch {
            runCatching { repo.seriesInfo(id) }
                .onSuccess { resp ->
                    val seasons = resp.seasons?.map { it.toModel() } ?: emptyList()
                    val episodesMap = (resp.episodes ?: emptyMap()).mapNotNull { (k, v) ->
                        val sk = k.toIntOrNull() ?: return@mapNotNull null
                        sk to v.sortedBy { it.episodeNum ?: 0 }.map { it.toModel(id, sk) }
                    }.toMap()
                    val mergedSeasons = if (seasons.isEmpty()) {
                        episodesMap.keys.sorted().map { sn ->
                            Season(sn, "Temporada $sn", null, episodesMap[sn]?.size ?: 0)
                        }
                    } else seasons.sortedBy { it.seasonNumber }
                    _state.value = SeriesDetail(id, title, mergedSeasons, episodesMap)
                }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesSection(
    vm: HomeViewModel,
    onPlay: (PlayerArgs) -> Unit
) {
    val cats by vm.seriesCategories.collectAsState()
    val series by vm.series.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var openSeries by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadSeriesCategories()
    }

    if (openSeries != null) {
        SeriesDetailScreen(
            seriesId = openSeries!!.first,
            title = openSeries!!.second,
            onBack = { openSeries = null },
            onPlay = onPlay
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = TvDim.ScreenPadding, vertical = 12.dp)) {
        if (selectedCat == null) {
            Text(stringResource(R.string.section_series_categories), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            if (cats.loading && cats.items.isEmpty()) Text(stringResource(R.string.loading))
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadSeriesCategories(forceRefresh = true) }) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
            ) {
                items(cats.items) { cat ->
                    CategoryCard(title = cat.name, count = null, locked = false) {
                        selectedCat = cat.id
                        vm.loadSeries(cat.id)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Button(onClick = { selectedCat = null }) { Text(stringResource(R.string.back)) }
                val seriesDefault = stringResource(R.string.section_series_default)
                Text(
                    "  ${cats.items.firstOrNull { it.id == selectedCat }?.name ?: seriesDefault}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Box(modifier = Modifier.weight(1f))
                SortMenuButton(
                    current = settings.seriesSort,
                    options = SortOption.SERIES_OPTIONS
                ) { vm.setSort(SortScope.SERIES, it) }
            }
            if (series.loading && series.items.isEmpty()) Text(stringResource(R.string.loading))
            series.error?.let { ErrorState(message = it, onRetry = { vm.loadSeries(selectedCat, forceRefresh = true) }) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(TvDim.SeriesGridColumns),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
            ) {
                items(series.items) { s ->
                    PosterCard(
                        title = s.name,
                        imageUrl = s.coverUrl,
                        fallbackIcon = Icons.Filled.Tv
                    ) {
                        openSeries = s.id to s.name
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    title: String,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    vm: SeriesDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var selectedSeason by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(seriesId) { vm.load(seriesId, title) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = TvDim.ScreenPadding, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Button(onClick = {
                if (selectedSeason != null) selectedSeason = null else onBack()
            }) { Text("← Voltar") }
            Text("  $title", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp))
        }
        val detail = state
        if (detail == null) {
            Text(stringResource(R.string.loading))
            return
        }
        if (selectedSeason == null) {
            Text(stringResource(R.string.section_seasons), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
            ) {
                items(detail.seasons) { s ->
                    CategoryCard(
                        title = s.name,
                        count = s.episodeCount.takeIf { it > 0 } ?: detail.episodesBySeason[s.seasonNumber]?.size,
                        locked = false
                    ) { selectedSeason = s.seasonNumber }
                }
            }
        } else {
            val episodes = detail.episodesBySeason[selectedSeason] ?: emptyList()
            Text(
                "Temporada $selectedSeason — ${episodes.size} episódios",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
            ) {
                items(episodes) { e ->
                    PosterCard(
                        title = "T${e.seasonNumber}E${e.episodeNum} • ${e.title}",
                        imageUrl = e.poster,
                        fallbackIcon = Icons.Filled.Tv
                    ) {
                        onPlay(
                            PlayerArgs(
                                kind = PlayerKind.EPISODE,
                                streamId = e.id.toIntOrNull() ?: 0,
                                title = e.title,
                                containerExtension = e.containerExtension,
                                seriesId = e.seriesId,
                                seasonNumber = e.seasonNumber,
                                episodeId = e.id
                            )
                        )
                    }
                }
            }
        }
    }
}
