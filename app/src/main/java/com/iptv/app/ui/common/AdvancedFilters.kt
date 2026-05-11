package com.iptv.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R

/**
 * Locally-applied filters over the in-memory list. Persisting them across
 * sessions adds noise (users forget they're filtering) — keep them in
 * rememberSaveable on the screen instead.
 */
data class AdvancedFilters(
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val ratingMin: Double? = null,
    val genres: Set<String> = emptySet()
) {
    val isActive: Boolean
        get() = yearMin != null || yearMax != null || ratingMin != null || genres.isNotEmpty()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AdvancedFiltersDialog(
    initial: AdvancedFilters,
    availableGenres: List<String>,
    onDismiss: () -> Unit,
    onApply: (AdvancedFilters) -> Unit
) {
    var yearMin by remember { mutableStateOf(initial.yearMin?.toString().orEmpty()) }
    var yearMax by remember { mutableStateOf(initial.yearMax?.toString().orEmpty()) }
    var ratingMin by remember { mutableStateOf(initial.ratingMin?.toString().orEmpty()) }
    var selectedGenres by remember { mutableStateOf(initial.genres) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TouchableButton(onClick = {
                onApply(
                    AdvancedFilters(
                        yearMin = yearMin.toIntOrNull(),
                        yearMax = yearMax.toIntOrNull(),
                        ratingMin = ratingMin.replace(',', '.').toDoubleOrNull(),
                        genres = selectedGenres
                    )
                )
            }) { Text(stringResource(R.string.filters_apply)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TouchableButton(onClick = {
                    yearMin = ""; yearMax = ""; ratingMin = ""; selectedGenres = emptySet()
                }) { Text(stringResource(R.string.filters_reset)) }
                TouchableButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
        title = { Text(stringResource(R.string.filters_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.filters_year_range),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearMin,
                        onValueChange = { yearMin = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.filters_year_from)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                    OutlinedTextField(
                        value = yearMax,
                        onValueChange = { yearMax = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.filters_year_to)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    stringResource(R.string.filters_rating_min),
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedTextField(
                    value = ratingMin,
                    onValueChange = {
                        // Accept "7" or "7.5" / "7,5". Trim to 4 chars (e.g. 10.0).
                        ratingMin = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(4)
                    },
                    label = { Text(stringResource(R.string.filters_rating_min_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (availableGenres.isNotEmpty()) {
                    Text(
                        stringResource(R.string.filters_genres),
                        style = MaterialTheme.typography.titleSmall
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(availableGenres) { genre ->
                            val selected = genre in selectedGenres
                            TouchableButton(
                                onClick = {
                                    selectedGenres = if (selected) selectedGenres - genre
                                    else selectedGenres + genre
                                }
                            ) {
                                Text(if (selected) "✓ $genre" else genre)
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * Pulls a 4-digit year out of release-date strings as Xtream/TMDB return them:
 * "2021-08-15", "2021/08/15", "2021", or sometimes the bare year buried in noise.
 */
fun parseYear(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""\b(19|20)\d{2}\b""").find(raw)?.value
    return match?.toIntOrNull()
}
