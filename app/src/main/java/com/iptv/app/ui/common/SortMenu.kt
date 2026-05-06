package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.iptv.app.domain.sort.SortOption

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SortMenuButton(
    current: SortOption,
    options: List<SortOption>,
    onSelect: (SortOption) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }) {
        Icon(Icons.Filled.Sort, contentDescription = null)
        Text("  Ordenar: ${current.label}")
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.sort_title), style = MaterialTheme.typography.titleLarge)
                    options.forEach { opt ->
                        Button(
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
