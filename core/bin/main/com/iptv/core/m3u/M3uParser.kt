package com.iptv.core.m3u

/**
 * Minimal `#EXTM3U` playlist parser scoped to what we ingest for live channels.
 *
 * Each track is two consecutive lines:
 *
 *     #EXTINF:-1 tvg-id="cnn.us" tvg-logo="https://..." group-title="News",CNN
 *     http://server/stream/123.m3u8
 *
 * We honour `tvg-id`, `tvg-logo` and `group-title`; everything else is ignored
 * to keep the parser easy to audit and predictable across providers.
 *
 * Lives in :core because nothing here touches Android — just String/Regex.
 * That keeps its unit tests Android-free and lets any module import it
 * without paying the cost of pulling in app frameworks.
 */
data class M3uTrack(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val epgChannelId: String? = null,
    val groupTitle: String? = null
)

object M3uParser {

    fun parse(content: String): List<M3uTrack> {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty() || !lines.first().startsWith("#EXTM3U")) return emptyList()

        val out = mutableListOf<M3uTrack>()
        var i = 1
        while (i < lines.size) {
            val header = lines[i]
            if (!header.startsWith("#EXTINF")) { i++; continue }
            // First non-comment line after #EXTINF is the URL.
            var j = i + 1
            while (j < lines.size && lines[j].startsWith("#")) j++
            if (j >= lines.size) break
            val url = lines[j]
            parseExtinf(header, url)?.let(out::add)
            i = j + 1
        }
        return out
    }

    private fun parseExtinf(header: String, url: String): M3uTrack? {
        // The line shape is: #EXTINF:<duration> <key="value" ...>,<display name>
        // Display name is everything after the last comma on the line.
        val commaIdx = header.lastIndexOf(',')
        if (commaIdx < 0) return null
        val displayName = header.substring(commaIdx + 1).trim().ifEmpty { return null }
        val attrs = header.substring(0, commaIdx)
        val attributes = ATTR_REGEX.findAll(attrs).associate { it.groupValues[1] to it.groupValues[2] }
        return M3uTrack(
            name = displayName,
            url = url,
            logoUrl = attributes["tvg-logo"]?.takeIf { it.isNotBlank() },
            epgChannelId = attributes["tvg-id"]?.takeIf { it.isNotBlank() },
            groupTitle = attributes["group-title"]?.takeIf { it.isNotBlank() }
        )
    }

    private val ATTR_REGEX = Regex("""(\w[\w-]*)="([^"]*)"""")
}
