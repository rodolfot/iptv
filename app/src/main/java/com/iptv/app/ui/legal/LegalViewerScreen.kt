package com.iptv.app.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.R
import com.iptv.app.ui.common.rememberTvDim

enum class LegalDoc { TERMS, PRIVACY }

@Composable
fun LegalViewerScreen(doc: LegalDoc, onClose: () -> Unit) {
    val context = LocalContext.current
    val dim = rememberTvDim()
    androidx.activity.compose.BackHandler(onBack = onClose)
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
            .safeDrawingPadding()
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp),
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
        TouchableButton(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}
