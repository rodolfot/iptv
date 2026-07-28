package com.iptv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.ComboBox
import com.iptv.app.ui.common.ComboColumn
import com.iptv.app.ui.common.ComboOption
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.TvSafeTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.R
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.db.LiveHistoryDao
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.db.SeriesProgressDao
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.data.prefs.DecoderMode
import com.iptv.app.data.prefs.DeviceProfile
import com.iptv.app.data.prefs.LiveViewMode
import com.iptv.app.data.prefs.RefreshInterval
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.data.prefs.StreamFormat
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.LocaleManager
import com.iptv.app.ui.common.FormFactor
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.work.CatalogRefreshWorker
import com.iptv.app.work.UpdateCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.DateFormat
import java.util.Date

data class CredentialsTest(val ok: Boolean, val message: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val progressDao: EpisodeProgressDao,
    private val movieProgressDao: MovieProgressDao,
    private val seriesProgressDao: SeriesProgressDao,
    private val liveHistoryDao: LiveHistoryDao,
    private val repo: XtreamRepository,
    private val currentProfile: CurrentProfile,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()
    private val _testResult = MutableStateFlow<CredentialsTest?>(null)
    val testResult = _testResult.asStateFlow()

    fun setPin(pin: String) { viewModelScope.launch { settings.setPin(pin) } }
    fun resetProgress() { viewModelScope.launch { progressDao.clearAll(currentProfile.id()) } }

    /**
     * Apaga o histórico do perfil ativo. Cada flag controla um eixo:
     *  - movies → progresso de filmes ("Continuar filmes" + watched)
     *  - series → progresso de séries + episódios
     *  - channels → últimos canais assistidos (live_history)
     */
    fun clearHistory(movies: Boolean, series: Boolean, channels: Boolean) {
        if (!(movies || series || channels)) return
        viewModelScope.launch {
            val pid = currentProfile.id()
            if (movies) movieProgressDao.clearAll(pid)
            if (series) {
                seriesProgressDao.clearAll(pid)
                progressDao.clearAll(pid)
            }
            if (channels) liveHistoryDao.clearAll(pid)
        }
    }

    /**
     * Persiste a escolha; a aplicação efetiva do locale + recreate da Activity
     * é feita pelo caller (precisa do Activity em mãos).
     */
    fun setAppLocale(tag: String?) {
        viewModelScope.launch { settings.setAppLocale(tag) }
    }

    fun setDeviceProfile(profile: DeviceProfile?) {
        viewModelScope.launch { settings.setDeviceProfile(profile) }
    }

    fun setShowHourlyClock(enabled: Boolean) {
        viewModelScope.launch { settings.setShowHourlyClock(enabled) }
    }

    fun setRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch {
            settings.setRefreshInterval(interval)
            CatalogRefreshWorker.schedule(appContext, interval, replace = true)
        }
    }

    fun setStreamFormat(format: StreamFormat) {
        viewModelScope.launch { settings.setStreamFormat(format) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        viewModelScope.launch { settings.setDecoderMode(mode) }
    }

    fun setLiveViewMode(mode: LiveViewMode) {
        viewModelScope.launch { settings.setLiveViewMode(mode) }
    }

    fun setExternalPlayer(pkg: String?) {
        viewModelScope.launch { settings.setExternalPlayer(pkg) }
    }

    fun setEpgUrlOverride(url: String?) {
        viewModelScope.launch { settings.setEpgUrlOverride(url) }
    }

    fun setEpgOffsetMinutes(minutes: Int) {
        viewModelScope.launch { settings.setEpgOffsetMinutes(minutes) }
    }

    /** Apps de vídeo instalados que respondem a ACTION_VIEW de vídeo — para o
     *  usuário escolher o player externo preferido (MX Player, VLC, etc.). */
    fun installedVideoPlayers(): List<Pair<String, String>> {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.parse("http://example.com/a.mkv"), "video/*")
        }
        val pm = appContext.packageManager
        val flags = android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        return runCatching {
            pm.queryIntentActivities(intent, flags)
                .filter { it.activityInfo.packageName != appContext.packageName }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun addProfile(profile: com.iptv.app.data.prefs.Profile) {
        viewModelScope.launch { settings.addProfile(profile) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { settings.deleteProfile(id) }
    }

    fun activateProfile(id: String, onActivated: () -> Unit) {
        viewModelScope.launch {
            settings.activateProfile(id)
            onActivated()
        }
    }

    fun testCredentials(host: String, user: String, pass: String) {
        viewModelScope.launch {
            _testing.value = true
            _testResult.value = null
            val cleanHost = host.trim().let {
                if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
            }.trimEnd('/')
            runCatching { repo.login(cleanHost, user.trim(), pass.trim()) }
                .onSuccess { resp ->
                    val ok = resp.userInfo?.auth == 1 || resp.userInfo?.username != null
                    _testResult.value = CredentialsTest(
                        ok = ok,
                        message = resp.userInfo?.message ?: ""
                    )
                }
                .onFailure { _testResult.value = CredentialsTest(false, it.message ?: "?") }
            _testing.value = false
        }
    }

    fun saveCredentials(host: String, user: String, pass: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            val cleanHost = host.trim().let {
                if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
            }.trimEnd('/')
            settings.saveCredentials(cleanHost, user.trim(), pass.trim())
            onSaved()
        }
    }

    fun clearTestResult() { _testResult.value = null }
}

