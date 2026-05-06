package com.iptv.app.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.iptv.app.BuildConfig
import com.iptv.app.R
import com.iptv.app.update.UpdateChecker
import com.iptv.app.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker
) : ViewModel() {
    private val _info = MutableStateFlow<UpdateInfo?>(null)
    val info = _info.asStateFlow()
    private var checked = false

    fun checkOnce() {
        if (checked) return
        checked = true
        viewModelScope.launch { _info.value = checker.check() }
    }

    fun dismiss() { _info.value = null }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdatePromptHost(vm: UpdateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.checkOnce() }
    val info by vm.info.collectAsState()
    val ctx = LocalContext.current
    info?.let { update ->
        Dialog(onDismissRequest = { vm.dismiss() }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                        Text(update.notes.take(400), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        Button(onClick = { vm.dismiss() }) {
                            Text(stringResource(R.string.update_later))
                        }
                        Button(onClick = {
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
