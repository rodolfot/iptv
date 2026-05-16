package com.iptv.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface XtreamApi {

    @GET
    suspend fun login(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String
    ): LoginResponse

    @GET
    suspend fun liveCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<CategoryDto>

    @GET
    suspend fun liveStreams(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): List<LiveStreamDto>

    @GET
    suspend fun vodCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<CategoryDto>

    @GET
    suspend fun vodStreams(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null
    ): List<VodStreamDto>

    @GET
    suspend fun vodInfo(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: Int
    ): VodInfoResponse

    @GET
    suspend fun seriesCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): List<CategoryDto>

    @GET
    suspend fun series(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: String? = null
    ): List<SeriesDto>

    @GET
    suspend fun seriesInfo(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): SeriesInfoResponse
}

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "user_info") val userInfo: UserInfo? = null,
    @Json(name = "server_info") val serverInfo: ServerInfo? = null
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val username: String? = null,
    val password: String? = null,
    val message: String? = null,
    val auth: Int? = null,
    val status: String? = null,
    @Json(name = "exp_date") val expDate: String? = null,
    @Json(name = "is_trial") val isTrial: String? = null,
    @Json(name = "active_cons") val activeConnections: String? = null,
    @Json(name = "max_connections") val maxConnections: String? = null
)

@JsonClass(generateAdapter = true)
data class ServerInfo(
    val url: String? = null,
    val port: String? = null,
    @Json(name = "https_port") val httpsPort: String? = null,
    @Json(name = "server_protocol") val serverProtocol: String? = null,
    val timezone: String? = null
)

@JsonClass(generateAdapter = true)
data class CategoryDto(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id") val parentId: Int? = 0
)

@JsonClass(generateAdapter = true)
data class LiveStreamDto(
    val num: Int? = null,
    val name: String,
    @Json(name = "stream_type") val streamType: String? = null,
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    val added: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "tv_archive") val tvArchive: Int? = 0,
    @Json(name = "direct_source") val directSource: String? = null
)

@JsonClass(generateAdapter = true)
data class VodStreamDto(
    val num: Int? = null,
    val name: String,
    @Json(name = "stream_type") val streamType: String? = null,
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    val rating: String? = null,
    @Json(name = "rating_5based") val rating5: Double? = null,
    val added: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "releaseDate") val releaseDateAlt: String? = null
)

@JsonClass(generateAdapter = true)
data class VodInfoResponse(
    val info: VodInfoDetail? = null,
    @Json(name = "movie_data") val movieData: VodMovieData? = null
)

@JsonClass(generateAdapter = true)
data class VodInfoDetail(
    @Json(name = "movie_image") val movieImage: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releasedate: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    @Json(name = "duration_secs") val durationSecs: Int? = null,
    @Json(name = "youtube_trailer") val youtubeTrailer: String? = null,
    @Json(name = "backdrop_path") val backdropPath: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class VodMovieData(
    @Json(name = "stream_id") val streamId: Int,
    val name: String,
    val added: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null
)

@JsonClass(generateAdapter = true)
data class SeriesDto(
    val num: Int? = null,
    val name: String,
    @Json(name = "series_id") val seriesId: Int,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "release_date") val releaseDateSnake: String? = null,
    @Json(name = "last_modified") val lastModified: String? = null,
    val rating: String? = null,
    @Json(name = "rating_5based") val rating5: Double? = null,
    @Json(name = "category_id") val categoryId: String? = null
)

/**
 * Provider variance:
 *  - happy path: `episodes` is an object like `{"1": [..], "2": [..]}`
 *  - some providers send `episodes: []` (an array) when there's nothing → we
 *    must NOT fail the whole `seriesInfo` call in that case.
 *  - some send `episodes: {}` (empty object) — fine.
 * We declare `episodes` as `Any?` and normalise in [normalizedEpisodes] so
 * Moshi never throws on the array shape.
 */