@Composable
fun SettingsScreen(
    vm: HomeViewModel,
    onLogout: () -> Unit,
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val s by vm.settingsFlow.collectAsState()
    val testing by settingsVm.testing.collectAsState()
    val testResult by settingsVm.testResult.collectAsState()
    val dim = rememberTvDim()
    val snackbar = LocalSnackbar.current
    val pinSavedMsg = stringResource(R.string.snack_pin_saved)
    val credsSavedMsg = stringResource(R.string.snack_credentials_saved)
    val refreshingMsg = stringResource(R.string.snack_catalog_refreshing)
    val refreshedMsg = stringResource(R.string.snack_catalog_refreshed)
    val refreshIntervalSavedMsg = stringResource(R.string.snack_refresh_interval_saved)
    val localeSavedMsg = stringResource(R.string.snack_locale_saved)
    val activity = LocalContext.current as? android.app.Activity
    var pin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var currentPinError by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var crashLogOpen by remember { mutableStateOf(false) }
    var profilesOpen by remember { mutableStateOf(false) }
    var historyDialogOpen by remember { mutableStateOf(false) }
    val historyClearedMsg = stringResource(R.string.history_cleared)
    var editingServer by remember { mutableStateOf(false) }
    var editHost by remember { mutableStateOf(s.host) }
    var editUser by remember { mutableStateOf(s.username) }
    var editPass by remember { mutableStateOf(s.password) }

    LaunchedEffect(s.parentalPin) {
        // Limpa os campos sempre que o PIN persistido muda (acabou de salvar).
        pin = ""
        currentPin = ""
        currentPinError = false
    }
    LaunchedEffect(s.host, s.username, s.password) {
        if (!editingServer) {
            editHost = s.host; editUser = s.username; editPass = s.password
        }
    }

    if (crashLogOpen) {
        CrashLogScreen(onClose = { crashLogOpen = false })
        return
    }
    // Sem tela de loading global aqui — o Worker roda em background e o
    // usuário pode continuar navegando. A faixa de progresso aparece na
    // tela Início (CatalogLoadingScreen é usada só no primeiro boot).
    if (aboutOpen) {
        AboutScreen(onClose = { aboutOpen = false })
        return
    }
    if (profilesOpen) {
        ProfilesScreen(
            vm = settingsVm,
            settingsStoreFlow = vm.settingsFlow,
            onClose = { profilesOpen = false },
            onProfileActivated = {
                profilesOpen = false
                vm.refreshAll()
            }
        )
        return
    }

    val isPhone = dim.formFactor == FormFactor.Phone

    // --- Coluna esquerda: identidade/conta (servidor, PIN parental, geral, sessão) ---
    val leftColumn: @Composable ColumnScope.() -> Unit = {
        SettingsSection(title = stringResource(R.string.settings_server)) {
            if (!editingServer) {
                Text(stringResource(R.string.settings_host, s.host), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.settings_user, s.username), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TouchableButton(compact = true, onClick = { editingServer = true }) {
                        Text(stringResource(R.string.settings_change_server))
                    }
                    TouchableButton(compact = true, onClick = { profilesOpen = true }) {
                        Text(stringResource(R.string.settings_profiles_button))
                    }
                }
                if (s.profiles.size > 1) {
                    Text(
                        stringResource(
                            R.string.profiles_active_label,
                            s.profiles.firstOrNull { it.id == s.activeProfileId }?.name ?: "—"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                TvSafeTextField(
                    value = editHost,
                    onValueChange = { editHost = it },
                    label = stringResource(R.string.login_host),
                    modifier = Modifier.fillMaxWidth()
                )
                TvSafeTextField(
                    value = editUser,
                    onValueChange = { editUser = it },
                    label = stringResource(R.string.login_user),
                    modifier = Modifier.fillMaxWidth()
                )
                TvSafeTextField(
                    value = editPass,
                    onValueChange = { editPass = it },
                    label = stringResource(R.string.login_pass),
                    isPassword = true,
                    modifier = Modifier.fillMaxWidth()
                )
                testResult?.let { r ->
                    if (r.ok) {
                        Text(
                            stringResource(R.string.settings_connection_ok),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            stringResource(R.string.settings_connection_failed, r.message),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TouchableButton(
                        compact = true,
                        enabled = !testing && editHost.isNotBlank() && editUser.isNotBlank() && editPass.isNotBlank(),
                        onClick = { settingsVm.testCredentials(editHost, editUser, editPass) }
                    ) { Text(stringResource(R.string.settings_test_connection)) }
                    TouchableButton(
                        compact = true,
                        enabled = testResult?.ok == true,
                        onClick = {
                            settingsVm.saveCredentials(editHost, editUser, editPass) {
                                editingServer = false
                                settingsVm.clearTestResult()
                                vm.refreshAll()
                                snackbar?.show(credsSavedMsg)
                            }
                        }
                    ) { Text(stringResource(R.string.settings_save_credentials)) }
                    TouchableButton(compact = true, onClick = {
                        editingServer = false
                        editHost = s.host; editUser = s.username; editPass = s.password
                        settingsVm.clearTestResult()
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_parental_title)) {
            Text(
                stringResource(if (s.isPinSet) R.string.settings_pin_set else R.string.settings_pin_unset),
                style = MaterialTheme.typography.bodySmall
            )
            if (s.isPinSet) {
                PinField(
                    value = currentPin,
                    onValueChange = {
                        currentPin = it.filter(Char::isDigit).take(8)
                        if (currentPinError) currentPinError = false
                    },
                    placeholder = stringResource(R.string.settings_pin_current_label),
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentPinError) {
                    Text(
                        stringResource(R.string.settings_pin_current_wrong),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            PinField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                placeholder = stringResource(
                    if (s.isPinSet) R.string.settings_pin_label_set else R.string.settings_pin_label_unset
                ),
                modifier = Modifier.fillMaxWidth()
            )
            val canSavePin = pin.length >= 4 && (!s.isPinSet || currentPin.isNotEmpty())
            TouchableButton(
                compact = true,
                enabled = canSavePin,
                onClick = {
                    // Quando já existe PIN, exige que o "PIN atual" digitado bata
                    // com o persistido antes de aceitar a troca.
                    if (s.isPinSet && currentPin != (s.parentalPin ?: "")) {
                        currentPinError = true
                    } else {
                        settingsVm.setPin(pin)
                        snackbar?.show(pinSavedMsg)
                    }
                }
            ) {
                Text(stringResource(
                    if (s.isPinSet) R.string.settings_pin_save_set else R.string.settings_pin_save_unset
                ))
            }
        }

        SettingsSection(title = stringResource(R.string.settings_display)) {
            NotificationsPermissionSection()

            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            var pendingLocaleTag by remember { mutableStateOf<String?>(null) }
            var localeDialogOpen by remember { mutableStateOf(false) }
            run {
                val localeOptions = LocaleManager.available.map {
                    ComboOption(id = it.tag ?: "__system__", label = it.display)
                }
                val currentKey = s.appLocale ?: "__system__"
                val current = localeOptions.firstOrNull { it.id == currentKey }
                ComboBox(
                    selected = current,
                    options = localeOptions,
                    onSelect = {
                        val tag = if (it.id == "__system__") null else it.id
                        settingsVm.setAppLocale(tag)
                        snackbar?.show(localeSavedMsg)
                        pendingLocaleTag = tag
                        localeDialogOpen = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (localeDialogOpen) {
                AlertDialog(
                    onDismissRequest = { localeDialogOpen = false },
                    title = { Text(stringResource(R.string.locale_restart_title)) },
                    text = { Text(stringResource(R.string.locale_restart_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            localeDialogOpen = false
                            val tag = pendingLocaleTag
                            // 1) Persiste o locale via AppCompatDelegate (no
                            //    Android 13+ o sistema recria a activity
                            //    sozinho via LocaleManager).
                            // 2) Em versões < 13, ou se o auto-recreate não
                            //    rolar, relançamos MainActivity com
                            //    CLEAR_TOP | NEW_TASK e finalizamos a atual.
                            //    Antes usávamos activity.recreate() — em
                            //    algumas TVs ele "trava" porque está dentro
                            //    de um Dialog/Compose state inconsistente.
                            if (tag == null) LocaleManager.resetToSystem()
                            else LocaleManager.apply(tag)
                            activity?.let { act ->
                                val pm = act.packageManager
                                val intent = pm.getLaunchIntentForPackage(act.packageName)
                                    ?.apply {
                                        addFlags(
                                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        )
                                    }
                                if (intent != null) {
                                    act.startActivity(intent)
                                    act.finish()
                                    // overridePendingTransition(0, 0) evita
                                    // flash branco entre as duas activities.
                                    act.overridePendingTransition(0, 0)
                                }
                            }
                        }) { Text(stringResource(R.string.locale_restart_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { localeDialogOpen = false }) {
                            Text(stringResource(R.string.exit_no))
                        }
                    }
                )
            }

            run {
                val autoLabel = stringResource(R.string.onboarding_device_auto)
                val tvLabel = stringResource(R.string.onboarding_device_tv)
                val tabletLabel = stringResource(R.string.onboarding_device_tablet)
                val phoneLabel = stringResource(R.string.onboarding_device_phone)
                val options = listOf(
                    ComboOption(id = "__auto__", label = autoLabel),
                    ComboOption(id = DeviceProfile.TV.name, label = tvLabel),
                    ComboOption(id = DeviceProfile.TABLET.name, label = tabletLabel),
                    ComboOption(id = DeviceProfile.PHONE.name, label = phoneLabel)
                )
                val currentKey = s.deviceProfile?.name ?: "__auto__"
                val current = options.firstOrNull { it.id == currentKey }
                ComboColumn(label = stringResource(R.string.onboarding_device_title)) {
                    ComboBox(
                        selected = current,
                        options = options,
                        onSelect = {
                            val profile = if (it.id == "__auto__") null else DeviceProfile.valueOf(it.id)
                            settingsVm.setDeviceProfile(profile)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsVm.setShowHourlyClock(!s.showHourlyClock) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = s.showHourlyClock,
                    onCheckedChange = { settingsVm.setShowHourlyClock(it) }
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_hourly_clock),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.settings_hourly_clock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_session)) {
            TouchableButton(compact = true, onClick = {
                vm.logout()
                onLogout()
            }) { Text(stringResource(R.string.settings_logout)) }
        }
    }

    // --- Coluna direita: conteúdo/playback (catálogo, player, EPG, histórico, sobre) ---
    val rightColumn: @Composable ColumnScope.() -> Unit = {
        SettingsSection(title = stringResource(R.string.settings_catalog)) {
            run {
                val intervalOptions = RefreshInterval.values().map {
                    ComboOption(it, stringResource(it.labelRes))
                }
                val current = intervalOptions.firstOrNull { it.id == s.refreshInterval }
                ComboColumn(label = stringResource(R.string.settings_refresh_interval)) {
                    ComboBox(
                        selected = current,
                        options = intervalOptions,
                        onSelect = {
                            settingsVm.setRefreshInterval(it.id)
                            snackbar?.show(refreshIntervalSavedMsg)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            val lastUpdated by vm.lastUpdatedAt.collectAsState()
            Text(
                text = lastUpdated?.let {
                    stringResource(
                        R.string.settings_refresh_last,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                    )
                } ?: stringResource(R.string.settings_refresh_never),
                style = MaterialTheme.typography.bodySmall
            )
            val refreshCtx = LocalContext.current
            val workProgress by CatalogRefreshWorker
                .observeProgress(refreshCtx)
                .collectAsState(initial = null)
            val wasRunning = remember { mutableStateOf(false) }
            LaunchedEffect(workProgress) {
                val running = workProgress != null
                if (wasRunning.value && !running) {
                    snackbar?.show(refreshedMsg)
                    vm.refreshLastUpdatedAt()
                }
                wasRunning.value = running
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TouchableButton(compact = true, onClick = {
                    snackbar?.show(refreshingMsg)
                    CatalogRefreshWorker.enqueueOneShot(refreshCtx)
                }) {
                    Text(stringResource(R.string.settings_refresh_now))
                }
                TouchableButton(compact = true, onClick = { settingsVm.resetProgress() }) {
                    Text(stringResource(R.string.settings_reset_progress))
                }
            }
            workProgress?.let { p ->
                val phaseLabel = when (p.phase) {
                    "categories" -> stringResource(R.string.refresh_phase_categories)
                    "live" -> stringResource(R.string.refresh_phase_live)
                    "movies" -> stringResource(R.string.refresh_phase_movies)
                    "series" -> stringResource(R.string.refresh_phase_series)
                    else -> p.phase
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "$phaseLabel ${p.current}/${p.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_player_advanced)) {
            // Decoder: automático / hardware / software.
            val decoderOptions = DecoderMode.values().map {
                ComboOption(
                    it,
                    when (it) {
                        DecoderMode.AUTO -> stringResource(R.string.decoder_auto)
                        DecoderMode.HARDWARE -> stringResource(R.string.decoder_hardware)
                        DecoderMode.SOFTWARE -> stringResource(R.string.decoder_software)
                    }
                )
            }
            ComboColumn(label = stringResource(R.string.settings_decoder)) {
                ComboBox(
                    selected = decoderOptions.firstOrNull { it.id == s.decoderMode },
                    options = decoderOptions,
                    onSelect = { settingsVm.setDecoderMode(it.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Player externo preferido.
            val internalOpt = ComboOption("", stringResource(R.string.external_player_internal))
            val playerOptions = listOf(internalOpt) +
                remember { settingsVm.installedVideoPlayers() }
                    .map { ComboOption(it.first, it.second) }
            ComboColumn(label = stringResource(R.string.settings_external_player)) {
                ComboBox(
                    selected = playerOptions.firstOrNull { it.id == (s.externalPlayerPackage ?: "") }
                        ?: internalOpt,
                    options = playerOptions,
                    onSelect = { settingsVm.setExternalPlayer(it.id.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_epg)) {
            // Formato padrão do stream Ao Vivo.
            val formatOptions = StreamFormat.values().map {
                ComboOption(
                    it,
                    when (it) {
                        StreamFormat.TS -> stringResource(R.string.stream_format_ts)
                        StreamFormat.HLS -> stringResource(R.string.stream_format_hls)
                    }
                )
            }
            ComboColumn(label = stringResource(R.string.settings_stream_format)) {
                ComboBox(
                    selected = formatOptions.firstOrNull { it.id == s.streamFormat },
                    options = formatOptions,
                    onSelect = { settingsVm.setStreamFormat(it.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Layout de navegação do Ao Vivo (grade, lista com categorias, lista com preview).
            val liveViewModeOptions = LiveViewMode.values().map {
                ComboOption(
                    it,
                    when (it) {
                        LiveViewMode.GRID -> stringResource(R.string.live_view_mode_grid)
                        LiveViewMode.LIST_WITH_CATEGORIES -> stringResource(R.string.live_view_mode_list_categories)
                        LiveViewMode.LIST_FOCUS -> stringResource(R.string.live_view_mode_list_focus)
                    }
                )
            }
            ComboColumn(label = stringResource(R.string.live_view_mode_title)) {
                ComboBox(
                    selected = liveViewModeOptions.firstOrNull { it.id == s.liveViewMode },
                    options = liveViewModeOptions,
                    onSelect = { settingsVm.setLiveViewMode(it.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            var epgUrl by remember(s.epgUrlOverride) { mutableStateOf(s.epgUrlOverride ?: "") }
            TvSafeTextField(
                value = epgUrl,
                onValueChange = {
                    epgUrl = it
                    settingsVm.setEpgUrlOverride(it.ifBlank { null })
                },
                label = stringResource(R.string.settings_epg_url),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.settings_epg_url_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val offsets = listOf(-180, -120, -60, -30, 0, 30, 60, 120, 180)
            val offsetOptions = offsets.map {
                ComboOption(it, if (it == 0) "0 min" else (if (it > 0) "+$it min" else "$it min"))
            }
            ComboColumn(label = stringResource(R.string.settings_epg_offset)) {
                ComboBox(
                    selected = offsetOptions.firstOrNull { it.id == s.epgOffsetMinutes }
                        ?: offsetOptions.first { it.id == 0 },
                    options = offsetOptions,
                    onSelect = { settingsVm.setEpgOffsetMinutes(it.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_history)) {
            TouchableButton(compact = true, onClick = { historyDialogOpen = true }) {
                Text(stringResource(R.string.settings_history_clear))
            }
        }

        SettingsSection(title = stringResource(R.string.settings_about)) {
            val ctx = LocalContext.current
            // Baixa e prepara a instalação da release mais nova em segundo
            // plano (worker único "update_check_oneshot") — o resultado
            // chega aqui via WorkInfo, sem precisar de callback direto.
            val updateWorkInfo by UpdateCheckWorker
                .observeOneShot(ctx)
                .collectAsState(initial = null)
            LaunchedEffect(updateWorkInfo?.id, updateWorkInfo?.state) {
                val info = updateWorkInfo ?: return@LaunchedEffect
                when (info.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        val found = info.outputData.getBoolean(UpdateCheckWorker.OUTPUT_FOUND, false)
                        if (found) {
                            val version = info.outputData.getString(UpdateCheckWorker.OUTPUT_VERSION)
                            snackbar?.show(ctx.getString(R.string.snack_update_available, version ?: ""))
                        } else {
                            snackbar?.show(ctx.getString(R.string.snack_update_up_to_date))
                        }
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        snackbar?.show(ctx.getString(R.string.snack_update_check_failed))
                    }
                    else -> Unit
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TouchableButton(compact = true, onClick = { UpdateCheckWorker.checkNow(ctx) }) {
                    Text(stringResource(R.string.settings_check_update))
                }
                TouchableButton(compact = true, onClick = { aboutOpen = true }) {
                    // Versão visível direto no botão — antes só aparecia depois de
                    // entrar na tela Sobre. Útil pra suporte ("qual versão você está
                    // usando?") sem precisar navegar.
                    Text(stringResource(
                        R.string.settings_open_about_with_version,
                        com.iptv.app.BuildConfig.VERSION_NAME
                    ))
                }
            }
            // Log de crash local — só visível quando há registros. Sem
            // dependência externa (Firebase/Sentry); o usuário pode copiar e
            // mandar pro suporte se quiser.
            val hasCrashLog = remember(activity) {
                activity?.let { com.iptv.app.diag.CrashLog.read(it).isNotBlank() } ?: false
            }
            if (hasCrashLog) {
                TouchableButton(compact = true, onClick = { crashLogOpen = true }) {
                    Text(stringResource(R.string.crash_log_title))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isPhone) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                leftColumn()
                rightColumn()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { leftColumn() }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { rightColumn() }
            }
        }
    }

    if (historyDialogOpen) {
        ClearHistoryDialog(
            onDismiss = { historyDialogOpen = false },
            onConfirm = { movies, series, channels ->
                settingsVm.clearHistory(movies, series, channels)
                historyDialogOpen = false
                snackbar?.show(historyClearedMsg)
            }
        )
    }
}

/**
 * Card de agrupamento visual — cada bloco de configurações relacionadas
 * (Servidor, Segurança, Player…) fica dentro de um card com fundo e cantos
 * arredondados, em vez de títulos soltos direto no fluxo da tela.
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (movies: Boolean, series: Boolean, channels: Boolean) -> Unit
) {
    var movies by remember { mutableStateOf(true) }
    var series by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf(true) }
    val anySelected = movies || series || channels
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.history_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                HistoryCheckRow(
                    label = stringResource(R.string.history_movies),
                    checked = movies,
                    onToggle = { movies = !movies }
                )
                HistoryCheckRow(
                    label = stringResource(R.string.history_series),
                    checked = series,
                    onToggle = { series = !series }
                )
                HistoryCheckRow(
                    label = stringResource(R.string.history_channels),
                    checked = channels,
                    onToggle = { channels = !channels }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = anySelected,
                onClick = { onConfirm(movies, series, channels) }
            ) { Text(stringResource(R.string.history_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun HistoryCheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotificationsPermissionSection() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    Text(stringResource(R.string.settings_notifications_title), style = MaterialTheme.typography.titleSmall)
    if (granted) {
        Text(
            stringResource(R.string.settings_notifications_perm_granted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        TouchableButton(compact = true, onClick = {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }) { Text(stringResource(R.string.settings_notifications_perm)) }
    }
}

/**
 * D-pad friendly PIN input. Same pattern as the catalog filter field:
 *  - Focus alone never opens the IME.
 *  - OK / D-pad center / Enter enters edit mode (then the IME shows).
 *  - Back / Escape leaves edit mode without losing focus.
 *
 * Prevents the soft keyboard from popping up just because the user navigated
 * past the field with the remote.
 */
@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            editorFocus.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (editing) MaterialTheme.colorScheme.primary else Color(0x40FFFFFF)
    val masked = "•".repeat(value.length)

    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(2.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { editing = false }),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editorFocus)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Back, Key.Escape -> { editing = false; true }
                            else -> false
                        }
                    }
            )
        } else {
            Text(
                text = if (value.isEmpty()) placeholder else masked,
                color = if (value.isEmpty()) Color(0x80FFFFFF) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { editing = true; true }
                            else -> false
                        }
                    }
                    .clickable { editing = true }
            )
        }
    }
}
