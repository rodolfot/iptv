package com.iptv.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iptv.app.R
// Button replaced with TouchableButton from same package — no import needed.
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.iptv.app.domain.sort.SortOption

/** Botão "Filtros" com ícone — mesma linguagem visual do [SortMenuButton]. */
@Composable
fun FiltersButton(active: Boolean, onClick: () -> Unit) {
    TouchableButton(onClick = onClick) {
        Icon(Icons.Filled.FilterList, contentDescription = null)
        Text(
            "  " + stringResource(if (active) R.string.filters_button_active else R.string.filters_button),
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun SortMenuButton(
    current: SortOption,
    options: List<SortOption>,
    onSelect: (SortOption) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val dim = rememberTvDim()
    val displayLabel = if (dim.formFactor == FormFactor.Phone) current.shortLabel else current.label
    TouchableButton(onClick = { open = true }) {
        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
        Text(
            "  $displayLabel",
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge
        )
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.sort_title), style = MaterialTheme.typography.titleLarge)
                    options.forEach { opt ->
                        TouchableButton(
                            onClick = {
                                onSelect(opt)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (opt == current) Icon(Icons.Filled.Check, contentDescription = null)
                                Text("  ${opt.label}")
                            }
                        }
                    }
                }
            }
        }
    }
}
