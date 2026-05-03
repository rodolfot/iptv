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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.iptv.app.ui.common.TvDim
import com.iptv.app.ui.favorites.FavoritesScreen
import com.iptv.app.ui.live.LiveSection
import com.iptv.app.ui.movies.MoviesSection
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.series.SeriesSection
import com.iptv.app.ui.settings.SettingsScreen

private val tabs = listOf("Ao Vivo", "Filmes", "Séries", "Favoritos", "Config")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlay: (PlayerArgs) -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val parental = remember { ParentalSession() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(selected, { selected = it })
            when (selected) {
                0 -> LiveSection(vm = vm, parental = parental, onPlay = onPlay)
                1 -> MoviesSection(vm = vm, parental = parental, onPlay = onPlay)
                2 -> SeriesSection(vm = vm, onPlay = onPlay)
                3 -> FavoritesScreen(vm = vm, parental = parental, onPlay = onPlay)
                4 -> SettingsScreen(vm = vm, onLogout = onLogout)
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
        Text("IPTV", style = MaterialTheme.typography.headlineMedium)
        TabRow(selectedTabIndex = selected, modifier = Modifier.padding(start = 16.dp)) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = i == selected,
                    onFocus = { onSelected(i) }
                ) {
                    Text(t, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}
