package com.iptv.app.update

import com.iptv.app.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val releasePageUrl: String
)

@Singleton
class UpdateChecker @Inject constructor(
    private val http: OkHttpClient
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(GhRelease::class.java)

    /** Returns non-null when the GitHub release is newer than the installed build. */
    suspend fun check(): UpdateInfo? = runCatching {
        val owner = BuildConfig.UPDATE_REPO_OWNER
        val repo = BuildConfig.UPDATE_REPO_NAME
        if (owner.isBlank() || repo.isBlank()) return@runCatching null
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) return@runCatching null
        val body = resp.body?.string() ?: return@runCatching null
        val release = adapter.fromJson(body) ?: return@runCatching null
        val tag = release.tag_name?.removePrefix("v")?.trim() ?: return@runCatching null

        // Tag must look like 1.2.3 or 1.2.3-N (the trailing N maps to a versionCode bump).
        val parts = tag.split("-", limit = 2)
        val name = parts[0]
        val tagCode = parts.getOrNull(1)?.toIntOrNull() ?: name.split(".").let { v ->
            (v.getOrNull(0)?.toIntOrNull() ?: 0) * 10000 +
                (v.getOrNull(1)?.toIntOrNull() ?: 0) * 100 +
                (v.getOrNull(2)?.toIntOrNull() ?: 0)
        }
        if (tagCode <= BuildConfig.VERSION_CODE) return@runCatching null

        val apkAsset = release.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
            ?: return@runCatching null
        UpdateInfo(
            versionName = name,
            versionCode = tagCode,
            notes = release.body.orEmpty(),
            apkUrl = apkAsset.browser_download_url ?: return@runCatching null,
            releasePageUrl = release.html_url ?: ""
        )
    }.getOrNull()
}

@JsonClass(generateAdapter = true)
internal data class GhRelease(
    val tag_name: String? = null,
    val name: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GhAsset>? = null
)

@JsonClass(generateAdapter = true)
internal data class GhAsset(
    val name: String? = null,
    val browser_download_url: String? = null
)
