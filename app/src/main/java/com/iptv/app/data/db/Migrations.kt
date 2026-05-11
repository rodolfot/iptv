package com.iptv.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 → 2: original v1 shipped only `favorites` and `episode_progress`.
 * Adds `movie_progress` and `series_progress` so installed users keep
 * their favorites and episode positions when upgrading.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movie_progress (
                movieId INTEGER NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                posterUrl TEXT,
                containerExtension TEXT,
                categoryId TEXT,
                positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                watched INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series_progress (
                seriesId INTEGER NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                coverUrl TEXT,
                lastEpisodeId TEXT NOT NULL,
                lastSeasonNumber INTEGER NOT NULL,
                lastEpisodeNum INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * 2 → 3: introduce catalog cache tables plus FTS4 mirrors and a `cache_meta`
 * book-keeping table for TTLs.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cache_meta (
                scope TEXT NOT NULL PRIMARY KEY,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS category_cache (
                type TEXT NOT NULL,
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                parentId INTEGER NOT NULL,
                isAdult INTEGER NOT NULL,
                sortKey TEXT NOT NULL,
                PRIMARY KEY(type, id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS live_cache (
                streamId INTEGER NOT NULL PRIMARY KEY,
                num INTEGER,
                name TEXT NOT NULL,
                logoUrl TEXT,
                categoryId TEXT,
                epgChannelId TEXT,
                addedTimestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movie_cache (
                streamId INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                posterUrl TEXT,
                rating REAL NOT NULL,
                containerExtension TEXT,
                categoryId TEXT,
                addedTimestamp INTEGER NOT NULL,
                releaseDate TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series_cache (
                seriesId INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                coverUrl TEXT,
                rating REAL NOT NULL,
                plot TEXT,
                cast TEXT,
                genre TEXT,
                categoryId TEXT,
                releaseDate TEXT,
                lastModifiedTimestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS live_fts USING FTS4(`name` TEXT NOT NULL)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS movie_fts USING FTS4(`name` TEXT NOT NULL)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS series_fts USING FTS4(`name` TEXT NOT NULL)")
    }
}

/**
 * 3 → 4: EPG storage. Composite key on (channelId, startMs) plus an index
 * matching the runtime lookup pattern.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS epg_programme (
                channelId TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                stopMs INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                PRIMARY KEY(channelId, startMs)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_epg_programme_channelId_startMs_stopMs " +
                "ON epg_programme(channelId, startMs, stopMs)"
        )
    }
}

/**
 * 4 → 5: extend FTS tables for movies and series with an `extra` column carrying
 * genre/cast/plot/release-date so search hits beyond the title.
 * SQLite FTS4 cannot ALTER, so we drop and recreate; data will be repopulated
 * by the next catalog refresh.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS movie_fts")
        db.execSQL("DROP TABLE IF EXISTS series_fts")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS movie_fts USING FTS4(`name` TEXT NOT NULL, `extra` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS series_fts USING FTS4(`name` TEXT NOT NULL, `extra` TEXT NOT NULL)"
        )
        // Force the next refresh to repopulate the FTS rows.
        db.execSQL("DELETE FROM cache_meta WHERE scope IN ('movie_streams','series_list')")
    }
}

/**
 * 5 → 6: detail metadata cache for offline access (vodInfo, seriesInfo as JSON blobs).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS detail_cache (
                kind TEXT NOT NULL,
                id INTEGER NOT NULL,
                payload TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(kind, id)
            )
            """.trimIndent()
        )
        // Track tv_archive flag on cached channels for time-shift support.
        db.execSQL("ALTER TABLE live_cache ADD COLUMN tvArchive INTEGER NOT NULL DEFAULT 0")
        // Force re-fetch so the field gets populated with real values.
        db.execSQL("DELETE FROM cache_meta WHERE scope = 'live_streams'")
    }
}

/**
 * 6 → 7: introduce watchlist table mirroring favorites.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watchlist (
                type TEXT NOT NULL,
                itemId INTEGER NOT NULL,
                name TEXT NOT NULL,
                logoUrl TEXT,
                categoryId TEXT,
                containerExtension TEXT,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(type, itemId)
            )
            """.trimIndent()
        )
    }
}

/**
 * 7 → 8: introduce `profileId` column in user-data tables and rebuild composite
 * primary keys around it. Existing rows are tagged with the sentinel "default"
 * so they remain visible until the user picks a profile and exports/migrates.
 *
 * SQLite cannot ALTER PRIMARY KEY in place, so we use the canonical
 * create-new-copy-rename trick for each table.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // favorites
        db.execSQL("""
            CREATE TABLE favorites_new (
                profileId TEXT NOT NULL,
                type TEXT NOT NULL,
                itemId INTEGER NOT NULL,
                name TEXT NOT NULL,
                logoUrl TEXT,
                categoryId TEXT,
                containerExtension TEXT,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, type, itemId)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO favorites_new (profileId, type, itemId, name, logoUrl, categoryId, containerExtension, addedAt)
            SELECT 'default', type, itemId, name, logoUrl, categoryId, containerExtension, addedAt FROM favorites
        """.trimIndent())
        db.execSQL("DROP TABLE favorites")
        db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")

        // watchlist
        db.execSQL("""
            CREATE TABLE watchlist_new (
                profileId TEXT NOT NULL,
                type TEXT NOT NULL,
                itemId INTEGER NOT NULL,
                name TEXT NOT NULL,
                logoUrl TEXT,
                categoryId TEXT,
                containerExtension TEXT,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, type, itemId)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO watchlist_new (profileId, type, itemId, name, logoUrl, categoryId, containerExtension, addedAt)
            SELECT 'default', type, itemId, name, logoUrl, categoryId, containerExtension, addedAt FROM watchlist
        """.trimIndent())
        db.execSQL("DROP TABLE watchlist")
        db.execSQL("ALTER TABLE watchlist_new RENAME TO watchlist")

        // episode_progress
        db.execSQL("""
            CREATE TABLE episode_progress_new (
                profileId TEXT NOT NULL,
                episodeId TEXT NOT NULL,
                seriesId INTEGER NOT NULL,
                seasonNumber INTEGER NOT NULL,
                episodeNum INTEGER NOT NULL,
                positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                watched INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, episodeId)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO episode_progress_new (profileId, episodeId, seriesId, seasonNumber, episodeNum, positionMs, durationMs, watched, updatedAt)
            SELECT 'default', episodeId, seriesId, seasonNumber, episodeNum, positionMs, durationMs, watched, updatedAt FROM episode_progress
        """.trimIndent())
        db.execSQL("DROP TABLE episode_progress")
        db.execSQL("ALTER TABLE episode_progress_new RENAME TO episode_progress")

        // movie_progress
        db.execSQL("""
            CREATE TABLE movie_progress_new (
                profileId TEXT NOT NULL,
                movieId INTEGER NOT NULL,
                title TEXT NOT NULL,
                posterUrl TEXT,
                containerExtension TEXT,
                categoryId TEXT,
                positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                watched INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, movieId)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO movie_progress_new (profileId, movieId, title, posterUrl, containerExtension, categoryId, positionMs, durationMs, watched, updatedAt)
            SELECT 'default', movieId, title, posterUrl, containerExtension, categoryId, positionMs, durationMs, watched, updatedAt FROM movie_progress
        """.trimIndent())
        db.execSQL("DROP TABLE movie_progress")
        db.execSQL("ALTER TABLE movie_progress_new RENAME TO movie_progress")

        // series_progress
        db.execSQL("""
            CREATE TABLE series_progress_new (
                profileId TEXT NOT NULL,
                seriesId INTEGER NOT NULL,
                title TEXT NOT NULL,
                coverUrl TEXT,
                lastEpisodeId TEXT NOT NULL,
                lastSeasonNumber INTEGER NOT NULL,
                lastEpisodeNum INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, seriesId)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO series_progress_new (profileId, seriesId, title, coverUrl, lastEpisodeId, lastSeasonNumber, lastEpisodeNum, updatedAt)
            SELECT 'default', seriesId, title, coverUrl, lastEpisodeId, lastSeasonNumber, lastEpisodeNum, updatedAt FROM series_progress
        """.trimIndent())
        db.execSQL("DROP TABLE series_progress")
        db.execSQL("ALTER TABLE series_progress_new RENAME TO series_progress")
    }
}

/**
 * 8 → 9: add `streamUrl` to `live_cache` so M3U-imported channels can carry
 * the concrete playlist URL alongside the synthetic streamId.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE live_cache ADD COLUMN streamUrl TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9
)
