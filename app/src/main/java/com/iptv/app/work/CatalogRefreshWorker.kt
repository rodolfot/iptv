package com.iptv.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iptv.app.data.cache.CatalogCacheRepository
import com.iptv.app.data.epg.EpgRepository
import com.iptv.app.data.prefs.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class CatalogRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cache: CatalogCacheRepository,
    private val epg: EpgRepository,
    private val settings: SettingsStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Skip if there is no logged-in session — credentials are required for the API.
        val s = settings.flow.first()
        if (!s.isLoggedIn) return Result.success()

        val errors = cache.refreshAll().toMutableList()
        epg.refresh().exceptionOrNull()?.let(errors::add)
        return when {
            errors.isEmpty() -> Result.success()
            // Partial failure: retry with backoff up to a few times before giving up.
            runAttemptCount < 3 -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "catalog_refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(
                repeatInterval = 6, repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 1, flexTimeIntervalUnit = TimeUnit.HOURS
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
