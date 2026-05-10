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
import com.iptv.app.ui.common.TouchableButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.api.SeriesInfoDetail
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.EpisodeProgressEntity
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Episode
import com.iptv.app.domain.model.Season
import com.iptv.app.domain.model.toModel
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.PosterCard
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.rememberTvDim
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
    val episodesBySeason: Map<Int, List<Episode>>,
    val resume: SeriesResume? = null,
    val watchedEpisodes: Set<String> = emptySet(),
    val episodePercents: Map<String, Int> = emptyMap(),
    val info: SeriesInfoDetail? = null
)

data class SeriesResume(
    val episode: Episode,
    val positionMs: Long,
    val percent: Int
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val episodeProgressDao: EpisodeProgressDao,
    private val seriesProgressDao: SeriesProgressDao
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

                    val progressBySeries = episodeProgressDao.getBySeries(id)
                    val watched = progressBySeries.filter { it.watched }.map { it.episodeId }.toSet()
                    val percents = progressBySeries
                        .filter { !it.watched && it.durationMs > 0 }
                        .associate { it.episodeId to ((it.positionMs * 100L) / it.durationMs).toInt().coerceIn(0, 100) }

                    val resume = buildResume(id, episodesMap, progressBySeries)

                    _state.value = SeriesDetail(
                        seriesId = id,
                        title = title,
                        seasons = mergedSeasons,
                        episodesBySeason = episodesMap,
                        resume = resume,
                        watchedEpisodes = watched,
                        episodePercents = percents,
                        info = resp.info
                    )
                }
        }
    }

    private suspend fun buildResume(
        seriesId: Int,
        episodesMap: Map<Int, List<Episode>>,
        progress: List<EpisodeProgressEntity>
    ): SeriesResume? {
        // Priority: in-progress episode (latest by updatedAt). Otherwise, next unwatched
        // episode after the last one the user watched (from SeriesProgress).
        val inProgress = progress
            .filter { !it.watched && it.positionMs > 0 }
            .maxByOrNull { it.updatedAt }
        if (inProgress != null) {
            val ep = findEpisode(episodesMap, inProgress.episodeId)
            if (ep != null) {
                val pct = if (inProgress.durationMs > 0)
                    ((inProgress.positionMs * 100L) / inProgress.durationMs).toInt().coerceIn(0, 100)
                else 0
                return SeriesResume(ep, inProgress.positionMs, pct)
            }
        }
        val seriesLast: SeriesProgressEntity? = seriesProgressDao.getById(seriesId)
        if (seriesLast != null) {
            val nextAfter = nextEpisodeAfter(
                episodesMap,
                seriesLast.lastSeasonNumber,
                seriesLast.lastEpisodeNum
            )
            if (nextAfter != null) return SeriesResume(nextAfter, 0L, 0)
        }
        return null
    }

    private fun findEpisode(map: Map<Int, List<Episode>>, episodeId: String): Episode? =
        map.values.flatten().firstOrNull { it.id == episodeId }

    private fun nextEpisodeAfter(
        map: Map<Int, List<Episode>>,
        season: Int,
        episodeNum: Int
    ): Episode? {
        val flat = map.entries
            .sortedBy { it.key }
            .flatMap { (s, list) -> list.sortedBy { it.episodeNum }.map { s to it } }
        val idx = flat.indexOfFirst { it.first == season && it.second.episodeNum == episodeNum }
        if (idx < 0) return null
        return flat.getOrNull(idx + 1)?.second
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
    val dim = rememberTvDim()
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var openSeries by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var localFilter by rememberSaveable(selectedCat) { mutableStateOf("") }

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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dim.ScreenPadding, vertical = 12.dp)) {
        val catCols = when (dim.formFactor) {
            com.iptv.app.ui.common.FormFactor.Phone -> 2
            com.iptv.app.ui.common.FormFactor.Tablet -> 3
            com.iptv.app.ui.common.FormFactor.Tv -> 3
        }
        if (selectedCat == null) {
            Text(stringResource(R.string.section_series_categories), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            if (cats.loading && cats.items.isEmpty()) Text(stringResource(R.string.loading))
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadSeriesCategories(forceRefresh = true) }) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(catCols),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
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
                TouchableButton(onClick = { selectedCat = null }) { Text(stringResource(R.string.back)) }
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
            LocalFilterField(
                value = localFilter,
                onValueChange = { localFilter = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val needle = localFilter.trim().lowercase()
            val filteredSeries = if (needle.isBlank()) series.items
                else series.items.filter { it.name.lowercase().contains(needle) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(dim.SeriesGridColumns),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
            ) {
                items(filteredSeries) { s ->
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
    val dim = rememberTvDim()
    var selectedSeason by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(seriesId) { vm.load(seriesId, title) }

    val seasonCols = when (dim.formFactor) {
        com.iptv.app.ui.common.FormFactor.Phone -> 2
        com.iptv.app.ui.common.FormFactor.Tablet -> 3
        com.iptv.app.ui.common.FormFactor.Tv -> 4
    }
    val episodeCols = when (dim.formFactor) {
        com.iptv.app.ui.common.FormFactor.Phone -> 2
        com.iptv.app.ui.common.FormFactor.Tablet -> 3
        com.iptv.app.ui.common.FormFactor.Tv -> 4
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dim.ScreenPadding, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            TouchableButton(onClick = {
                if (selectedSeason != null) selectedSeason = null else onBack()
            }) { Text(stringResource(R.string.back)) }
            Text("  $title", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp))
            Box(modifier = Modifier.weight(1f))
            state?.resume?.let { resume ->
                val labelRes = if (resume.positionMs > 0) R.string.series_resume else R.string.series_play_next
                TouchableButton(onClick = {
                    onPlay(
                        PlayerArgs(
                            kind = PlayerKind.EPISODE,
                            streamId = resume.episode.id.toIntOrNull() ?: 0,
                            title = resume.episode.title,
                            containerExtension = resume.episode.containerExtension,
                            seriesId = resume.episode.seriesId,
                            seasonNumber = resume.episode.seasonNumber,
                            episodeId = resume.episode.id,
                            startPositionMs = resume.positionMs
                        )
                    )
                }) {
                    Text(stringResource(labelRes, resume.episode.seasonNumber, resume.episode.episodeNum))
                }
            }
        }
        val detail = state
        if (detail == null) {
            Text(stringResource(R.string.loading))
            return
        }
        if (selectedSeason == null) {
            detail.info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                Text(
                    stringResource(R.string.synopsis),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    plot,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Text(stringResource(R.string.section_seasons), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(seasonCols),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
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
                columns = GridCells.Fixed(episodeCols),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
            ) {
                items(episodes) { e ->
                    val isWatched = e.id in detail.watchedEpisodes
                    val pct = detail.episodePercents[e.id] ?: 0
                    val titlePrefix = if (isWatched) "✓ " else ""
                    PosterCard(
                        title = "${titlePrefix}T${e.seasonNumber}E${e.episodeNum} • ${e.title}" +
                            (if (pct in 1..99) "  (${pct}%)" else ""),
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
