@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.os.Handler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.iptv.app.R
import com.iptv.app.ui.common.TouchableButton

/**
 * Saída forçada em PCM escolhida no player. Lida pelo AudioSink de
 * [buildRenderersFactory] a cada configuração da faixa de áudio — por isso
 * dá para trocar no meio da reprodução sem recriar o player. Volta ao
 * automático quando o player é liberado.
 */
object AudioOutputOverride {
    @Volatile
    var forcePcm: Boolean = false
}

/** Opção do seletor "Formato de áudio". */
sealed interface AudioFormatChoice {
    /** Melhor faixa e passthrough conforme as Configurações. */
    data object Auto : AudioFormatChoice

    /** Faixa de um codec (Atmos, Dolby Digital, DTS…), em bitstream se a saída aceitar. */
    data class Codec(
        val family: AudioCodecFamily,
        val channels: String?,
        val group: Tracks.Group,
        val trackIndex: Int
    ) : AudioFormatChoice

    /** Decodifica no aparelho (estéreo/multicanal PCM), mesmo com receiver. */
    data object Pcm : AudioFormatChoice
}

object AudioFormatSelector {

    /** Automático, um item por codec imersivo presente no conteúdo e PCM. */
    fun choices(tracks: Tracks): List<AudioFormatChoice> {
        val byFamily = linkedMapOf<AudioCodecFamily, AudioFormatChoice.Codec>()
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val family = ImmersiveAudio.familyOf(format)
                if (family == AudioCodecFamily.OTHER) continue
                val current = byFamily[family]
                // Mais de uma faixa do mesmo codec: fica a de mais canais.
                if (current == null || format.channelCount > current.group.getTrackFormat(current.trackIndex).channelCount) {
                    byFamily[family] = AudioFormatChoice.Codec(
                        family, ImmersiveAudio.channelsLabel(format.channelCount), group, i
                    )
                }
            }
        }
        return listOf(AudioFormatChoice.Auto) + byFamily.values + AudioFormatChoice.Pcm
    }

    /** Opção em uso agora (para o rótulo da linha no painel). */
    fun current(tracks: Tracks, player: Player): AudioFormatChoice {
        if (AudioOutputOverride.forcePcm) return AudioFormatChoice.Pcm
        val hasOverride = player.trackSelectionParameters.overrides.keys.any { it.type == C.TRACK_TYPE_AUDIO }
        if (!hasOverride) return AudioFormatChoice.Auto
        val selected = ImmersiveAudio.selectedFamily(tracks) ?: return AudioFormatChoice.Auto
        return choices(tracks).filterIsInstance<AudioFormatChoice.Codec>()
            .firstOrNull { it.family == selected } ?: AudioFormatChoice.Auto
    }

    /**
     * Aplica a escolha. O renderer de áudio só decide passthrough × PCM ao se
     * configurar, então a faixa de áudio é desligada e religada logo em
     * seguida — um instante sem som, sem perder a posição do vídeo.
     */
    fun apply(player: Player, choice: AudioFormatChoice) {
        AudioOutputOverride.forcePcm = choice is AudioFormatChoice.Pcm
        val builder = player.trackSelectionParameters.buildUpon()
        when (choice) {
            AudioFormatChoice.Auto -> builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            is AudioFormatChoice.Codec -> builder.setOverrideForType(
                TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndex)
            )
            AudioFormatChoice.Pcm -> Unit
        }
        player.trackSelectionParameters = builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        Handler(player.applicationLooper).postDelayed({
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        }, REENABLE_DELAY_MS)
    }

    private const val REENABLE_DELAY_MS = 150L
}

@Composable
fun audioFormatLabel(choice: AudioFormatChoice): String = when (choice) {
    AudioFormatChoice.Auto -> stringResource(R.string.audio_format_auto)
    is AudioFormatChoice.Codec -> listOfNotNull(choice.family.label, choice.channels).joinToString(" · ")
    AudioFormatChoice.Pcm -> stringResource(R.string.audio_format_pcm)
}

/** Lista de formatos do conteúdo atual; a opção em uso vem destacada. */
@Composable
fun AudioFormatDialog(
    choices: List<AudioFormatChoice>,
    current: AudioFormatChoice,
    onSelect: (AudioFormatChoice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_format_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                choices.forEach { choice ->
                    // ✓ em vez do estado "selected" do botão: selecionado + focado
                    // deixava o texto quase invisível no azul.
                    val label = audioFormatLabel(choice)
                    TouchableButton(
                        onClick = { onSelect(choice) },
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (choice == current) "✓  $label" else label, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    stringResource(R.string.audio_format_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}
