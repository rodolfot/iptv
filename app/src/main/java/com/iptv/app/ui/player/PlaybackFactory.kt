@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.iptv.app.data.prefs.DecoderMode

/**
 * Constrói a [RenderersFactory] do ExoPlayer respeitando a escolha de decoder:
 *
 *  - [DecoderMode.AUTO]: comportamento padrão do ExoPlayer (prefere hardware,
 *    cai para software se preciso).
 *  - [DecoderMode.HARDWARE]: força decoders de hardware — útil quando o software
 *    engasga em 4K/HDR.
 *  - [DecoderMode.SOFTWARE]: força decoders de software — saída para formatos
 *    HEVC/AV1 que o hardware do aparelho não suporta (o erro "formato não
 *    suportado" some na maioria dos casos).
 *
 * Quando o filtro deixaria a lista vazia (ex.: forçar HW num formato que só tem
 * decoder de software), cai de volta para a lista completa para não falhar a
 * reprodução à toa.
 */
fun buildRenderersFactory(context: Context, mode: DecoderMode): RenderersFactory =
    DefaultRenderersFactory(context).apply {
        setEnableDecoderFallback(true)
        if (mode != DecoderMode.AUTO) {
            setMediaCodecSelector(decoderModeSelector(mode))
        }
    }

private fun decoderModeSelector(mode: DecoderMode): MediaCodecSelector =
    MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val all: List<MediaCodecInfo> = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType, requiresSecureDecoder, requiresTunnelingDecoder
        )
        val filtered = when (mode) {
            DecoderMode.SOFTWARE -> all.filter { it.softwareOnly }
            DecoderMode.HARDWARE -> all.filter { it.hardwareAccelerated }
            DecoderMode.AUTO -> all
        }
        filtered.ifEmpty { all }
    }
