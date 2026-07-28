package com.iptv.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baixa o APK de uma release e monta o Intent que aciona o instalador do
 * sistema — a parte que efetivamente falta pro fluxo de update deixar de
 * depender de navegador/pendrive/ADB. Android nunca deixa pular a
 * confirmação final de instalação (sem privilégio de sistema/device owner),
 * então o "sozinho se atualiza" vai até baixar o arquivo e abrir essa tela;
 * o toque de confirmação é do usuário.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    private val http: OkHttpClient
) {
    /** Baixa para o cache privado do app (sem precisar de permissão de storage). */
    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit = {}): File {
        val file = File(context.cacheDir, "update.apk")
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download HTTP ${response.code}")
            val body = response.body ?: error("Download sem corpo")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (total > 0) onProgress(((readTotal * 100) / total).toInt())
                    }
                }
            }
        }
        return file
    }

    /** Falso quando o usuário ainda precisa liberar "instalar apps desconhecidos" pra este app (API 26+). */
    fun canRequestInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Tela do sistema onde o usuário libera a instalação de fontes desconhecidas pra este app.
     *  Só é chamada quando [canRequestInstall] já deu false, o que só acontece a partir da API 26
     *  (lint não enxerga essa garantia através do booleano, daí o suppress). */
    @Suppress("InlinedApi")
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Abre o instalador do sistema para o APK já baixado. */
    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Ação da notificação "atualização pronta" — toca pra abrir o instalador direto. */
    fun installPendingIntent(context: Context, apkFile: File): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, INSTALL_REQUEST_CODE, installIntent(context, apkFile), flags)
    }

    private companion object {
        const val INSTALL_REQUEST_CODE = 4001
    }
}
