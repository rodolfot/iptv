package com.iptv.app.data.prefs

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stored Xtream account. The PIN is per-profile so families can isolate
 * adults/kids without needing app-wide settings, while host/user/pass let
 * the same install switch between IPTV providers without re-entering them.
 */
data class Profile(
    val id: String,
    val name: String,
    val host: String,
    val username: String,
    val password: String,
    val pin: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("user", username)
        put("pass", password)
        if (pin != null) put("pin", pin)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(o: JSONObject): Profile = Profile(
            id = o.optString("id").ifBlank { newId() },
            name = o.optString("name", "Sem nome"),
            host = o.optString("host"),
            username = o.optString("user"),
            password = o.optString("pass"),
            pin = if (o.has("pin")) o.optString("pin").ifBlank { null } else null
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
