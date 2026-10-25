package com.iptv.app.update

import android.util.Log
import com.iptv.app.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val releasePageUrl: String
)

/** Resultado de uma checagem — distingue "sem novidade" de "não deu pra checar"
 *  para a UI dar o feedback certo (antes as duas coisas viravam `null`). */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

@Singleton
class UpdateChecker @Inject constructor(
    private val http: OkHttpClient
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(GhRelease::class.java)

    /** Versão mais nova publicada no GitHub, ou `null` quando já está na última
     *  (ou quando a checagem falhou — use [checkDetailed] para distinguir). */
    suspend fun check(): UpdateInfo? = (checkDetailed() as? UpdateCheckResult.Available)?.info

    /**
     * Consulta a última release no GitHub e compara com a versão instalada.
     *
     * Roda em [Dispatchers.IO]: o diálogo de atualização chamava isto a partir do
     * viewModelScope (thread principal), o `execute()` síncrono do OkHttp
     * estourava NetworkOnMainThreadException e o `runCatching` engolia o erro —
     * o aviso de nova versão nunca aparecia dentro do app.
     */
    suspend fun checkDetailed(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val owner = BuildConfig.UPDATE_REPO_OWNER
        val repo = BuildConfig.UPDATE_REPO_NAME
        if (owner.isBlank() || repo.isBlank()) {
            return@withContext UpdateCheckResult.Failed("repositório de update não configurado")
        }
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GitHub respondeu HTTP ${resp.code}")
                    return@withContext UpdateCheckResult.Failed("HTTP ${resp.code}")
                }
                val body = resp.body?.string()
                    ?: return@withContext UpdateCheckResult.Failed("resposta vazia")
                val release = adapter.fromJson(body)
                    ?: return@withContext UpdateCheckResult.Failed("JSON inválido")
                if (release.draft || release.prerelease) return@withContext UpdateCheckResult.UpToDate
                val remote = release.tag_name?.removePrefix("v")?.trim()
                    ?: return@withContext UpdateCheckResult.Failed("release sem tag")
                // Compara pelo versionName (SemVer da tag). Antes comparava um
                // código derivado da tag (3.1.0 → 30100) com o versionCode, que no
                // CI é o número de commits — as duas escalas não têm relação.
                if (!AppVersion.isNewer(remote, BuildConfig.VERSION_NAME)) {
                    return@withContext UpdateCheckResult.UpToDate
                }
                val apk = release.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                    ?.browser_download_url
                    ?: return@withContext UpdateCheckResult.Failed("release sem APK")
                UpdateCheckResult.Available(
                    UpdateInfo(
                        versionName = AppVersion.core(remote),
                        notes = release.body.orEmpty(),
                        apkUrl = apk,
                        releasePageUrl = release.html_url.orEmpty()
                    )
                )
            }
        } catch (e: Exception) {
            // Rede fora, DNS, certificado inválido (TVs com relógio errado)…
            Log.w(TAG, "Falha ao checar atualização", e)
            UpdateCheckResult.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }
    }

    private companion object {
        const val TAG = "UpdateChecker"
    }
}

/** Comparação de versões no formato da tag (`4.0.0`, `v4.0.0`) e do
 *  versionName gerado pelo `git describe` em builds locais (`4.0.0-3-gabc123-dirty`). */
object AppVersion {
    /** Só o núcleo numérico `X.Y.Z` (descarta prefixo `v` e sufixos). */
    fun core(version: String): String =
        Regex("""\d+(\.\d+)*""").find(version.removePrefix("v"))?.value ?: version

    private fun parts(version: String): List<Int> =
        core(version).split('.').map { it.toIntOrNull() ?: 0 }

    /** True quando [candidate] é estritamente maior que [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
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
