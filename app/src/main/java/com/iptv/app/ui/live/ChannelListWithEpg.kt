@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil.compose.AsyncImage
import com.iptv.app.data.db.EpgProgrammeEntity
import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vertical list of channels with a side panel showing the EPG of the focused
 * channel. UX:
 *  - Move the focus through the list -> the right panel updates with the
 *    schedule of the highlighted channel (current programme + upcoming).
 *  - Press OK once -> the row becomes "armed" (still no playback) so the user
 *    can read the EPG.
 *  - Press OK again on the same row -> opens the player.
 *  - Pressing OK on a different row arms that row instead.
 */
@Composable
fun ChannelListWithEpg(
    vm: HomeViewModel,
    channels: List<LiveChannel>,
    epgNow: Map<String, EpgProgrammeEntity>,
    isCategoryAdult: Boolean,
    isParentalUnlocked: Boolean,
    onPlay: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    var focusedIndex by remember(channels) { mutableStateOf(0) }
    var armedChannelId by remember(channels) { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            // Restore focus to the top of the list when the dataset changes
            // (e.g. user typed in the filter).
            runCatching { firstFocus.requestFocus() }
        }
    }

    val focusedChannel = channels.getOrNull(focusedIndex)

    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 12.dp, bottom = 12.dp),
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            items(items = channels, key = { it.id }) { channel ->
                val index = channels.indexOf(channel)
                val locked = isCategoryAdult && !isParentalUnlocked
                val now = channel.epgChannelId?.let { epgNow[it] }
                ChannelRow(
                    channel = channel,
                    nowPlaying = now?.title,
                    nowProgress = now?.let {
                        val span = (it.stopMs - it.startMs).coerceAtLeast(1)
                        ((System.currentTimeMillis() - it.startMs).toFloat() / span)
                            .coerceIn(0f, 1f)
                    },
                    locked = locked,
                    armed = armedChannelId == channel.id,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) focusedIndex = index },
                    onClick = {
                        if (armedChannelId == channel.id) {
                            onPlay(channel)
                        } else {
                            armedChannelId = channel.id
                        }
                    }
                )
            }
        }

        EpgPanel(
            channel = focusedChannel,
            vm = vm,
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    nowPlaying: String?,
    nowProgress: Float?,
    locked: Boolean,
    armed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = modifier.fillMaxWidth().height(76.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null)
                } else {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel.num != null) {
                        Text(
                            "${channel.num}  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (armed) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (!nowPlaying.isNullOrBlank()) {
                    Text(
                        nowPlaying,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (nowProgress != null && nowProgress in 0f..1f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(nowProgress)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .height(2.dp)
                            )
                        }
                    }
                }
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EpgPanel(
    channel: LiveChannel?,
    vm: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val schedule by produceState<List<EpgProgrammeEntity>>(initialValue = emptyList(), channel?.id) {
        value = if (channel == null) emptyList() else vm.epgScheduleFor(channel.epgChannelId)
    }

    Box(
        modifier = modifier
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        if (channel == null) {
            Text(
                "Sem canal selecionado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Box
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            channel.num?.let {
                Text(
                    "Canal $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(modifier = Modifier.height(12.dp))
            if (schedule.isEmpty()) {
                Text(
                    "Sem programação disponível",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(schedule.take(20)) { p ->
                    EpgEntry(
                        programme = p,
                        isCurrent = isLive(p)
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgEntry(programme: EpgProgrammeEntity, isCurrent: Boolean) {
    val time = remember(programme.startMs, programme.stopMs) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        "${fmt.format(Date(programme.startMs))} – ${fmt.format(Date(programme.stopMs))}"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            programme.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!programme.description.isNullOrBlank()) {
            Text(
                programme.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun isLive(p: EpgProgrammeEntity): Boolean {
    val now = System.currentTimeMillis()
    return now in p.startMs..p.stopMs
}
