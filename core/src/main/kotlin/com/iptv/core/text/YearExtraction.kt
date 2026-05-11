package com.iptv.core.text

/**
 * Pulls a 4-digit year out of release-date strings as Xtream/TMDB return them:
 * "2021-08-15", "2021/08/15", "2021", or sometimes the bare year buried in
 * noise. Anchored to `19xx`/`20xx` so "200" or "2099-rip" don't false-match.
 */
fun parseYear(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""\b(19|20)\d{2}\b""").find(raw)?.value
    return match?.toIntOrNull()
}
