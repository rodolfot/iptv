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
import com.iptv.app.R
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.notify.NotificationChannels
import com.iptv.app.notify.Notifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Pings the user once a week if they have unfinished movies/episodes that
 * haven't been touched in a while. Silent until the user actually has
 * dormant progress, so it doesn't spam fresh installs.
 */
@HiltWorker
class ResumeReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val movieProgress: MovieProgressDao,
    private val seriesProgress: SeriesProgressDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() - DORMANT_THRESHOLD_MS
        val staleMovies = runCatching { movieProgress.observeInProgress().first() }
            .getOrDefault(emptyList())
            .count { it.updatedAt in 1..cutoff }
        val staleSeries = runCatching { seriesProgress.observeRecent().first() }
            .getOrDefault(emptyList())
            .count { it.updatedAt in 1..cutoff }
        if (staleMovies + staleSeries == 0) return Result.success()

        val ctx = applicationContext
        Notifications.show(
            ctx, NotificationChannels.RESUME, NOTIF_ID,
            ctx.getString(R.string.notif_resume_title),
            ctx.getString(R.string.notif_resume_text)
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "resume_reminder"
        private const val NOTIF_ID = 2001
        private val DORMANT_THRESHOLD_MS = TimeUnit.DAYS.toMillis(3)

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<ResumeReminderWorker>(
                repeatInterval = 7, repeatIntervalTimeUnit = TimeUnit.DAYS,
                flexTimeInterval = 6, flexTimeIntervalUnit = TimeUnit.HOURS
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
