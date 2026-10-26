@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.audio.AudioCapabilities

/** Família do codec de áudio, para badges e decisão de passthrough. */
enum class AudioCodecFamily(val label: String) {
    DOLBY_ATMOS("Dolby Atmos"),
    DOLBY_TRUEHD("Dolby TrueHD"),
    DOLBY_DIGITAL_PLUS("Dolby Digital Plus"),
    DOLBY_DIGITAL("Dolby Digital"),
    DTS_X("DTS:X"),
    DTS_HD("DTS-HD"),
    DTS_EXPRESS("DTS Express"),
    DTS("DTS"),
    OTHER("")
}

/**
 * O que a UI precisa saber do áudio em reprodução: codec imersivo (badge
 * "Dolby Atmos"/"DTS"), canais e se está indo em bitstream para o receiver.
 */
data class AudioBadge(
    val family: AudioCodecFamily,
    /** "5.1", "7.1"… ou null para estéreo/mono/desconhecido. */
    val channels: String?,
    /** True quando a saída atual (HDMI/óptica) aceita o formato em bitstream e
     *  o passthrough está ligado — o receiver decodifica. */
    val passthrough: Boolean
) {
    val isImmersive: Boolean get() = family != AudioCodecFamily.OTHER

    /** Texto do badge: "Dolby Atmos · 7.1 · Passthrough". */
    fun text(passthroughLabel: String): String =
        listOfNotNull(family.label.ifBlank { null }, channels, passthroughLabel.takeIf { passthrough })
            .joinToString(" · ")
}

object ImmersiveAudio {

    /** Identifica a família pelo MIME que o extrator/parser do Media3 atribuiu
     *  — inclusive E-AC-3 JOC (Atmos) sinalizado em HLS por `CHANNELS="16/JOC"`
     *  e em TS/MKV pelo próprio bitstream. */
    fun familyOf(format: Format): AudioCodecFamily = when (format.sampleMimeType) {
        MimeTypes.AUDIO_E_AC3_JOC -> AudioCodecFamily.DOLBY_ATMOS
        MimeTypes.AUDIO_TRUEHD -> AudioCodecFamily.DOLBY_TRUEHD
        MimeTypes.AUDIO_E_AC3 -> AudioCodecFamily.DOLBY_DIGITAL_PLUS
        MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_AC4 -> AudioCodecFamily.DOLBY_DIGITAL
        MimeTypes.AUDIO_DTS_X -> AudioCodecFamily.DTS_X
        MimeTypes.AUDIO_DTS_HD -> AudioCodecFamily.DTS_HD
        MimeTypes.AUDIO_DTS_EXPRESS -> AudioCodecFamily.DTS_EXPRESS
        MimeTypes.AUDIO_DTS -> AudioCodecFamily.DTS
        else -> AudioCodecFamily.OTHER
    }

    fun channelsLabel(count: Int): String? = when (count) {
        Format.NO_VALUE, 1, 2 -> null
        3 -> "2.1"
        6 -> "5.1"
        8 -> "7.1"
        else -> if (count > 8) "$count ch" else null
    }

    /** Encoding de AudioTrack correspondente, para consultar o suporte da saída. */
    private fun encodingOf(format: Format): Int = when (familyOf(format)) {
        AudioCodecFamily.DOLBY_ATMOS -> C.ENCODING_E_AC3_JOC
        AudioCodecFamily.DOLBY_TRUEHD -> C.ENCODING_DOLBY_TRUEHD
        AudioCodecFamily.DOLBY_DIGITAL_PLUS -> C.ENCODING_E_AC3
        AudioCodecFamily.DOLBY_DIGITAL ->
            if (format.sampleMimeType == MimeTypes.AUDIO_AC4) C.ENCODING_AC4 else C.ENCODING_AC3
        AudioCodecFamily.DTS_X -> C.ENCODING_DTS_UHD_P2
        AudioCodecFamily.DTS_HD -> C.ENCODING_DTS_HD
        AudioCodecFamily.DTS_EXPRESS, AudioCodecFamily.DTS -> C.ENCODING_DTS
        AudioCodecFamily.OTHER -> C.ENCODING_INVALID
    }

    /** True quando a saída de áudio atual aceita o formato em bitstream. */
    @Suppress("DEPRECATION")
    fun outputSupportsBitstream(context: Context, format: Format): Boolean {
        val encoding = encodingOf(format)
        if (encoding == C.ENCODING_INVALID) return false
        return runCatching {
            AudioCapabilities.getCapabilities(context).supportsEncoding(encoding)
        }.getOrDefault(false)
    }

    /** Família do codec da faixa de áudio selecionada. */
    fun selectedFamily(tracks: Tracks): AudioCodecFamily? = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { familyOf(g.getTrackFormat(it)) } }

    /** Badge da faixa de áudio selecionada, ou null se não houver áudio. */
    fun selectedBadge(context: Context, tracks: Tracks, passthroughEnabled: Boolean): AudioBadge? {
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
            ?: return null
        val family = familyOf(format)
        return AudioBadge(
            family = family,
            channels = channelsLabel(format.channelCount),
            passthrough = passthroughEnabled && family != AudioCodecFamily.OTHER &&
                outputSupportsBitstream(context, format)
        )
    }

    /**
     * True quando o conteúdo tem faixas de áudio mas nenhuma pode ser tocada
     * neste aparelho (sem decoder e sem passthrough — típico de DTS em celular
     * ou TV sem licença). O ExoPlayer, nesse caso, seleciona a faixa mesmo assim
     * e o vídeo roda mudo; a UI usa isto para avisar e oferecer o player
     * externo em vez de deixar o usuário sem som e sem explicação.
     */
    fun hasOnlyUnsupportedAudio(tracks: Tracks): Boolean {
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audio.isEmpty()) return false
        return audio.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }
    }

    /** Nome do codec da primeira faixa de áudio (para a mensagem de aviso). */
    fun firstAudioCodecLabel(tracks: Tracks): String? = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        ?.getTrackFormat(0)
        ?.let { familyOf(it).label.ifBlank { it.sampleMimeType } }
}
