package com.iptv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R
import com.iptv.app.data.db.EpisodeProgressDao
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.ui.common.TvDim
import com.iptv.app.ui.home.HomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val progressDao: EpisodeProgressDao
) : ViewModel() {
    fun setPin(pin: String) { viewModelScope.launch { settings.setPin(pin) } }
    fun resetProgress() { viewModelScope.launch { progressDao.clearAll() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: HomeViewModel,
    onLogout: () -> Unit,
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val s by vm.settingsFlow.collectAsState()
    var pin by remember { mutableStateOf(s.parentalPin.orEmpty()) }
    var aboutOpen by remember { mutableStateOf(false) }

    LaunchedEffect(s.parentalPin) { pin = s.parentalPin.orEmpty() }

    if (aboutOpen) {
        AboutScreen(onClose = { aboutOpen = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvDim.ScreenPadding, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_host, s.host), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_user, s.username), style = MaterialTheme.typography.bodyMedium)

        Text(stringResource(R.string.settings_parental_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (s.isPinSet) R.string.settings_pin_set else R.string.settings_pin_unset),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = {
                Text(stringResource(
                    if (s.isPinSet) R.string.settings_pin_label_set else R.string.settings_pin_label_unset
                ))
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.width(360.dp)
        )
        Button(
            enabled = pin.length >= 4,
            onClick = { settingsVm.setPin(pin) }
        ) {
            Text(stringResource(
                if (s.isPinSet) R.string.settings_pin_save_set else R.string.settings_pin_save_unset
            ))
        }

        Text(stringResource(R.string.settings_catalog), style = MaterialTheme.typography.titleMedium)
        Button(onClick = { vm.refreshAll() }) { Text(stringResource(R.string.settings_refresh)) }
        Button(onClick = { settingsVm.resetProgress() }) {
            Text(stringResource(R.string.settings_reset_progress))
        }

        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Button(onClick = { aboutOpen = true }) {
            Text(stringResource(R.string.settings_open_about))
        }

        Text(stringResource(R.string.settings_session), style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            vm.logout()
            onLogout()
        }) { Text(stringResource(R.string.settings_logout)) }
    }
}
