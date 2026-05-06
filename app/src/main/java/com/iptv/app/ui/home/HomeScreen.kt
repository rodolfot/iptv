package com.iptv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.iptv.app.ui.common.TvDim
import com.iptv.app.ui.continueWatching.ContinueWatchingScreen
import com.iptv.app.ui.favorites.FavoritesScreen
import com.iptv.app.ui.live.ChannelDetailScreen
import com.iptv.app.ui.live.LiveSection
import com.iptv.app.ui.movies.MovieDetailScreen
import com.iptv.app.ui.movies.MoviesSection
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.search.SearchScreen
import com.iptv.app.ui.series.SeriesDetailScreen
import com.iptv.app.ui.series.SeriesSection
import com.iptv.app.ui.settings.SettingsScreen
import com.iptv.app.ui.update.UpdatePromptHost

private val TAB_LABELS = intArrayOf(
    com.iptv.app.R.string.tab_home,
    com.iptv.app.R.string.tab_search,
    com.iptv.app.R.string.tab_live,
    com.iptv.app.R.string.tab_movies,
    com.iptv.app.R.string.tab_series,
    com.iptv.app.R.string.tab_favorites,
    com.iptv.app.R.string.tab_settings
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlay: (PlayerArgs) -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val parental = remember { ParentalSession() }
    var openSeries by remember { mutableStateOf<Triple<Int, String, String?>?>(null) }
    var openMovie by remember { mutableStateOf<PlayerArgs?>(null) }
    var openChannel by remember { mutableStateOf<com.iptv.app.domain.model.LiveChannel?>(null) }

    // Movies open the detail screen first; other kinds (live, episode) play immediately.
    val handlePlay: (PlayerArgs) -> Unit = { args ->
        if (args.kind == com.iptv.app.ui.player.PlayerKind.MOVIE && args.startPositionMs == 0L) {
            openMovie = args
        } else {
            onPlay(args)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UpdatePromptHost()
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(selected, { selected = it })
            when {
                openMovie != null -> MovieDetailScreen(
                    args = openMovie!!,
                    onPlay = { args -> openMovie = null; onPlay(args) },
                    onBack = { openMovie = null }
                )
                openChannel != null -> ChannelDetailScreen(
                    channel = openChannel!!,
                    onBack = { openChannel = null },
                    onPlay = { args -> openChannel = null; onPlay(args) }
                )
                openSeries != null -> {
                    val (id, title, _) = openSeries!!
                    SeriesDetailScreen(
                        seriesId = id,
                        title = title,
                        onBack = { openSeries = null },
                        onPlay = onPlay
                    )
                }
                else -> when (selected) {
                    0 -> ContinueWatchingScreen(onPlay = onPlay)
                    1 -> SearchScreen(
                        onPlay = handlePlay,
                        onOpenSeries = { id, title, cover -> openSeries = Triple(id, title, cover) }
                    )
                    2 -> LiveSection(
                        vm = vm,
                        parental = parental,
                        onPlay = onPlay,
                        onOpenChannel = { ch -> openChannel = ch }
                    )
                    3 -> MoviesSection(vm = vm, parental = parental, onPlay = handlePlay)
                    4 -> SeriesSection(vm = vm, onPlay = onPlay)
                    5 -> FavoritesScreen(vm = vm, parental = parental, onPlay = handlePlay)
                    6 -> SettingsScreen(vm = vm, onLogout = onLogout)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvDim.ScreenPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            stringResource(com.iptv.app.R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )
        TabRow(selectedTabIndex = selected, modifier = Modifier.padding(start = 16.dp)) {
            TAB_LABELS.forEachIndexed { i, resId ->
                Tab(
                    selected = i == selected,
                    onFocus = { onSelected(i) },
                    onClick = { onSelected(i) }
                ) {
                    Text(
                        stringResource(resId),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
