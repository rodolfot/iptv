package com.iptv.app.diag

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only, opt-in crash log. Never leaves the device unless the user copies it. */
object CrashLog {
    private const val FILE_NAME = "crash.log"
    private const val MAX_SIZE_BYTES = 256 * 1024L

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                append(context, formatCrash(thread, error))
            } catch (_: Throwable) {
                // Never let logging break the crash path itself.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String {
        val f = file(context)
        return if (f.exists()) f.readText() else ""
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun append(context: Context, entry: String) {
        val f = file(context)
        if (f.exists() && f.length() > MAX_SIZE_BYTES) {
            // Keep the newest half on overflow.
            val tail = f.readText().takeLast((MAX_SIZE_BYTES / 2).toInt())
            f.writeText(tail)
        }
        f.appendText(entry + "\n\n")
    }

    private fun formatCrash(thread: Thread, error: Throwable): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return buildString {
            append("--- crash @ $ts ---\n")
            append("device: ${Build.MANUFACTURER} ${Build.MODEL} (api ${Build.VERSION.SDK_INT})\n")
            append("thread: ${thread.name}\n")
            append(sw.toString())
        }
    }
}
