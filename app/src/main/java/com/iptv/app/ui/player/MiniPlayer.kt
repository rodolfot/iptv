package com.iptv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.iptv.app.R
import kotlinx.coroutines.delay

/**
 * Persistent strip docked at the bottom of the app while playback is minimized.
 * Tapping the strip reopens the full PlayerScreen; the play/pause and close
 * controls operate directly on the shared ExoPlayer.
 */
@Composable
fun MiniPlayer(
    holder: ActivePlaybackHolder,
    onExpand: () -> Unit
) {
    val player = holder.player ?: return
    val args = holder.args.value ?: return

    var isPlaying by remember { mutableStateOf(player.isPlaying) }

    // ExoPlayer doesn't notify isPlaying via Compose state; poll cheaply while visible.
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            delay(500)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onExpand)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!args.posterUrl.isNullOrBlank()) {
                AsyncImage(model = args.posterUrl, contentDescription = null)
            } else {
                Icon(Icons.Filled.Movie, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                args.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                stringResource(
                    when (args.kind) {
                        PlayerKind.LIVE -> R.string.content_type_live
                        PlayerKind.MOVIE -> R.string.content_type_movie
                        PlayerKind.EPISODE -> R.string.content_type_episode
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = {
            if (player.isPlaying) player.pause() else player.play()
        }) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null
            )
        }
        IconButton(onClick = { holder.release() }) {
            Icon(Icons.Filled.Close, contentDescription = null)
        }
    }
}
