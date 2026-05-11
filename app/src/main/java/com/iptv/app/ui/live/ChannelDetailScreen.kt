package com.iptv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.iptv.app.R
import com.iptv.app.data.db.EpgProgrammeEntity
import com.iptv.app.data.db.FavoriteDao
import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.epg.EpgRepository
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.domain.model.ContentType
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ChannelDetailState(
    val now: EpgProgrammeEntity? = null,
    val upcoming: List<EpgProgrammeEntity> = emptyList(),
    val loading: Boolean = false,
    val isFavorite: Boolean = false
)

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val epg: EpgRepository,
    private val favoriteDao: FavoriteDao,
    private val currentProfile: CurrentProfile
) : ViewModel() {
    private val _state = MutableStateFlow(ChannelDetailState())
    val state = _state.asStateFlow()

    fun load(channel: LiveChannel) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                isFavorite = isFavorite(currentProfile.id(), channel.id)
            )
            val epgChannelId = channel.epgChannelId
            if (epgChannelId.isNullOrBlank()) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }
            val upcoming = epg.upcoming(epgChannelId)
            val now = upcoming.firstOrNull { p ->
                val t = System.currentTimeMillis()
                p.startMs <= t && p.stopMs > t
            }
            val rest = upcoming.filter { it != now }.take(20)
            _state.value = _state.value.copy(now = now, upcoming = rest, loading = false)
        }
    }

    fun toggleFavorite(channel: LiveChannel) {
        viewModelScope.launch {
            val pid = currentProfile.id()
            val current = _state.value.isFavorite
            if (current) {
                favoriteDao.delete(pid, ContentType.LIVE, channel.id)
            } else {
                favoriteDao.insert(
                    FavoriteEntity(
                        profileId = pid,
                        type = ContentType.LIVE,
                        itemId = channel.id,
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        categoryId = channel.categoryId
                    )
                )
            }
            _state.value = _state.value.copy(isFavorite = !current)
        }
    }

    private suspend fun isFavorite(profileId: String, id: Int): Boolean = try {
        favoriteDao.observeAll(profileId).first()
            .any { it.type == ContentType.LIVE && it.itemId == id }
    } catch (_: Throwable) { false }
}

@Composable
fun ChannelDetailScreen(
    channel: LiveChannel,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    vm: ChannelDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val dim = rememberTvDim()
    val snackbar = LocalSnackbar.current
    val addedMsg = stringResource(R.string.snack_favorite_added)
    val removedMsg = stringResource(R.string.snack_favorite_removed)
    LaunchedEffect(channel.id) { vm.load(channel) }
    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Back moved up to the app top bar.

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null)
                } else {
                    AsyncImage(model = channel.logoUrl, contentDescription = channel.name)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.headlineMedium)
                channel.num?.let {
                    Text("Canal $it", style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TouchableButton(onClick = {
                        onPlay(
                            PlayerArgs(
                                kind = PlayerKind.LIVE,
                                streamId = channel.id,
                                title = channel.name,
                                containerExtension = null
                            )
                        )
                    }) {
                        Text(stringResource(R.string.channel_play))
                    }
                    TouchableButton(onClick = {
                        val wasFavorite = state.isFavorite
                        vm.toggleFavorite(channel)
                        snackbar?.show(if (wasFavorite) removedMsg else addedMsg)
                    }) {
                        Text(stringResource(
                            if (state.isFavorite) R.string.remove_favorite else R.string.add_favorite
                        ))
                    }
                }
                if (channel.tvArchive) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TouchableButton(onClick = {
                            onPlay(timeshiftArgs(channel, minutesAgo = 30))
                        }) { Text(stringResource(R.string.channel_timeshift_30)) }
                        TouchableButton(onClick = {
                            onPlay(timeshiftArgs(channel, minutesAgo = 60))
                        }) { Text(stringResource(R.string.channel_timeshift, 1)) }
                        TouchableButton(onClick = {
                            onPlay(timeshiftArgs(channel, minutesAgo = 120))
                        }) { Text(stringResource(R.string.channel_timeshift, 2)) }
                    }
                }
            }
        }

        if (state.now == null && state.upcoming.isEmpty() && !state.loading) {
            Text(stringResource(R.string.channel_no_epg), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        state.now?.let { now ->
            Text(stringResource(R.string.channel_now), style = MaterialTheme.typography.titleMedium)
            ProgrammeRow(now, isNow = true)
        }
        if (state.upcoming.isNotEmpty()) {
            Text(stringResource(R.string.channel_upcoming), style = MaterialTheme.typography.titleMedium)
            val dayFmt = remember { SimpleDateFormat("EEE, dd/MM", Locale.getDefault()) }
            var lastDay = ""
            state.upcoming.forEach { p ->
                val day = dayFmt.format(Date(p.startMs))
                if (day != lastDay) {
                    Text(day, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                    lastDay = day
                }
                ProgrammeRow(p, isNow = false)
            }
        }
    }
}

private fun timeshiftArgs(channel: LiveChannel, minutesAgo: Int): PlayerArgs =
    PlayerArgs(
        kind = PlayerKind.LIVE,
        streamId = channel.id,
        title = channel.name,
        containerExtension = null,
        timeshiftStartMs = System.currentTimeMillis() - minutesAgo * 60_000L,
        timeshiftDurationMin = (minutesAgo + 60).coerceAtLeast(60)
    )

@Composable
private fun ProgrammeRow(p: EpgProgrammeEntity, isNow: Boolean) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dim = rememberTvDim()
    val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone
    val titleColor = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    val timeText = "${timeFmt.format(Date(p.startMs))}–${timeFmt.format(Date(p.stopMs))}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isPhone) 8.dp else 4.dp)
    ) {
        if (isPhone) {
            // Two-line layout: time on top in a label color, then the full title with
            // wrapping. Avoids ellipsizing program names on narrow screens.
            Text(
                timeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                p.title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor
            )
        } else {
            Text(
                "$timeText  •  ${p.title}",
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
        }
        if (isNow) {
            val span = (p.stopMs - p.startMs).coerceAtLeast(1)
            val progress = ((System.currentTimeMillis() - p.startMs).toFloat() / span)
                .coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .background(MaterialTheme.colorScheme.primary)
                        .height(3.dp)
                )
            }
        }
        p.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
