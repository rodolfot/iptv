@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.iptv.app.ui.player

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.iptv.app.data.prefs.DecoderMode
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

/** Mesmo User-Agent das chamadas da API Xtream (AppModule) — alguns provedores
 *  seguram ou recusam streams pedidos com o UA genérico "Dalvik/…" do
 *  HttpURLConnection, que era o que o ExoPlayer mandava por padrão. */
private const val STREAM_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

/**
 * Cliente HTTP único para todos os players (tela cheia e prévias).
 *
 * Separado do cliente da API de propósito: aquele tem `callTimeout` de 120s,
 * que derrubaria qualquer canal ao vivo depois de 2 minutos. Aqui não há
 * callTimeout — só connect/read, curtos o bastante para uma conexão presa num
 * servidor de borda lento virar erro (e reconexão) em segundos, em vez de
 * deixar o canal "carregando" indefinidamente.
 *
 * Compartilhar a instância reaproveita o pool de conexões e as threads do
 * OkHttp entre trocas de canal, em vez de cada player criar os seus.
 */
private val streamingHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // OkHttp segue redirects http↔https por padrão; o DefaultHttpDataSource
        // do ExoPlayer não seguia, e o canal falhava quando o painel Xtream
        // redirecionava para um servidor de streaming em outro protocolo.
        .followSslRedirects(true)
        .build()
}

/**
 * Buffers do player principal. Os defaults do ExoPlayer esperam 2,5s de mídia
 * antes de começar e deixam o buffer crescer até ~130 MB de heap — em TVs com
 * pouca RAM isso pesava no GC e deixava o app lento com o tempo.
 */
private fun mainLoadControl(): LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 15_000,
        /* maxBufferMs = */ 50_000,
        /* bufferForPlaybackMs = */ 1_000,
        /* bufferForPlaybackAfterRebufferMs = */ 2_500
    )
    .setTargetBufferBytes(32 * 1024 * 1024)
    .build()

/** Prévias (painel/PIP) só precisam de alguns segundos de buffer. */
private fun previewLoadControl(): LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 4_000,
        /* maxBufferMs = */ 12_000,
        /* bufferForPlaybackMs = */ 1_000,
        /* bufferForPlaybackAfterRebufferMs = */ 2_000
    )
    .setTargetBufferBytes(8 * 1024 * 1024)
    .build()

/**
 * Cria um ExoPlayer configurado para streaming IPTV. Usa sempre o
 * applicationContext: o player vive no [ActivePlaybackHolder] e não pode
 * segurar a Activity (que é recriada a cada troca de configuração).
 */
fun newStreamingPlayer(
    context: Context,
    renderersFactory: RenderersFactory? = null,
    preview: Boolean = false
): ExoPlayer {
    val appContext = context.applicationContext
    val dataSourceFactory = DefaultDataSource.Factory(
        appContext,
        OkHttpDataSource.Factory(streamingHttpClient).setUserAgent(STREAM_USER_AGENT)
    )
    val builder = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setLoadControl(if (preview) previewLoadControl() else mainLoadControl())
    if (renderersFactory != null) builder.setRenderersFactory(renderersFactory)
    return builder.build().apply {
        if (preview) {
            // Prévia é pequena na tela: limita a SD quando o stream é adaptativo
            // (HLS com várias qualidades) para poupar banda e decoder.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSizeSd()
                .build()
        }
    }
}
