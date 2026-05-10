package com.iptv.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.iptv.app.ui.common.TouchableButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.iptv.app.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrackPickerDialog(
    player: ExoPlayer,
    tracks: Tracks,
    onDismiss: () -> Unit
) {
    val groupsByType = remember(tracks) {
        tracks.groups.groupBy { it.type }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.player_tracks),
                    style = MaterialTheme.typography.titleLarge
                )

                groupsByType[C.TRACK_TYPE_VIDEO]?.let { groups ->
                    TrackSection(
                        label = stringResource(R.string.player_video),
                        groups = groups,
                        player = player,
                        formatLabel = ::videoLabel
                    )
                }
                groupsByType[C.TRACK_TYPE_AUDIO]?.let { groups ->
                    TrackSection(
                        label = stringResource(R.string.player_audio),
                        groups = groups,
                        player = player,
                        formatLabel = ::audioLabel
                    )
                }
                groupsByType[C.TRACK_TYPE_TEXT]?.let { groups ->
                    TrackSection(
                        label = stringResource(R.string.player_subtitle),
                        groups = groups,
                        player = player,
                        formatLabel = ::subtitleLabel,
                        offType = C.TRACK_TYPE_TEXT
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TouchableButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackSection(
    label: String,
    groups: List<Tracks.Group>,
    player: ExoPlayer,
    formatLabel: (Format) -> String,
    offType: Int? = null
) {
    Text(label, style = MaterialTheme.typography.titleMedium)

    TouchableButton(onClick = {
        val params = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(groups.first().type)
            .setTrackTypeDisabled(groups.first().type, offType != null)
            .build()
        player.trackSelectionParameters = params
    }) {
        Text(stringResource(if (offType != null) R.string.player_track_off else R.string.player_track_auto))
    }

    groups.forEach { group: Tracks.Group ->
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val selected = group.isTrackSelected(i)
            val supported = group.isTrackSupported(i)
            if (!supported) continue
            TouchableButton(
                onClick = {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, i)
                    val params = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(group.type, false)
                        .setOverrideForType(override)
                        .build()
                    player.trackSelectionParameters = params
                }
            ) {
                Text((if (selected) "• " else "") + formatLabel(format))
            }
        }
    }
}

private fun videoLabel(f: Format): String {
    val res = if (f.height > 0) "${f.height}p" else f.label ?: "video"
    val br = if (f.bitrate > 0) " · ${f.bitrate / 1000} kbps" else ""
    return "$res$br"
}

private fun audioLabel(f: Format): String {
    val lang = f.language?.uppercase() ?: f.label ?: "audio"
    val ch = if (f.channelCount > 0) " · ${f.channelCount}ch" else ""
    return "$lang$ch"
}

private fun subtitleLabel(f: Format): String =
    f.language?.uppercase() ?: f.label ?: "sub"

private fun TrackGroup.toLogString(): String = "TrackGroup(len=$length)"
