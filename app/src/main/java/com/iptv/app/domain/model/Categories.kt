package com.iptv.app.domain.model

/**
 * Order categories the way the user expects in the grid: alphabetical, with
 * adult categories at the bottom so they don't sit between everyday choices.
 *
 * Sorting is locale-insensitive lowercase — the providers we see ship Latin
 * names with sporadic accents, and this keeps "Ação" and "Acao" near each
 * other instead of in different buckets.
 */
fun List<Category>.sortedForDisplay(): List<Category> =
    sortedWith(
        compareBy(
            { it.isAdult },
            // Normaliza espaços múltiplos antes de comparar — alguns
            // provedores enviam "Canais |  Turquia" com dois espaços, o que
            // jogava esses nomes na frente de "Canais | 24 Horas" na ordem
            // lexicográfica padrão.
            { it.name.lowercase().replace(Regex("\\s+"), " ").trim() }
        )
    )