@JsonClass(generateAdapter = true)
data class SeriesInfoResponse(
    val seasons: List<SeasonDto>? = null,
    val info: SeriesInfoDetail? = null,
    val episodes: Any? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun normalizedEpisodes(): Map<String, List<EpisodeDto>> {
        val map = episodes as? Map<String, *> ?: return emptyMap()
        // The inner lists arrive as `List<Map<String, Any?>>` because Moshi
        // decodes JSON objects into Maps when the field type is Any. Re-encode
        // each entry through the Moshi adapter to get EpisodeDto back.
        //
        // IMPORTANTE: registramos LooseStringAdapter para que `season` e
        // `episode_num` aceitem tanto Int quanto String — vimos providers que
        // misturam os dois formatos. Sem isso, temporadas inteiras eram
        // descartadas silenciosamente (ex.: The Boys T5 retornava 0 episódios
        // no app mas existia no provedor).
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(LooseStringAdapter())
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val listType = com.squareup.moshi.Types.newParameterizedType(
            List::class.java, EpisodeDto::class.java
        )
        val listAdapter = moshi.adapter<List<EpisodeDto>>(listType).lenient()
        val mapAdapter = moshi.adapter(Any::class.java)
        return map.entries.mapNotNull { (k, v) ->
            val key = k.toString()
            val json = mapAdapter.toJson(v) ?: return@mapNotNull null
            val result = runCatching { listAdapter.fromJson(json) }
            val list = result.getOrNull()
            if (list == null) {
                android.util.Log.w(
                    "XtreamApi",
                    "normalizedEpisodes: season key '$key' falhou parse: ${result.exceptionOrNull()?.message}"
                )
                return@mapNotNull null
            }
            android.util.Log.d("XtreamApi", "normalizedEpisodes: key='$key' -> ${list.size} eps")
            key to list
        }.toMap()
    }
}

/**
 * Aceita um valor JSON como Int, Long, Double ou String e devolve String.
 * Necessário para campos de providers Xtream que oscilam o tipo entre
 * versões (ex.: episode_num, season). Use anotando o field com [LooseString].
 */
@JvmInline
value class LooseString(val value: String)

/** Marca um field para usar o LooseStringAdapter. */
@com.squareup.moshi.JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Loose

class LooseStringAdapter {
    @com.squareup.moshi.FromJson
    @Loose
    fun fromJson(reader: com.squareup.moshi.JsonReader): String? {
        return when (reader.peek()) {
            com.squareup.moshi.JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                null
            }
            com.squareup.moshi.JsonReader.Token.NUMBER -> {
                // Trata como Long quando dá (evita virar "5.0" para temporadas).
                val s = reader.nextString()
                if (s.endsWith(".0")) s.dropLast(2) else s
            }
            com.squareup.moshi.JsonReader.Token.STRING -> reader.nextString()
            com.squareup.moshi.JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @com.squareup.moshi.ToJson
    fun toJson(@Loose value: String?): String? = value
}

@JsonClass(generateAdapter = true)
data class SeasonDto(
    val id: Int? = null,
    val name: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    val overview: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
    val cover: String? = null,
    @Json(name = "cover_big") val coverBig: String? = null
)

@JsonClass(generateAdapter = true)
data class SeriesInfoDetail(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "last_modified") val lastModified: String? = null,
    val rating: String? = null,
    @Json(name = "backdrop_path") val backdropPath: List<String>? = null,
    @Json(name = "youtube_trailer") val youtubeTrailer: String? = null,
    @Json(name = "episode_run_time") val episodeRunTime: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeDto(
    val id: String,
    // Provedores vacilam: alguns mandam Int, outros String. @Loose aceita
    // os dois via LooseStringAdapter. Conversão pra Int fica no toModel.
    @Loose @Json(name = "episode_num") val episodeNum: String? = null,
    val title: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    val info: EpisodeInfo? = null,
    @Loose @Json(name = "season") val season: String? = null,
    @Json(name = "added") val added: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeInfo(
    // Provider variance for the poster URL — different panels expose it under
    // different keys. Fall through the list in toModel().
    @Json(name = "movie_image") val movieImage: String? = null,
    @Json(name = "cover_big") val coverBig: String? = null,
    val cover: String? = null,
    val image: String? = null,
    val name: String? = null,
    val title: String? = null,
    val plot: String? = null,
    val overview: String? = null,
    val duration: String? = null,
    @Json(name = "duration_secs") val durationSecs: Int? = null,
    val rating: Double? = null,
    @Json(name = "releasedate") val releaseDate: String? = null,
    val release_date: String? = null
)
