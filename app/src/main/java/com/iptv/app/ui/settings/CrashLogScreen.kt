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
import com.iptv.app.diag.CrashLog
import com.iptv.app.ui.common.rememberTvDim

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CrashLogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val dim = rememberTvDim()
    var contents by remember { mutableStateOf(CrashLog.read(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.crash_log_title), style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (contents.isBlank()) {
                Text(stringResource(R.string.crash_log_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(contents, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchableButton(onClick = {
                CrashLog.clear(context)
                contents = ""
            }) { Text(stringResource(R.string.crash_log_clear)) }
            TouchableButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}
