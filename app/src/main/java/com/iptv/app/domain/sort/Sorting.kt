package com.iptv.app.domain.sort

import com.iptv.app.domain.model.LiveChannel
import com.iptv.app.domain.model.Movie
import com.iptv.app.domain.model.Series

/**
 * Ordem por número do canal (`num` do provedor). Canais sem número vão para o
 * fim, em ordem alfabética — antes caíam no `streamId`, que é um ID interno de
 * outra escala e embaralhava esses canais no meio da numeração.
 */
private val byChannelNumber: Comparator<LiveChannel> =
    compareBy<LiveChannel> { it.num == null }
        .thenBy { it.num ?: 0 }
        .thenBy { it.name.lowercase() }

private val byChannelNumberDesc: Comparator<LiveChannel> =
    compareBy<LiveChannel> { it.num == null }
        .thenByDescending { it.num ?: 0 }
        .thenBy { it.name.lowercase() }

fun List<LiveChannel>.sorted(option: SortOption): List<LiveChannel> = when (option) {
    SortOption.NAME_ASC -> sortedBy { it.name.lowercase() }
    SortOption.NAME_DESC -> sortedByDescending { it.name.lowercase() }
    SortOption.ID_ASC -> sortedWith(byChannelNumber)
    SortOption.ID_DESC -> sortedWith(byChannelNumberDesc)
    SortOption.ADDED_DATE_DESC -> sortedByDescending { it.addedTimestamp }
    SortOption.ADDED_DATE_ASC -> sortedBy { it.addedTimestamp }
    else -> sortedBy { it.name.lowercase() }
}

fun List<Movie>.sortedMovies(option: SortOption): List<Movie> = when (option) {
    SortOption.NAME_ASC -> sortedBy { it.name.lowercase() }
    SortOption.NAME_DESC -> sortedByDescending { it.name.lowercase() }
    SortOption.RELEASE_DATE_DESC -> sortedByDescending { it.releaseDate ?: "" }
    SortOption.RELEASE_DATE_ASC -> sortedBy { it.releaseDate ?: "" }
    SortOption.ADDED_DATE_DESC -> sortedByDescending { it.addedTimestamp }
    SortOption.ADDED_DATE_ASC -> sortedBy { it.addedTimestamp }
    SortOption.ID_ASC -> sortedBy { it.id }
    SortOption.ID_DESC -> sortedByDescending { it.id }
    SortOption.RATING_DESC -> sortedByDescending { it.rating }
}

fun List<Series>.sortedSeries(option: SortOption): List<Series> = when (option) {
    SortOption.NAME_ASC -> sortedBy { it.name.lowercase() }
    SortOption.NAME_DESC -> sortedByDescending { it.name.lowercase() }
    SortOption.RELEASE_DATE_DESC -> sortedByDescending { it.releaseDate ?: "" }
    SortOption.RELEASE_DATE_ASC -> sortedBy { it.releaseDate ?: "" }
    SortOption.ADDED_DATE_DESC -> sortedByDescending { it.lastModifiedTimestamp }
    SortOption.ADDED_DATE_ASC -> sortedBy { it.lastModifiedTimestamp }
    SortOption.ID_ASC -> sortedBy { it.id }
    SortOption.ID_DESC -> sortedByDescending { it.id }
    SortOption.RATING_DESC -> sortedByDescending { it.rating }
}
