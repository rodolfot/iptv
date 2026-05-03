package com.iptv.app.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ChannelCard
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.TvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.ui.parental.ParentalPinDialog
import com.iptv.app.ui.parental.ParentalSession
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveSection(
    vm: HomeViewModel,
    parental: ParentalSession,
    onPlay: (PlayerArgs) -> Unit
) {
    val cats by vm.liveCategories.collectAsState()
    val channels by vm.channels.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<Category?>(null) }
    var pendingChannel by remember { mutableStateOf<PlayerArgs?>(null) }

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadLiveCategories()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = TvDim.ScreenPadding, vertical = 12.dp)) {
        if (selectedCat == null) {
            Text("Categorias", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            if (cats.loading) Text("Carregando...")
            cats.error?.let { Text("Erro: $it", color = MaterialTheme.colorScheme.error) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
            ) {
                items(cats.items) { cat ->
                    CategoryCard(
                        title = cat.name,
                        count = null,
                        locked = cat.isAdult && !parental.isUnlocked()
                    ) {
                        if (cat.isAdult && !parental.isUnlocked()) {
                            pendingCategory = cat
                        } else {
                            selectedCat = cat.id
                            vm.loadChannels(cat.id)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Button(onClick = { selectedCat = null }) { Text("← Voltar") }
                Text(
                    "  ${cats.items.firstOrNull { it.id == selectedCat }?.name ?: "Canais"}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Box(modifier = Modifier.weight(1f))
                SortMenuButton(
                    current = settings.liveSort,
                    options = SortOption.LIVE_OPTIONS
                ) { vm.setSort(SortScope.LIVE, it) }
            }
            if (channels.loading) Text("Carregando...")
            channels.error?.let { Text("Erro: $it", color = MaterialTheme.colorScheme.error) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(TvDim.ChannelGridColumns),
                horizontalArrangement = Arrangement.spacedBy(TvDim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(TvDim.CardSpacing)
            ) {
                items(channels.items) { ch ->
                    val cat = cats.items.firstOrNull { it.id == selectedCat }
                    val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                    ChannelCard(
                        title = ch.name,
                        number = ch.num,
                        logoUrl = ch.logoUrl,
                        locked = locked
                    ) {
                        val args = PlayerArgs(
                            kind = PlayerKind.LIVE,
                            streamId = ch.id,
                            title = ch.name,
                            containerExtension = null
                        )
                        if (locked) pendingChannel = args
                        else onPlay(args)
                    }
                }
            }
        }
    }

    pendingCategory?.let { cat ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingCategory = null
                selectedCat = cat.id
                vm.loadChannels(cat.id)
            },
            onCancel = { pendingCategory = null }
        )
    }
    pendingChannel?.let { args ->
        ParentalPinDialog(
            expectedPin = settings.parentalPin,
            onUnlocked = {
                parental.unlock()
                pendingChannel = null
                onPlay(args)
            },
            onCancel = { pendingChannel = null }
        )
    }
}
