package com.iptv.app.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R
import com.iptv.app.ui.common.TvDim

enum class LegalDoc { TERMS, PRIVACY }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LegalViewerScreen(doc: LegalDoc, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember(doc) {
        when (doc) {
            LegalDoc.TERMS -> LegalText.terms(context)
            LegalDoc.PRIVACY -> LegalText.privacy(context)
        }
    }
    val title = stringResource(
        when (doc) {
            LegalDoc.TERMS -> R.string.about_terms
            LegalDoc.PRIVACY -> R.string.about_privacy
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = TvDim.ScreenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}
