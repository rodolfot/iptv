package com.iptv.app.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.R
import com.iptv.app.ui.common.TouchableButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.prefs.SortScope
import com.iptv.app.domain.model.Category
import com.iptv.app.domain.sort.SortOption
import com.iptv.app.ui.common.CategoryCard
import com.iptv.app.ui.common.ChannelCard
import com.iptv.app.ui.common.ErrorState
import com.iptv.app.ui.common.LocalFilterField
import com.iptv.app.ui.common.PullToRefreshBox
import com.iptv.app.ui.common.SortMenuButton
import com.iptv.app.ui.common.rememberTvDim
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
    onPlay: (PlayerArgs) -> Unit,
    onOpenChannel: (com.iptv.app.domain.model.LiveChannel) -> Unit = {}
) {
    val rawCats by vm.liveCategories.collectAsState()
    val channels by vm.channels.collectAsState()
    val epgNow by vm.epgNow.collectAsState()
    val settings by vm.settingsFlow.collectAsState()
    val kidsAllowed by vm.kidsAllowedCategories.collectAsState()
    // In Kids mode the parent picks specific categories; everything else is hidden.
    val cats = if (kidsAllowed.isEmpty()) rawCats
        else rawCats.copy(items = rawCats.items.filter { "live:${it.id}" in kidsAllowed })
    val dim = rememberTvDim()
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<Category?>(null) }
    var pendingChannel by remember { mutableStateOf<PlayerArgs?>(null) }
    var localFilter by rememberSaveable(selectedCat) { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (cats.items.isEmpty()) vm.loadLiveCategories()
    }
    androidx.activity.compose.BackHandler(enabled = selectedCat != null) {
        selectedCat = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dim.ScreenPadding, vertical = 12.dp)) {
        val catCols = when (dim.formFactor) {
            com.iptv.app.ui.common.FormFactor.Phone -> 2
            com.iptv.app.ui.common.FormFactor.Tablet -> 3
            com.iptv.app.ui.common.FormFactor.Tv -> 3
        }
        if (selectedCat == null) {
            Text(stringResource(R.string.section_categories), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            LocalFilterField(
                value = categoryFilter,
                onValueChange = { categoryFilter = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val needleCat = categoryFilter.trim().lowercase()
            val visibleCats = if (needleCat.isBlank()) cats.items
            else cats.items.filter { it.name.lowercase().contains(needleCat) }
            if (cats.loading && cats.items.isEmpty()) Text(stringResource(R.string.loading))
            cats.error?.let { ErrorState(message = it, onRetry = { vm.loadLiveCategories(forceRefresh = true) }) }
            PullToRefreshBox(
                isRefreshing = cats.loading,
                onRefresh = { vm.loadLiveCategories(forceRefresh = true) },
                enabled = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(catCols),
                    horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                    verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
                ) {
                    items(visibleCats) { cat ->
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
            }
        } else {
            val sectionDefault = stringResource(R.string.section_live_default)
            val categoryName = cats.items.firstOrNull { it.id == selectedCat }?.name ?: sectionDefault
            val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
            if (isPhone) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    TouchableButton(onClick = { selectedCat = null }) { Text(stringResource(R.string.back)) }
                    Text(
                        categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    SortMenuButton(
                        current = settings.liveSort,
                        options = SortOption.LIVE_OPTIONS
                    ) { vm.setSort(SortScope.LIVE, it) }
                }
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    TouchableButton(onClick = { selectedCat = null }) { Text(stringResource(R.string.back)) }
                    Text(
                        "  $categoryName",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Box(modifier = Modifier.weight(1f))
                    SortMenuButton(
                        current = settings.liveSort,
                        options = SortOption.LIVE_OPTIONS
                    ) { vm.setSort(SortScope.LIVE, it) }
                }
            }
            if (channels.loading && channels.items.isEmpty()) Text(stringResource(R.string.loading))
            channels.error?.let { ErrorState(message = it, onRetry = { vm.loadChannels(selectedCat, forceRefresh = true) }) }
            LocalFilterField(
                value = localFilter,
                onValueChange = { localFilter = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val needle = localFilter.trim().lowercase()
            val filteredChannels = if (needle.isBlank()) channels.items
                else channels.items.filter { it.name.lowercase().contains(needle) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(dim.ChannelGridColumns),
                horizontalArrangement = Arrangement.spacedBy(dim.CardSpacing),
                verticalArrangement = Arrangement.spacedBy(dim.CardSpacing)
            ) {
                items(filteredChannels) { ch ->
                    val cat = cats.items.firstOrNull { it.id == selectedCat }
                    val locked = (cat?.isAdult == true) && !parental.isUnlocked()
                    val now = ch.epgChannelId?.let { epgNow[it] }
                    val nowProgress = now?.let {
                        val span = (it.stopMs - it.startMs).coerceAtLeast(1)
                        ((System.currentTimeMillis() - it.startMs).toFloat() / span)
                            .coerceIn(0f, 1f)
                    }
                    ChannelCard(
                        title = ch.name,
                        number = ch.num,
                        logoUrl = ch.logoUrl,
                        locked = locked,
                        nowPlaying = now?.title,
                        nowProgress = nowProgress
                    ) {
                        if (locked) {
                            pendingChannel = PlayerArgs(
                                kind = PlayerKind.LIVE,
                                streamId = ch.id,
                                title = ch.name,
                                containerExtension = null
                            )
                        } else {
                            onOpenChannel(ch)
                        }
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
            onCancel = { pendingCategory = null },
            onPinCreated = { vm.setParentalPin(it) }
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
            onCancel = { pendingChannel = null },
            onPinCreated = { vm.setParentalPin(it) }
        )
    }
}
