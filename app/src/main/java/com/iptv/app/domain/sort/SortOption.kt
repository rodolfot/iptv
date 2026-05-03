package com.iptv.app.domain.sort

enum class SortOption(val label: String) {
    NAME_ASC("Nome (A→Z)"),
    NAME_DESC("Nome (Z→A)"),
    RELEASE_DATE_DESC("Lançamento (mais novo)"),
    RELEASE_DATE_ASC("Lançamento (mais antigo)"),
    ADDED_DATE_DESC("Adicionado (mais novo)"),
    ADDED_DATE_ASC("Adicionado (mais antigo)"),
    ID_ASC("ID (crescente)"),
    ID_DESC("ID (decrescente)"),
    RATING_DESC("Avaliação (maior)");

    companion object {
        fun fromName(name: String?): SortOption? = entries.firstOrNull { it.name == name }

        val LIVE_OPTIONS = listOf(NAME_ASC, NAME_DESC, ID_ASC, ID_DESC, ADDED_DATE_DESC)
        val MOVIE_OPTIONS = entries.toList()
        val SERIES_OPTIONS = entries.toList()
        val FAV_OPTIONS = listOf(NAME_ASC, NAME_DESC, ADDED_DATE_DESC, ADDED_DATE_ASC)
    }
}
