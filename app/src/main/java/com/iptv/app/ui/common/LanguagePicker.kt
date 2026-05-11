package com.iptv.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Vertical list of language options. Highlights the selected one and emits
 * the chosen BCP-47 tag (or null for "system"). Used in both the onboarding
 * step and Settings → Language.
 */
@Composable
fun LanguagePicker(
    selectedTag: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocaleManager.available.forEach { opt ->
            val isSelected = opt.tag == selectedTag
            TouchableButton(
                onClick = { onPick(opt.tag) },
                selected = isSelected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    opt.display,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
