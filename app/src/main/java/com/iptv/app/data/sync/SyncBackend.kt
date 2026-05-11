package com.iptv.app.data.sync

import com.iptv.app.data.db.FavoriteEntity
import com.iptv.app.data.db.MovieProgressEntity
import com.iptv.app.data.db.SeriesProgressEntity
import com.iptv.app.data.db.WatchlistEntity

/**
 * Cross-device sync point of extension. The app is fully usable without it;
 * [NoopSyncBackend] is the default binding so existing installs keep their
 * privacy posture (no network traffic for user data).
 *
 * When a real backend lands (CouchDB / Supabase / WebDAV / etc.), bind a
 * concrete implementation in a Hilt module and the rest of the code starts
 * picking up pushes/pulls without further refactor.
 *
 * Contract:
 *  * Push is fire-and-forget — it should never block UI threads or fail in
 *    a way that breaks the local write.
 *  * Pull returns the remote-side view of the data for the active profile;
 *    the data layer merges it with local state (last-write-wins on
 *    `updatedAt` / `addedAt`).
 *  * All methods take a `profileId` because sync is per-profile.
 */
interface SyncBackend {
    /** True when the backend is configured and reachable. */
    suspend fun isEnabled(): Boolean

    suspend fun pushFavorite(profileId: String, entity: FavoriteEntity) {}
    suspend fun pushWatchlist(profileId: String, entity: WatchlistEntity) {}
    suspend fun pushMovieProgress(profileId: String, entity: MovieProgressEntity) {}
    suspend fun pushSeriesProgress(profileId: String, entity: SeriesProgressEntity) {}

    suspend fun pullFavorites(profileId: String): List<FavoriteEntity> = emptyList()
    suspend fun pullWatchlist(profileId: String): List<WatchlistEntity> = emptyList()
    suspend fun pullMovieProgress(profileId: String): List<MovieProgressEntity> = emptyList()
    suspend fun pullSeriesProgress(profileId: String): List<SeriesProgressEntity> = emptyList()
}

/** Default no-op implementation. Keeps the app offline by design. */
class NoopSyncBackend : SyncBackend {
    override suspend fun isEnabled(): Boolean = false
}
