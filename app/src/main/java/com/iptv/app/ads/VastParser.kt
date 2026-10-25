package com.iptv.app.ads

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** Anúncio em vídeo pronto para tocar, extraído de uma resposta VAST. */
data class VastAd(
    val mediaUrl: String,
    val durationMs: Long,
    /** Quando o botão "Pular" aparece; null = não pulável. */
    val skipOffsetMs: Long?,
    val clickThrough: String?,
    val impressions: List<String>,
    /** Evento VAST (start, firstQuartile, midpoint, thirdQuartile, complete,
     *  skip, error…) → URLs de rastreamento. */
    val tracking: Map<String, List<String>>
)

/** Resultado do parse de um documento VAST: um anúncio InLine, um Wrapper a
 *  seguir, ou nada (VAST vazio = sem anúncio para esta requisição). */
sealed interface VastDocument {
    data class InLine(val ad: VastAd) : VastDocument
    data class Wrapper(
        val nextUrl: String,
        val impressions: List<String>,
        val tracking: Map<String, List<String>>
    ) : VastDocument
    data object Empty : VastDocument
}

/**
 * Parser VAST 2.0–4.x mínimo, sem SDK proprietário (o app é distribuído também
 * fora da Play Store e não depende de Google Play Services). Cobre o que o
 * player precisa: MediaFile MP4 progressivo, duração, skipoffset, Impression,
 * TrackingEvents, ClickThrough e Wrapper (VASTAdTagURI).
 */
object VastParser {

    fun parse(xml: String): VastDocument {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        var isWrapper = false
        var wrapperUrl: String? = null
        var duration: Long = 0
        var skipRaw: String? = null
        var clickThrough: String? = null
        val impressions = mutableListOf<String>()
        val tracking = linkedMapOf<String, MutableList<String>>()
        val media = mutableListOf<MediaCandidate>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Wrapper" -> isWrapper = true
                    "Linear" -> skipRaw = parser.getAttributeValue(null, "skipoffset")
                    "Duration" -> duration = parseTime(parser.nextText().trim()) ?: 0
                    "Impression" -> parser.nextText().trim().takeIf { it.isNotEmpty() }?.let(impressions::add)
                    "Tracking" -> {
                        val name = parser.getAttributeValue(null, "event").orEmpty()
                        val url = parser.nextText().trim()
                        if (name.isNotEmpty() && url.isNotEmpty()) tracking.getOrPut(name) { mutableListOf() } += url
                    }
                    "ClickThrough" -> clickThrough = parser.nextText().trim().ifEmpty { null }
                    "VASTAdTagURI" -> wrapperUrl = parser.nextText().trim()
                    "MediaFile" -> {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val delivery = parser.getAttributeValue(null, "delivery").orEmpty()
                        val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                        val bitrate = parser.getAttributeValue(null, "bitrate")?.toIntOrNull() ?: 0
                        val url = parser.nextText().trim()
                        if (url.isNotEmpty()) media += MediaCandidate(url, type, delivery, height, bitrate)
                    }
                }
            }
            event = parser.next()
        }

        if (isWrapper) {
            val next = wrapperUrl ?: return VastDocument.Empty
            return VastDocument.Wrapper(next, impressions, tracking)
        }
        val chosen = chooseMedia(media) ?: return VastDocument.Empty
        return VastDocument.InLine(
            VastAd(
                mediaUrl = chosen.url,
                durationMs = duration,
                // skipoffset em % depende da <Duration>, que vem depois do <Linear>.
                skipOffsetMs = skipRaw?.let { parseOffset(it, duration) },
                clickThrough = clickThrough,
                impressions = impressions,
                tracking = tracking
            )
        )
    }

    private data class MediaCandidate(
        val url: String,
        val type: String,
        val delivery: String,
        val height: Int,
        val bitrate: Int
    )

    /** MP4 progressivo de até 720p (TVs de entrada engasgam com 1080p de alto
     *  bitrate em anúncio curto); na falta, qualquer vídeo progressivo. */
    private fun chooseMedia(all: List<MediaCandidate>): MediaCandidate? {
        val playable = all.filter {
            it.delivery != "streaming" && (it.type.isEmpty() || it.type.startsWith("video/"))
        }
        val mp4 = playable.filter { it.type == "video/mp4" || it.url.substringBefore('?').endsWith(".mp4") }
        val pool = mp4.ifEmpty { playable }
        return pool.filter { it.height in 1..720 }.maxByOrNull { it.bitrate }
            ?: pool.minByOrNull { if (it.height > 0) it.height else Int.MAX_VALUE }
    }

    /** `HH:MM:SS(.mmm)` → ms. */
    fun parseTime(value: String): Long? {
        val parts = value.split(':')
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toDoubleOrNull() ?: return null
        return (h * 3600 + m * 60) * 1000 + (s * 1000).toLong()
    }

    /** skipoffset: `HH:MM:SS(.mmm)` ou `NN%` da duração. */
    private fun parseOffset(value: String, durationMs: Long): Long? {
        val v = value.trim()
        if (v.endsWith("%")) {
            val pct = v.dropLast(1).toDoubleOrNull() ?: return null
            return if (durationMs > 0) (durationMs * pct / 100).toLong() else null
        }
        return parseTime(v)
    }
}
