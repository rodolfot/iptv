package com.iptv.app.ui.player

import android.net.Uri
import androidx.navigation.NavBackStackEntry

enum class PlayerKind { LIVE, MOVIE, EPISODE }

data class PlayerArgs(
    val kind: PlayerKind,
    val streamId: Int,
    val title: String,
    val containerExtension: String?,
    val seriesId: Int = -1,
    val seasonNumber: Int = -1,
    val episodeId: String = "",
    val startPositionMs: Long = 0L,
    val posterUrl: String? = null,
    val categoryId: String? = null,
    /** When > 0 and kind == LIVE, request a tv_archive timeshift from this absolute UTC time. */
    val timeshiftStartMs: Long = 0L,
    val timeshiftDurationMin: Int = 240
) {
    companion object {
        const val ROUTE = "player/{kind}/{streamId}/{title}/{ext}/{series}/{season}/{episode}/{pos}/{poster}/{cat}/{tsStart}/{tsDur}"
        private const val EMPTY = "_"

        private fun enc(value: String?): String {
            val v = value.orEmpty()
            return if (v.isEmpty()) EMPTY else Uri.encode(v)
        }

        private fun dec(value: String?): String {
            val v = value.orEmpty()
            return if (v == EMPTY) "" else Uri.decode(v)
        }

        fun toRoute(args: PlayerArgs): String {
            val title = enc(args.title)
            val ext = enc(args.containerExtension)
            val episode = enc(args.episodeId)
            val poster = enc(args.posterUrl)
            val cat = enc(args.categoryId)
            return "player/${args.kind.name}/${args.streamId}/$title/$ext/${args.seriesId}/${args.seasonNumber}/$episode/${args.startPositionMs}/$poster/$cat/${args.timeshiftStartMs}/${args.timeshiftDurationMin}"
        }

        /** Deep link usado pelos cards do canal da tela inicial da Android TV:
         *  tartatv://play?kind=LIVE&id=123&title=...&ext=...&poster=...&cat=... */
        fun toDeepLink(args: PlayerArgs): String {
            val b = Uri.Builder()
                .scheme("tartatv").authority("play")
                .appendQueryParameter("kind", args.kind.name)
                .appendQueryParameter("id", args.streamId.toString())
                .appendQueryParameter("title", args.title)
            args.containerExtension?.let { b.appendQueryParameter("ext", it) }
            args.posterUrl?.let { b.appendQueryParameter("poster", it) }
            args.categoryId?.let { b.appendQueryParameter("cat", it) }
            return b.build().toString()
        }

        fun fromDeepLink(uri: Uri): PlayerArgs? {
            if (uri.scheme != "tartatv" || uri.authority != "play") return null
            val kind = runCatching { PlayerKind.valueOf(uri.getQueryParameter("kind") ?: "") }
                .getOrNull() ?: return null
            val id = uri.getQueryParameter("id")?.toIntOrNull() ?: return null
            return PlayerArgs(
                kind = kind,
                streamId = id,
                title = uri.getQueryParameter("title").orEmpty(),
                containerExtension = uri.getQueryParameter("ext"),
                posterUrl = uri.getQueryParameter("poster"),
                categoryId = uri.getQueryParameter("cat")
            )
        }

        fun fromBackStack(entry: NavBackStackEntry): PlayerArgs {
            val a = entry.arguments!!
            return PlayerArgs(
                kind = PlayerKind.valueOf(a.getString("kind")!!),
                streamId = a.getString("streamId")!!.toInt(),
                title = dec(a.getString("title")),
                containerExtension = dec(a.getString("ext")).takeIf { it.isNotBlank() },
                seriesId = a.getString("series")!!.toInt(),
                seasonNumber = a.getString("season")!!.toInt(),
                episodeId = dec(a.getString("episode")),
                startPositionMs = a.getString("pos")!!.toLong(),
                posterUrl = dec(a.getString("poster")).takeIf { it.isNotBlank() },
                categoryId = dec(a.getString("cat")).takeIf { it.isNotBlank() },
                timeshiftStartMs = a.getString("tsStart")?.toLongOrNull() ?: 0L,
                timeshiftDurationMin = a.getString("tsDur")?.toIntOrNull() ?: 240
            )
        }
    }
}
