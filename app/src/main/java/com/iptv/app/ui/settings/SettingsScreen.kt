package com.iptv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var pin by remember { mutableStateOf(s.parentalPin) }

    LaunchedEffect(s.parentalPin) { pin = s.parentalPin }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = TvDim.ScreenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Configurações", style = MaterialTheme.typography.headlineSmall)
        Text("Servidor", style = MaterialTheme.typography.titleMedium)
        Text("Host: ${s.host}", style = MaterialTheme.typography.bodyMedium)
        Text("Usuário: ${s.username}", style = MaterialTheme.typography.bodyMedium)

        Text("Senha parental", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.width(360.dp)
        )
        Button(onClick = { settingsVm.setPin(pin) }) { Text("Salvar PIN") }

        Text("Catálogo", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { vm.refreshAll() }) { Text("Atualizar catálogo") }
        Button(onClick = { settingsVm.resetProgress() }) { Text("Resetar progresso de séries") }

        Text("Sessão", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            vm.logout()
            onLogout()
        }) { Text("Sair") }
    }
}
