package com.iptv.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.R
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

    fun login(host: String, user: String, pass: String, errorFieldsRequired: String) {
        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            _state.value = LoginUiState(error = errorFieldsRequired)
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
                .onFailure {
                    val msg = it.message.orEmpty()
                    val friendly = when {
                        msg.contains("404") -> "Servidor respondeu 404. Verifique se o host inclui a porta correta (ex.: http://seu-servidor.com:8080)."
                        msg.contains("401") || msg.contains("403") -> "Credenciais recusadas pelo servidor."
                        msg.contains("UnknownHost", ignoreCase = true) -> "Host não encontrado. Confira o endereço."
                        msg.contains("timeout", ignoreCase = true) -> "Tempo esgotado conectando ao servidor."
                        else -> msg.ifBlank { "Erro de conexão" }
                    }
                    _state.value = LoginUiState(error = friendly)
                }
        }
    }
}

@Composable
fun LoginScreen(
    onLogged: () -> Unit,
    vm: LoginViewModel = hiltViewModel()
) {
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    val hostFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldsRequired = stringResource(R.string.login_fields_required)

    LaunchedEffect(Unit) {
        hostFocus.requestFocus()
        keyboard?.show()
    }

    if (state.success) {
        LaunchedEffect(Unit) { onLogged() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(640.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(stringResource(R.string.login_brand), style = MaterialTheme.typography.displayMedium)
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.login_host)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(hostFocus)
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(stringResource(R.string.login_user)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(userFocus)
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(stringResource(R.string.login_pass)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    vm.login(host, user, pass, fieldsRequired)
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(passFocus)
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { vm.login(host, user, pass, fieldsRequired) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.loading) R.string.login_connecting else R.string.login_button))
            }
        }
    }
}
