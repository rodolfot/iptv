package com.iptv.app.data.epg

import com.iptv.app.data.db.EpgProgrammeEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Streaming XMLTV parser. Memory-bounded: never holds the whole document.
 * Filters programmes to a moving window so the cache stays small.
 */
object XmltvParser {

    private val FORMATS = listOf(
        "yyyyMMddHHmmss Z",
        "yyyyMMddHHmmss",
        "yyyyMMddHHmm Z",
        "yyyyMMddHHmm"
    ).map { SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }

    fun parse(
        input: InputStream,
        windowStartMs: Long,
        windowEndMs: Long,
        knownChannelIds: Set<String>,
        emit: (EpgProgrammeEntity) -> Unit
    ) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser: XmlPullParser = factory.newPullParser()
        parser.setInput(input, null)

        var event = parser.eventType
        var currentChannel: String? = null
        var currentStart: Long = 0
        var currentStop: Long = 0
        var currentTitle: String? = null
        var currentDesc: String? = null
        var inProgramme = false
        var inTitle = false
        var inDesc = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        inProgramme = true
                        currentChannel = parser.getAttributeValue(null, "channel")
                        currentStart = parseTime(parser.getAttributeValue(null, "start"))
                        currentStop = parseTime(parser.getAttributeValue(null, "stop"))
                        currentTitle = null
                        currentDesc = null
                    }
                    "title" -> if (inProgramme) inTitle = true
                    "desc" -> if (inProgramme) inDesc = true
                }
                XmlPullParser.TEXT -> when {
                    inTitle -> currentTitle = (currentTitle ?: "") + parser.text
                    inDesc -> currentDesc = (currentDesc ?: "") + parser.text
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> inTitle = false
                    "desc" -> inDesc = false
                    "programme" -> {
                        val ch = currentChannel
                        if (
                            inProgramme &&
                            !ch.isNullOrBlank() &&
                            (knownChannelIds.isEmpty() || ch in knownChannelIds) &&
                            currentStart > 0 &&
                            currentStop > currentStart &&
                            currentStop >= windowStartMs &&
                            currentStart <= windowEndMs &&
                            !currentTitle.isNullOrBlank()
                        ) {
                            emit(
                                EpgProgrammeEntity(
                                    channelId = ch,
                                    startMs = currentStart,
                                    stopMs = currentStop,
                                    title = currentTitle!!.trim(),
                                    description = currentDesc?.trim()
                                )
                            )
                        }
                        inProgramme = false
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        for (fmt in FORMATS) {
            try {
                return fmt.parse(raw)?.time ?: continue
            } catch (_: Exception) {
                // try next format
            }
        }
        return 0L
    }
}
