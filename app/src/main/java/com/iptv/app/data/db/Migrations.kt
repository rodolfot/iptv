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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
