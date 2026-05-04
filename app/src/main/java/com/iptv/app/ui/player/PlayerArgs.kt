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
    val categoryId: String? = null
) {
    companion object {
        const val ROUTE = "player/{kind}/{streamId}/{title}/{ext}/{series}/{season}/{episode}/{pos}/{poster}/{cat}"

        fun toRoute(args: PlayerArgs): String {
            val title = Uri.encode(args.title)
            val ext = Uri.encode(args.containerExtension ?: "")
            val episode = Uri.encode(args.episodeId)
            val poster = Uri.encode(args.posterUrl ?: "")
            val cat = Uri.encode(args.categoryId ?: "")
            return "player/${args.kind.name}/${args.streamId}/$title/$ext/${args.seriesId}/${args.seasonNumber}/$episode/${args.startPositionMs}/$poster/$cat"
        }

        fun fromBackStack(entry: NavBackStackEntry): PlayerArgs {
            val a = entry.arguments!!
            return PlayerArgs(
                kind = PlayerKind.valueOf(a.getString("kind")!!),
                streamId = a.getString("streamId")!!.toInt(),
                title = Uri.decode(a.getString("title") ?: ""),
                containerExtension = Uri.decode(a.getString("ext") ?: "").takeIf { it.isNotBlank() },
                seriesId = a.getString("series")!!.toInt(),
                seasonNumber = a.getString("season")!!.toInt(),
                episodeId = Uri.decode(a.getString("episode") ?: ""),
                startPositionMs = a.getString("pos")!!.toLong(),
                posterUrl = Uri.decode(a.getString("poster") ?: "").takeIf { it.isNotBlank() },
                categoryId = Uri.decode(a.getString("cat") ?: "").takeIf { it.isNotBlank() }
            )
        }
    }
}
