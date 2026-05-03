package com.iptv.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: XtreamRepository,
    private val settings: SettingsStore
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun login(host: String, user: String, pass: String) {
        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            _state.value = LoginUiState(error = "Preencha todos os campos")
            return
        }
        val normalizedHost = host.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }.trimEnd('/')
        viewModelScope.launch {
            _state.value = LoginUiState(loading = true)
            runCatching { repo.login(normalizedHost, user.trim(), pass.trim()) }
                .onSuccess { resp ->
                    val auth = resp.userInfo?.auth
                    val ok = auth == 1 || resp.userInfo?.username != null
                    if (ok) {
                        settings.saveCredentials(normalizedHost, user.trim(), pass.trim())
                        _state.value = LoginUiState(success = true)
                    } else {
                        _state.value = LoginUiState(error = "Credenciais inválidas: ${resp.userInfo?.message ?: "auth=0"}")
                    }
                }
                .onFailure { _state.value = LoginUiState(error = it.message ?: "Erro de conexão") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogged: () -> Unit,
    vm: LoginViewModel = hiltViewModel()
) {
    var host by remember { mutableStateOf("http://bscx.one") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    if (state.success) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onLogged() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(640.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("IPTV", style = MaterialTheme.typography.displayMedium)
            Text("Conectar à sua lista", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("URL do servidor (ex: http://bscx.one)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuário") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { vm.login(host, user, pass) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.loading) "Conectando..." else "Entrar")
            }
        }
    }
}
