package com.iptv.app.diag

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import com.iptv.app.BuildConfig
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.premium.LicenseManager
import com.iptv.app.ui.premium.portalBaseUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Junta o diário do app ([DiagLog]) e o logcat do próprio processo, tira
 * usuário, senha e servidor da lista, e envia ao portal. O portal devolve um
 * código curto (ex.: K7M2QX) que o usuário passa ao suporte; o log aparece em
 * /admin/diagnosticos.
 */
@Singleton
class DiagnosticsUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val license: LicenseManager,
    http: OkHttpClient
) {
    private val client = http.newBuilder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed interface Result {
        data class Sent(val code: String) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun send(note: String? = null): Result = withContext(Dispatchers.IO) {
        runCatching {
            DiagLog.i("Envio de log solicitado")
            val s = settings.flow.first()
            val secrets = listOf(s.password, s.username, s.host.substringAfter("://").substringBefore('/'))
                .filter { it.length >= 3 }
            val raw = buildString {
                CrashLog.read(context).takeIf { it.isNotBlank() }?.let {
                    appendLine("===== Falhas (crash) =====")
                    appendLine(it)
                }
                appendLine("===== Diário do app =====")
                appendLine(DiagLog.read())
                appendLine("===== Logcat do processo =====")
                appendLine(processLogcat())
            }
            val body = JSONObject()
                .put("deviceId", license.state.value.deviceId.ifBlank { null })
                .put("appVersion", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                .put("device", deviceSummary(s.deviceProfile?.name))
                .put("log", redact(raw, secrets).takeLast(MAX_LOG_CHARS))
            if (!note.isNullOrBlank()) body.put("note", note.take(500))
            val request = Request.Builder()
                .url("${portalBaseUrl()}/api/app/v1/diagnostics")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    return@runCatching Result.Failed(msg?.ifBlank { null } ?: "HTTP ${res.code}")
                }
                Result.Sent(JSONObject(text).getString("code"))
            }
        }.getOrElse {
            DiagLog.e("Falha ao enviar log", it)
            Result.Failed(it.message ?: it.javaClass.simpleName)
        }
    }

    private fun processLogcat(): String = runCatching {
        val proc = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "-t", "4000", "--pid=${Process.myPid()}"
        ).redirectErrorStream(true).start()
        // `logcat -d` termina sozinho ao chegar ao fim do buffer; ler até o
        // EOF já espera o processo (waitFor com timeout é só API 26+).
        val out = proc.inputStream.bufferedReader().readText()
        proc.destroy()
        out
    }.getOrElse { "(logcat indisponível: ${it.message})" }

    private fun deviceSummary(profile: String?): String {
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        val type = when (uiMode) {
            Configuration.UI_MODE_TYPE_CAR -> "carro"
            Configuration.UI_MODE_TYPE_TELEVISION -> "tv"
            else -> "normal"
        }
        val sw = context.resources.configuration.smallestScreenWidthDp
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
            " · uiMode=$type · perfil=${profile ?: "auto"} · sw=${sw}dp"
    }

    companion object {
        private const val MAX_LOG_CHARS = 1_400_000

        private val PATTERNS = listOf(
            Regex("""(?i)(username|password|user|pass|token)=([^&\s"']+)""") to "$1=***",
            Regex("""/(live|movie|series|timeshift)/[^/\s]+/[^/\s]+/""") to "/$1/***/***/",
        )

        /** Remove credenciais da lista e parâmetros sensíveis de URLs. */
        fun redact(text: String, secrets: List<String>): String {
            var out = text
            PATTERNS.forEach { (re, rep) -> out = re.replace(out, rep) }
            secrets.forEach { out = out.replace(it, "***") }
            return out
        }
    }
}
