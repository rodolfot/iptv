package com.iptv.app.ui.update

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.iptv.app.update.UpdateCheckResult
import com.iptv.app.update.UpdateChecker
import com.iptv.app.update.UpdateInfo
import com.iptv.app.update.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Estágio do download/instalação em andamento no diálogo de atualização. */
sealed interface UpdateStage {
    data object Idle : UpdateStage
    data class Downloading(val percent: Int) : UpdateStage
    data class ReadyToInstall(val file: File) : UpdateStage
    data class Failed(val message: String) : UpdateStage
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val settings: SettingsStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val _info = MutableStateFlow<UpdateInfo?>(null)
    val info = _info.asStateFlow()
    private val _stage = MutableStateFlow<UpdateStage>(UpdateStage.Idle)
    val stage = _stage.asStateFlow()
    private var checked = false

    /** Resultado do "Verificar atualização agora" para a tela de Configurações
     *  dar o feedback (snackbar) — o diálogo em si abre sozinho via [info]. */
    private val _manualResult = MutableSharedFlow<UpdateCheckResult>(extraBufferCapacity = 1)
    val manualResult = _manualResult.asSharedFlow()

    fun checkOnce() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            val release = (checker.checkDetailed() as? UpdateCheckResult.Available)?.info ?: return@launch
            // Respect a previously-skipped version: only re-prompt when a newer one ships.
            val skipped = settings.skippedUpdate()
            if (skipped == release.versionName) return@launch
            _info.value = release
        }
    }

    /**
     * Checagem pedida pelo usuário nas Configurações. Diferente do automático,
     * ignora a versão "pulada" e abre o diálogo de instalação — antes só
     * aparecia um snackbar "nova versão disponível" e a instalação dependia da
     * notificação do sistema, que na Android TV não aparece.
     */
    fun checkNow() {
        viewModelScope.launch {
            val result = checker.checkDetailed()
            if (result is UpdateCheckResult.Available) {
                _stage.value = UpdateStage.Idle
                _info.value = result.info
            }
            _manualResult.tryEmit(result)
        }
    }

    fun dismiss() {
        _info.value = null
        _stage.value = UpdateStage.Idle
    }

    fun skipThisVersion() {
        val current = _info.value ?: return
        viewModelScope.launch {
            settings.skipUpdate(current.versionName)
            _info.value = null
            _stage.value = UpdateStage.Idle
        }
    }

    /** Baixa o APK da release atual, mostrando progresso — chamado pelo botão "Atualizar agora". */
    fun downloadUpdate() {
        val update = _info.value ?: return
        if (_stage.value is UpdateStage.Downloading) return
        viewModelScope.launch {
            _stage.value = UpdateStage.Downloading(0)
            runCatching {
                installer.download(appContext, update.apkUrl) { percent ->
                    _stage.value = UpdateStage.Downloading(percent)
                }
            }.onSuccess { file ->
                _stage.value = UpdateStage.ReadyToInstall(file)
            }.onFailure {
                _stage.value = UpdateStage.Failed(it.message.orEmpty())
            }
        }
    }

    fun canRequestInstall(context: Context): Boolean = installer.canRequestInstall(context)
    fun unknownSourcesSettingsIntent(context: Context): Intent = installer.unknownSourcesSettingsIntent(context)

    fun installIntent(context: Context): Intent? {
        val file = (_stage.value as? UpdateStage.ReadyToInstall)?.file ?: return null
        return installer.installIntent(context, file)
    }
}

/** ViewModel de atualização com escopo da Activity — o diálogo (na Home) e o
 *  botão "Verificar agora" (Configurações) compartilham a mesma instância. */
@Composable
fun rememberUpdateViewModel(): UpdateViewModel {
    val owner = LocalContext.current as? androidx.lifecycle.ViewModelStoreOwner
    return if (owner != null) hiltViewModel(owner) else hiltViewModel()
}

@Composable
fun UpdatePromptHost(vm: UpdateViewModel = rememberUpdateViewModel()) {
    LaunchedEffect(Unit) { vm.checkOnce() }
    val info by vm.info.collectAsState()
    val stage by vm.stage.collectAsState()
    val ctx = LocalContext.current
    // Foco inicial no botão principal: na TV o diálogo abria sem nenhum botão
    // focado e o primeiro toque no D-pad só "acordava" o foco.
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(info != null, stage) {
        if (info != null) {
            kotlinx.coroutines.delay(100)
            runCatching { primaryFocus.requestFocus() }
        }
    }
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

                    when (val s = stage) {
                        is UpdateStage.Downloading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.update_downloading, s.percent))
                            }
                        }
                        is UpdateStage.Failed -> {
                            Text(
                                stringResource(R.string.update_download_failed),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        is UpdateStage.ReadyToInstall -> {
                            if (!vm.canRequestInstall(ctx)) {
                                Text(
                                    stringResource(R.string.update_permission_needed),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        UpdateStage.Idle -> Unit
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        if (stage == UpdateStage.Idle) {
                            TouchableButton(onClick = { vm.skipThisVersion() }) {
                                Text(stringResource(R.string.update_skip))
                            }
                            TouchableButton(onClick = { vm.dismiss() }) {
                                Text(stringResource(R.string.update_later))
                            }
                        }
                        when (val s = stage) {
                            UpdateStage.Idle -> {
                                TouchableButton(
                                    onClick = { vm.downloadUpdate() },
                                    modifier = Modifier.focusRequester(primaryFocus)
                                ) {
                                    Text(stringResource(R.string.update_install_now))
                                }
                            }
                            is UpdateStage.Failed -> {
                                TouchableButton(onClick = { vm.dismiss() }) {
                                    Text(stringResource(R.string.update_later))
                                }
                                TouchableButton(
                                    onClick = { vm.downloadUpdate() },
                                    modifier = Modifier.focusRequester(primaryFocus)
                                ) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                            is UpdateStage.ReadyToInstall -> {
                                if (vm.canRequestInstall(ctx)) {
                                    TouchableButton(modifier = Modifier.focusRequester(primaryFocus), onClick = {
                                        vm.installIntent(ctx)?.let { intent ->
                                            // Alguns TV boxes não resolvem ACTION_VIEW de APK;
                                            // ACTION_INSTALL_PACKAGE cobre esses instaladores.
                                            runCatching { ctx.startActivity(intent) }.onFailure {
                                                runCatching {
                                                    @Suppress("DEPRECATION")
                                                    ctx.startActivity(
                                                        Intent(intent).setAction(Intent.ACTION_INSTALL_PACKAGE)
                                                    )
                                                }
                                            }
                                        }
                                        vm.dismiss()
                                    }) {
                                        Text(stringResource(R.string.update_install_now))
                                    }
                                } else {
                                    TouchableButton(modifier = Modifier.focusRequester(primaryFocus), onClick = {
                                        runCatching { ctx.startActivity(vm.unknownSourcesSettingsIntent(ctx)) }
                                    }) {
                                        Text(stringResource(R.string.update_open_settings))
                                    }
                                }
                            }
                            is UpdateStage.Downloading -> Unit
                        }
                    }
                }
            }
        }
    }
}
