package com.iptv.app.data.epg

import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.CacheMetaDao
import com.iptv.app.data.db.CacheMetaEntity
import com.iptv.app.data.db.EpgDao
import com.iptv.app.data.db.EpgProgrammeEntity
import com.iptv.app.data.db.LiveCacheDao
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val xtream: XtreamRepository,
    private val liveCache: LiveCacheDao,
    private val epgDao: EpgDao,
    private val cacheMeta: CacheMetaDao,
    private val http: OkHttpClient
) {

    suspend fun isStale(): Boolean {
        val ts = cacheMeta.getUpdatedAt(SCOPE) ?: return true
        return System.currentTimeMillis() - ts > TTL_MS
    }

    suspend fun refresh(): Result<Int> = runCatching {
        val now = System.currentTimeMillis()
        val windowEnd = now + 36 * 3600_000L
        val windowStart = now - 60_000L

        // Limit parsing to channels we actually display (saves memory and DB rows).
        val knownIds = liveCache.observeAll().first()
            .mapNotNull { it.epgChannelId }
            .filter { it.isNotBlank() }
            .toSet()
        // If we don't know any channel yet (first run before catalog refresh) bail
        // gracefully — the next worker cycle will retry once the live cache is populated.
        if (knownIds.isEmpty()) return@runCatching 0

        val url = xtream.epgUrl()
        val req = Request.Builder().url(url).build()
        val response = http.newCall(req).execute()
        if (!response.isSuccessful) error("EPG HTTP ${response.code}")
        val body = response.body ?: error("EPG empty body")

        val collected = ArrayList<EpgProgrammeEntity>(8000)
        body.byteStream().use { input ->
            XmltvParser.parse(
                input = input,
                windowStartMs = windowStart,
                windowEndMs = windowEnd,
                knownChannelIds = knownIds
            ) { p -> collected.add(p) }
        }
        epgDao.replaceAll(now, collected)
        epgDao.deleteExpired(windowStart)
        cacheMeta.upsert(CacheMetaEntity(SCOPE, now))
        collected.size
    }

    suspend fun currentForChannels(channelIds: List<String>): Map<String, EpgProgrammeEntity> {
        if (channelIds.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        return epgDao.getCurrentForChannels(channelIds, now).associateBy { it.channelId }
    }

    suspend fun upcoming(channelId: String): List<EpgProgrammeEntity> =
        epgDao.getUpcoming(channelId, System.currentTimeMillis())

    companion object {
        private const val SCOPE = "epg"
        private const val TTL_MS = 6L * 60 * 60 * 1000
    }
}
