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
import com.iptv.app.data.prefs.RefreshInterval
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

        /**
         * Schedule periodic refresh based on the user-chosen interval.
         * @param replace true when called after a settings change so the schedule updates immediately.
         */
        fun schedule(context: Context, interval: RefreshInterval, replace: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            // WorkManager periodic minimum is 15 min; longer intervals are passed through directly.
            val (repeat, unit) = repeatFor(interval)
            val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(
                repeatInterval = repeat, repeatIntervalTimeUnit = unit,
                flexTimeInterval = 1, flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun repeatFor(interval: RefreshInterval): Pair<Long, TimeUnit> = when (interval) {
            RefreshInterval.HOURS_1 -> 1L to TimeUnit.HOURS
            RefreshInterval.HOURS_4 -> 4L to TimeUnit.HOURS
            RefreshInterval.HOURS_12 -> 12L to TimeUnit.HOURS
            RefreshInterval.DAYS_1 -> 1L to TimeUnit.DAYS
            RefreshInterval.DAYS_4 -> 4L to TimeUnit.DAYS
            RefreshInterval.DAYS_7 -> 7L to TimeUnit.DAYS
            RefreshInterval.DAYS_30 -> 30L to TimeUnit.DAYS
        }
    }
}
