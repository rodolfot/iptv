package com.iptv.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.diag.CrashLog
import com.iptv.app.notify.Notifications
import com.iptv.app.work.CatalogRefreshWorker
import com.iptv.app.work.ResumeReminderWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class IptvApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Notifications.ensureChannels(this)
        // Locale is applied via AppCompatDelegate inside the Compose tree once
        // SettingsStore emits — doing it in `runBlocking` here was deadlocking
        // because the DataStore IO scheduler hadn't started yet.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val s = settings.flow.first()
            com.iptv.app.ui.common.LocaleManager.apply(s.appLocale)
            CatalogRefreshWorker.schedule(this@IptvApp, s.refreshInterval)
            ResumeReminderWorker.schedule(this@IptvApp)
        }
    }
}
