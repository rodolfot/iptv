package com.iptv.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.iptv.app.MainActivity
import com.iptv.app.R

object NotificationChannels {
    const val NEW_EPISODES = "new_episodes"
    const val CATALOG = "catalog"
    const val RESUME = "resume"
    const val ERRORS = "errors"
    const val UPDATES = "updates"
}

object Notifications {

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(
                NotificationChannels.NEW_EPISODES,
                context.getString(R.string.notif_channel_new_episodes),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            NotificationChannel(
                NotificationChannels.CATALOG,
                context.getString(R.string.notif_channel_catalog),
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                NotificationChannels.RESUME,
                context.getString(R.string.notif_channel_resume),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            NotificationChannel(
                NotificationChannels.ERRORS,
                context.getString(R.string.notif_channel_errors),
                NotificationManager.IMPORTANCE_HIGH
            ),
            NotificationChannel(
                NotificationChannels.UPDATES,
                context.getString(R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_HIGH
            ),
        ).forEach(mgr::createNotificationChannel)
    }

    fun show(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        text: String,
        /** Ação ao tocar na notificação. Null = abre o app (padrão). */
        contentIntent: PendingIntent? = null
    ) {
        // API 33+ exige a permissão POST_NOTIFICATIONS concedida em runtime —
        // sem essa checagem explícita, o notify() abaixo pode lançar
        // SecurityException se o usuário tiver negado a permissão.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val pi = contentIntent ?: launchIntent(context)
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notif)
        }
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}
