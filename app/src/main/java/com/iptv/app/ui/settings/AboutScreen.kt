package com.iptv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptv.app.ui.common.TouchableButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.legal.LegalDoc
import com.iptv.app.ui.legal.LegalViewerScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AboutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val dim = rememberTvDim()
    var viewing by remember { mutableStateOf<LegalDoc?>(null) }
    var crashOpen by remember { mutableStateOf(false) }

    if (viewing != null) {
        LegalViewerScreen(doc = viewing!!, onClose = { viewing = null })
        return
    }
    if (crashOpen) {
        CrashLogScreen(onClose = { crashOpen = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.about_version, versionName),
            style = MaterialTheme.typography.titleMedium
        )
        Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchableButton(onClick = { viewing = LegalDoc.TERMS }) {
                Text(stringResource(R.string.about_terms))
            }
            TouchableButton(onClick = { viewing = LegalDoc.PRIVACY }) {
                Text(stringResource(R.string.about_privacy))
            }
            TouchableButton(onClick = { crashOpen = true }) {
                Text(stringResource(R.string.about_crash_log))
            }
        }

        TouchableButton(onClick = onClose) { Text(stringResource(R.string.back)) }
    }
}
