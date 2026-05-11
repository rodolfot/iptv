package com.iptv.app.data.prefs

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stored Xtream account. The PIN is per-profile so families can isolate
 * adults/kids without needing app-wide settings, while host/user/pass let
 * the same install switch between IPTV providers without re-entering them.
 */
/**
 * Which playlist backend a profile uses. Xtream is the original API
 * (categories + live + movies + series + EPG). M3U is a flat list of
 * channels — only `host` is used (as the URL to the .m3u/.m3u8 playlist)
 * and only live channels become available.
 */
enum class ProviderType { XTREAM, M3U }

data class Profile(
    val id: String,
    val name: String,
    val host: String,
    val username: String,
    val password: String,
    val pin: String? = null,
    val lastUsedAt: Long = 0L,
    /**
     * When true the profile uses Kids mode: only allow-listed categories are
     * visible, search and adult content are hidden, and the UI hides the
     * Settings tab so a child can't switch profiles or change credentials.
     */
    val kids: Boolean = false,
    /**
     * Category ids (live + movies + series, prefixed by content kind to keep
     * them unique across types: "live:42", "movie:7", "series:9") that the
     * Kids profile is allowed to see. Empty set means "no allow-list yet" —
     * the parent must pick categories before the profile shows anything.
     */
    val allowedCategories: Set<String> = emptySet(),
    val provider: ProviderType = ProviderType.XTREAM
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("user", username)
        put("pass", password)
        if (pin != null) put("pin", pin)
        if (lastUsedAt > 0L) put("lastUsedAt", lastUsedAt)
        if (kids) put("kids", true)
        if (allowedCategories.isNotEmpty()) put("allowedCategories", allowedCategories.joinToString(","))
        if (provider != ProviderType.XTREAM) put("provider", provider.name)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(o: JSONObject): Profile = Profile(
            id = o.optString("id").ifBlank { newId() },
            name = o.optString("name", "Sem nome"),
            host = o.optString("host"),
            username = o.optString("user"),
            password = o.optString("pass"),
            pin = if (o.has("pin")) o.optString("pin").ifBlank { null } else null,
            lastUsedAt = o.optLong("lastUsedAt", 0L),
            kids = o.optBoolean("kids", false),
            allowedCategories = o.optString("allowedCategories", "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            provider = runCatching { ProviderType.valueOf(o.optString("provider", "XTREAM")) }
                .getOrDefault(ProviderType.XTREAM)
        )

        fun listToJson(profiles: List<Profile>): String {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String?): List<Profile> {
            if (json.isNullOrBlank()) return emptyList()
            val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }
    }
}
