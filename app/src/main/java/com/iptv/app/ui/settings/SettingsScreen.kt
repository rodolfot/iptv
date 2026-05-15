package com.iptv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.ui.common.TouchableButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iptv.app.R
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.data.prefs.RefreshInterval
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.rememberTvDim
import com.iptv.app.ui.home.HomeViewModel
import com.iptv.app.work.CatalogRefreshWorker
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
    fun setAppLocale(tag: String?) {
        viewModelScope.launch {
            settings.setAppLocale(tag)
            if (tag.isNullOrBlank()) com.iptv.app.ui.common.LocaleManager.resetToSystem()
            else com.iptv.app.ui.common.LocaleManager.apply(tag)
        }
    }

    fun setDeviceProfile(profile: com.iptv.app.data.prefs.DeviceProfile?) {
        viewModelScope.launch { settings.setDeviceProfile(profile) }
    }

    fun setRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch {
            settings.setRefreshInterval(interval)
            CatalogRefreshWorker.schedule(appContext, interval, replace = true)
        }
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
    val refreshIntervalSavedMsg = stringResource(R.string.snack_refresh_interval_saved)
    val localeSavedMsg = stringResource(R.string.snack_locale_saved)
    val languageLabel = stringResource(R.string.settings_language_label)
    var pin by remember { mutableStateOf(s.parentalPin.orEmpty()) }
    var aboutOpen by remember { mutableStateOf(false) }
    var profilesOpen by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf(false) }
    var editHost by remember { mutableStateOf(s.host) }
    var editUser by remember { mutableStateOf(s.username) }
    var editPass by remember { mutableStateOf(s.password) }

    LaunchedEffect(s.parentalPin) { pin = s.parentalPin.orEmpty() }
    LaunchedEffect(s.host, s.username, s.password) {
        if (!editingServer) {
            editHost = s.host; editUser = s.username; editPass = s.password
        }
    }

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

    val isPhone = dim.formFactor == com.iptv.app.ui.common.FormFactor.Phone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        // On TV/Tablet, split the settings into two side-by-side columns so
        // most of the page fits within a single viewport — the D-pad can't
        // jump back to the top of a long scroll, so density matters.
        val rootArrangement = if (isPhone) Arrangement.spacedBy(20.dp) else Arrangement.spacedBy(32.dp)

        val leftColumn: @Composable () -> Unit = {
            Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.titleMedium)
        if (!editingServer) {
            Text(stringResource(R.string.settings_host, s.host), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_user, s.username), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                TouchableButton(onClick = { editingServer = true }) {
                    Text(stringResource(R.string.settings_change_server))
                }
                TouchableButton(onClick = { profilesOpen = true }) {
                    Text(stringResource(R.string.settings_profiles_button))
                }
            }
            if (s.profiles.size > 1) {
                Text(
                    stringResource(R.string.profiles_active_label, s.profiles.firstOrNull { it.id == s.activeProfileId }?.name ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            OutlinedTextField(
                value = editHost,
                onValueChange = { editHost = it },
                label = { Text(stringResource(R.string.login_host)) },
                singleLine = true,
                modifier = Modifier.width(560.dp)
            )
            OutlinedTextField(
                value = editUser,
                onValueChange = { editUser = it },
                label = { Text(stringResource(R.string.login_user)) },
                singleLine = true,
                modifier = Modifier.width(360.dp)
            )
            OutlinedTextField(
                value = editPass,
                onValueChange = { editPass = it },
                label = { Text(stringResource(R.string.login_pass)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(360.dp)
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
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                TouchableButton(
                    enabled = !testing && editHost.isNotBlank() && editUser.isNotBlank() && editPass.isNotBlank(),
                    onClick = { settingsVm.testCredentials(editHost, editUser, editPass) }
                ) { Text(stringResource(R.string.settings_test_connection)) }
                TouchableButton(
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
                TouchableButton(onClick = {
                    editingServer = false
                    editHost = s.host; editUser = s.username; editPass = s.password
                    settingsVm.clearTestResult()
                }) { Text(stringResource(R.string.cancel)) }
            }
        }

        Text(stringResource(R.string.settings_parental_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (s.isPinSet) R.string.settings_pin_set else R.string.settings_pin_unset),
            style = MaterialTheme.typography.bodySmall
        )
        PinField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            placeholder = stringResource(
                if (s.isPinSet) R.string.settings_pin_label_set else R.string.settings_pin_label_unset
            ),
            modifier = Modifier.width(360.dp)
        )
        TouchableButton(
            enabled = pin.length >= 4,
            onClick = {
                settingsVm.setPin(pin)
                snackbar?.show(pinSavedMsg)
            }
        ) {
            Text(stringResource(
                if (s.isPinSet) R.string.settings_pin_save_set else R.string.settings_pin_save_unset
            ))
        }

        Text(stringResource(R.string.settings_catalog), style = MaterialTheme.typography.titleMedium)
        run {
            val intervalOptions = RefreshInterval.values().map {
                com.iptv.app.ui.common.ComboOption(it, stringResource(it.labelRes))
            }
            val current = intervalOptions.firstOrNull { it.id == s.refreshInterval }
            com.iptv.app.ui.common.ComboColumn(
                label = stringResource(R.string.settings_refresh_interval)
            ) {
                com.iptv.app.ui.common.ComboBox(
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
        TouchableButton(onClick = {
            vm.bootstrapCatalog(force = true)
            snackbar?.show(refreshingMsg)
        }) {
            Text(stringResource(R.string.settings_refresh_now))
        }
        TouchableButton(onClick = { settingsVm.resetProgress() }) {
            Text(stringResource(R.string.settings_reset_progress))
        }
        } // end leftColumn

        val rightColumn: @Composable () -> Unit = {
            NotificationsPermissionSection()

            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            run {
                val localeOptions = com.iptv.app.ui.common.LocaleManager.available.map {
                    com.iptv.app.ui.common.ComboOption(
                        id = it.tag ?: "__system__",
                        label = it.display
                    )
                }
                val currentKey = s.appLocale ?: "__system__"
                val current = localeOptions.firstOrNull { it.id == currentKey }
                com.iptv.app.ui.common.ComboColumn(label = languageLabel) {
                    com.iptv.app.ui.common.ComboBox(
                        selected = current,
                        options = localeOptions,
                        onSelect = {
                            val tag = if (it.id == "__system__") null else it.id
                            settingsVm.setAppLocale(tag)
                            snackbar?.show(localeSavedMsg)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            run {
                val autoLabel = stringResource(R.string.onboarding_device_auto)
                val tvLabel = stringResource(R.string.onboarding_device_tv)
                val tabletLabel = stringResource(R.string.onboarding_device_tablet)
                val phoneLabel = stringResource(R.string.onboarding_device_phone)
                val options = listOf(
                    com.iptv.app.ui.common.ComboOption(id = "__auto__", label = autoLabel),
                    com.iptv.app.ui.common.ComboOption(
                        id = com.iptv.app.data.prefs.DeviceProfile.TV.name,
                        label = tvLabel
                    ),
                    com.iptv.app.ui.common.ComboOption(
                        id = com.iptv.app.data.prefs.DeviceProfile.TABLET.name,
                        label = tabletLabel
                    ),
                    com.iptv.app.ui.common.ComboOption(
                        id = com.iptv.app.data.prefs.DeviceProfile.PHONE.name,
                        label = phoneLabel
                    )
                )
                val currentKey = s.deviceProfile?.name ?: "__auto__"
                val current = options.firstOrNull { it.id == currentKey }
                com.iptv.app.ui.common.ComboColumn(
                    label = stringResource(R.string.onboarding_device_title)
                ) {
                    com.iptv.app.ui.common.ComboBox(
                        selected = current,
                        options = options,
                        onSelect = {
                            val profile = if (it.id == "__auto__") null
                                else com.iptv.app.data.prefs.DeviceProfile.valueOf(it.id)
                            settingsVm.setDeviceProfile(profile)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        TouchableButton(onClick = { aboutOpen = true }) {
            Text(stringResource(R.string.settings_open_about))
        }

            Text(stringResource(R.string.settings_session), style = MaterialTheme.typography.titleMedium)
            TouchableButton(onClick = {
                vm.logout()
                onLogout()
            }) { Text(stringResource(R.string.settings_logout)) }
        } // end rightColumn

        if (isPhone) {
            leftColumn()
            rightColumn()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = rootArrangement
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) { leftColumn() }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) { rightColumn() }
            }
        }
    }
}

@Composable
private fun NotificationsPermissionSection() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = androidx.compose.ui.platform.LocalContext.current
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

    Text(stringResource(R.string.settings_notifications_title), style = MaterialTheme.typography.titleMedium)
    if (granted) {
        Text(
            stringResource(R.string.settings_notifications_perm_granted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        TouchableButton(onClick = {
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
            .background(MaterialTheme.colorScheme.surface)
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
