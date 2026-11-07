package com.iptv.app.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Diário de eventos do app para diagnóstico remoto (Configurações → Sobre →
 * Enviar log). O logcat do sistema gira em minutos em centrais multimídia
 * movimentadas; este arquivo guarda o que importa para reproduzir um
 * problema — ciclo de vida da tela, estado do player, motivo de pausa (foco
 * de áudio, câmera de ré…) e falhas — e sobrevive a reinícios do app.
 *
 * Tudo também vai para o logcat com a tag [TAG]. Escrita numa thread própria,
 * arquivo limitado a [MAX_BYTES] (o mais antigo vira `.old`).
 */
object DiagLog {
    const val TAG = "TartaDiag"
    private const val MAX_BYTES = 384 * 1024L

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "diag-log").apply { isDaemon = true } }
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var file: File? = null

    /** Falhas fatais continuam no [CrashLog]; o envio junta os dois. */
    fun init(context: Context) {
        file = File(context.filesDir, "diag.log")
    }

    fun i(event: String) {
        Log.i(TAG, event)
        val line = "${stamp.format(Date())} $event"
        io.execute { runCatching { append(line) } }
    }

    fun e(event: String, error: Throwable? = null) {
        Log.w(TAG, event, error)
        val line = "${stamp.format(Date())} ERRO $event" + (error?.let { ": ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        io.execute { runCatching { append(line) } }
    }

    /** Conteúdo atual (anterior + atual), do mais antigo ao mais novo. */
    fun read(): String {
        val f = file ?: return ""
        val old = File(f.path + ".old")
        return buildString {
            if (old.exists()) append(old.readText())
            if (f.exists()) append(f.readText())
        }
    }

    @Synchronized
    private fun append(line: String) {
        val f = file ?: return
        if (f.length() > MAX_BYTES) {
            val old = File(f.path + ".old")
            old.delete()
            f.renameTo(old)
        }
        f.appendText(line + "\n")
    }
}
