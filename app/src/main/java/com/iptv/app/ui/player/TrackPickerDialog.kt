@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.iptv.app.R
import java.util.Locale

/**
 * Seletor de faixas do conteúdo em reprodução: áudio (idioma — ex.: trocar
 * português ↔ inglês ↔ espanhol), legenda e qualidade de vídeo. Aberto pelas
 * linhas "Áudio"/"Legenda" do painel de opções do player.
 *
 * [tracks] deve vir do `onTracksChanged` do player — assim a faixa marcada
 * como selecionada atualiza na hora em que o usuário escolhe outra.
 */
@Composable
fun TrackPickerDialog(
    player: ExoPlayer,
    tracks: Tracks,
    onDismiss: () -> Unit
) {
    val groupsByType = remember(tracks) {
        tracks.groups.groupBy { it.type }
    }
    // Foco inicial na seção de áudio (o motivo mais comum de abrir o seletor).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        runCatching { firstFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.player_tracks),
                    style = MaterialTheme.typography.titleLarge
                )

                val audioGroups = groupsByType[C.TRACK_TYPE_AUDIO]
                if (audioGroups.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.player_audio) + ": —",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    TrackSection(
                        label = stringResource(R.string.player_audio),
                        groups = audioGroups,
                        player = player,
                        fallbackName = stringResource(R.string.player_audio),
                        formatLabel = ::audioLabel,
                        firstFocus = firstFocus
                    )
                }
                groupsByType[C.TRACK_TYPE_TEXT]?.let { groups ->
                    TrackSection(
                        label = stringResource(R.string.player_subtitle),
                        groups = groups,
                        player = player,
                        fallbackName = stringResource(R.string.player_subtitle),
                        formatLabel = ::subtitleLabel,
                        offType = C.TRACK_TYPE_TEXT
                    )
                }
                groupsByType[C.TRACK_TYPE_VIDEO]?.let { groups ->
                    // Só vale mostrar qualidade quando há mais de uma opção.
                    if (groups.sumOf { g -> (0 until g.length).count { g.isTrackSupported(it) } } > 1) {
                        TrackSection(
                            label = stringResource(R.string.player_video),
                            groups = groups,
                            player = player,
                            fallbackName = stringResource(R.string.player_video),
                            formatLabel = ::videoLabel
                        )
                    }
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

@Composable
private fun TrackSection(
    label: String,
    groups: List<Tracks.Group>,
    player: ExoPlayer,
    fallbackName: String,
    formatLabel: (Format) -> String?,
    offType: Int? = null,
    firstFocus: FocusRequester? = null
) {
    val type = groups.first().type
    val anySelected = groups.any { it.isSelected }
    Text(label, style = MaterialTheme.typography.titleSmall)

    TouchableButton(
        selected = offType != null && !anySelected,
        compact = true,
        onClick = {
            val builder = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, offType != null)
            // "Automático" no áudio volta a seguir o idioma padrão do conteúdo.
            if (type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguages()
            player.trackSelectionParameters = builder.build()
        }
    ) {
        Text(stringResource(if (offType != null) R.string.player_track_off else R.string.player_track_auto))
    }

    var position = 0
    groups.forEach { group: Tracks.Group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            position++
            val format = group.getTrackFormat(i)
            val selected = group.isTrackSelected(i)
            val name = formatLabel(format) ?: "$fallbackName $position"
            TouchableButton(
                selected = selected,
                compact = true,
                modifier = if (position == 1 && firstFocus != null) {
                    Modifier.focusRequester(firstFocus)
                } else Modifier,
                onClick = {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, i)
                    val builder = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(group.type, false)
                        .setOverrideForType(override)
                    // Guarda o idioma escolhido como preferido: o próximo
                    // episódio/canal já abre no mesmo idioma quando existir.
                    if (group.type == C.TRACK_TYPE_AUDIO && !format.language.isNullOrBlank()) {
                        builder.setPreferredAudioLanguage(format.language)
                    }
                    player.trackSelectionParameters = builder.build()
                }
            ) {
                Text(name)
            }
        }
    }
}

/** Rótulo da faixa em uso de um tipo (áudio/legenda), ou null se nenhuma. */
fun selectedTrackLabel(tracks: Tracks, type: Int): String? {
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (i in 0 until group.length) {
            if (group.isTrackSelected(i)) {
                val format = group.getTrackFormat(i)
                return when (type) {
                    C.TRACK_TYPE_AUDIO -> audioLabel(format)
                    C.TRACK_TYPE_TEXT -> subtitleLabel(format)
                    else -> videoLabel(format)
                } ?: (i + 1).toString()
            }
        }
    }
    return null
}

/** Nome do idioma no idioma do app ("en" → "Inglês"); null se indefinido. */
private fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == C.LANGUAGE_UNDETERMINED) return null
    val display = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
    if (display.isBlank() || display.equals(code, ignoreCase = true)) return code.uppercase()
    return display.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun videoLabel(f: Format): String? {
    val res = if (f.height > 0) "${f.height}p" else f.label ?: return null
    val br = if (f.bitrate > 0) " · ${f.bitrate / 1000} kbps" else ""
    return "$res$br"
}

private fun audioLabel(f: Format): String? {
    val name = f.label?.takeIf { it.isNotBlank() } ?: languageName(f.language) ?: return null
    val channels = when (f.channelCount) {
        1 -> " · Mono"
        2 -> " · 2.0"
        6 -> " · 5.1"
        8 -> " · 7.1"
        else -> ""
    }
    return "$name$channels"
}

private fun subtitleLabel(f: Format): String? =
    f.label?.takeIf { it.isNotBlank() } ?: languageName(f.language)
