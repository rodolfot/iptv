package com.iptv.app.data.stalker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skeleton of a Stalker (Ministra) Portal client. Stalker portals use a
 * different protocol than Xtream: a handshake call returns a session token
 * that subsequent calls echo back via the `Authorization: Bearer` header
 * along with a fixed `Cookie: mac=AA:BB:..` identifying the virtual STB.
 *
 * This file implements only the **handshake** so the surrounding code can
 * gate on whether the portal is reachable. Catalog/category/stream calls
 * land in follow-up work, which is why [StalkerRepository] (the consumer)
 * is currently marked WIP and not registered with the catalog pipeline.
 *
 * Reference: a portal URL looks like `http://host/portal.php` (or
 * `/stalker_portal/server/load.php`). Real-world deployments vary, so the
 * handshake takes the *base* host and probes both endpoints.
 */
@Singleton
class StalkerClient @Inject constructor(
    private val http: OkHttpClient
) {
    data class Session(
        val portalUrl: String,
        val token: String,
        val mac: String
    )

    /**
     * Try `portal.php` then `stalker_portal/server/load.php` until one returns
     * a handshake payload with a token. Returns null when neither answers.
     */
    suspend fun handshake(host: String, mac: String): Session? = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().trimEnd('/')
        val candidates = listOf(
            "$cleanHost/portal.php",
            "$cleanHost/stalker_portal/server/load.php"
        )
        for (portal in candidates) {
            val token = runCatching { handshakeAt(portal, mac) }.getOrNull()
            if (!token.isNullOrBlank()) return@withContext Session(portal, token, mac)
        }
        null
    }

    private fun handshakeAt(portalUrl: String, mac: String): String? {
        val url = "$portalUrl?type=stb&action=handshake&token=&JsHttpRequest=1-xml"
        val req = Request.Builder()
            .url(url)
            .header("Cookie", "mac=$mac; stb_lang=en; timezone=UTC")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return json.optJSONObject("js")?.optString("token")?.takeIf { it.isNotBlank() }
        }
    }

    companion object {
        // Sticky UA: portals reject obvious bot/browser strings.
        private const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2"
    }
}
