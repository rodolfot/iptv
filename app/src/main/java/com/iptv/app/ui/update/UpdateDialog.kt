package com.iptv.app.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.iptv.app.BuildConfig
import com.iptv.app.R
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.update.UpdateChecker
import com.iptv.app.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val settings: SettingsStore
) : ViewModel() {
    private val _info = MutableStateFlow<UpdateInfo?>(null)
    val info = _info.asStateFlow()
    private var checked = false

    fun checkOnce() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            val release = checker.check() ?: return@launch
            // Respect a previously-skipped version: only re-prompt when a newer one ships.
            val skipped = settings.skippedUpdate()
            if (skipped == release.versionName) return@launch
            _info.value = release
        }
    }

    fun dismiss() { _info.value = null }

    fun skipThisVersion() {
        val current = _info.value ?: return
        viewModelScope.launch {
            settings.skipUpdate(current.versionName)
            _info.value = null
        }
    }
}

@Composable
fun UpdatePromptHost(vm: UpdateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.checkOnce() }
    val info by vm.info.collectAsState()
    val ctx = LocalContext.current
    info?.let { update ->
        Dialog(onDismissRequest = { vm.dismiss() }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.width(560.dp).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.update_message, update.versionName, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (update.notes.isNotBlank()) {
                        Text(
                            stringResource(R.string.update_changelog_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        // Show full release notes inside a scrollable, height-bounded box so
                        // long changelogs fit without pushing the action buttons off-screen.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(update.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        TouchableButton(onClick = { vm.skipThisVersion() }) {
                            Text(stringResource(R.string.update_skip))
                        }
                        TouchableButton(onClick = { vm.dismiss() }) {
                            Text(stringResource(R.string.update_later))
                        }
                        TouchableButton(onClick = {
                            val target = update.releasePageUrl.takeIf { it.isNotBlank() }
                                ?: update.apkUrl
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { ctx.startActivity(intent) }
                            vm.dismiss()
                        }) {
                            Text(stringResource(R.string.update_open))
                        }
                    }
                }
            }
        }
    }
}
