package com.iptv.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iptv.app.R
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.notify.NotificationChannels
import com.iptv.app.notify.Notifications
import com.iptv.app.update.UpdateChecker
import com.iptv.app.update.UpdateInstaller
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Verifica periodicamente se há uma release mais nova no GitHub e, se houver,
 * já baixa o APK em segundo plano e notifica — instalar a atualização vira
 * "publicar a release + um toque na notificação", sem depender de
 * navegador, pendrive ou ADB. Android não permite pular a confirmação final
 * de instalação sem privilégio de sistema, então esse toque continua sendo
 * necessário.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val settings: SettingsStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val update = checker.check()
            ?: return Result.success(Data.Builder().putBoolean(OUTPUT_FOUND, false).build())

        // O ciclo periódico não repete a notificação pra mesma versão. (O
        // "Verificar agora" das Configurações não passa mais por aqui — abre o
        // diálogo de instalação dentro do app.)
        if (settings.lastNotifiedUpdateVersion() == update.versionName) {
            return Result.success(
                Data.Builder().putBoolean(OUTPUT_FOUND, true).putString(OUTPUT_VERSION, update.versionName).build()
            )
        }

        val apk = runCatching { installer.download(applicationContext, update.apkUrl) }
            .getOrElse {
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

        settings.setLastNotifiedUpdateVersion(update.versionName)
        val ctx = applicationContext
        Notifications.show(
            ctx, NotificationChannels.UPDATES, NOTIF_ID,
            ctx.getString(R.string.notif_update_title),
            ctx.getString(R.string.notif_update_text, update.versionName),
            contentIntent = installer.installPendingIntent(ctx, apk)
        )
        return Result.success(
            Data.Builder().putBoolean(OUTPUT_FOUND, true).putString(OUTPUT_VERSION, update.versionName).build()
        )
    }

    companion object {
        private const val UNIQUE_NAME = "update_check"
        private const val NOTIF_ID = 3001

        const val OUTPUT_FOUND = "found"
        const val OUTPUT_VERSION = "version"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                repeatInterval = 12, repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 2, flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
