package com.iptv.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.R
import com.iptv.app.data.api.XtreamRepository
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.TvSafeTextField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Returns the host(s) to try when logging in. We used to probe a handful of
 * common Xtream ports in parallel, but providers behind reverse proxies were
 * getting the wrong endpoint picked. Now we trust exactly what the user typed.
 */
internal fun portCandidatesFor(normalizedHost: String): List<String> = listOf(normalizedHost)

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
        val rawHost = host.trim()
        val withScheme = if (rawHost.startsWith("http://") || rawHost.startsWith("https://"))
            rawHost else "http://$rawHost"
        val normalizedHost = withScheme.trimEnd('/')
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
    val fieldsRequired = stringResource(R.string.login_fields_required)

    // Foco inicial no primeiro campo — mesmo padrão D-pad-safe do resto do
    // app (TvSafeTextField): o foco não abre o teclado sozinho, só marca
    // onde o OK vai agir primeiro.
    LaunchedEffect(Unit) { runCatching { hostFocus.requestFocus() } }

    if (state.success) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            // Dispara o bootstrap por categoria em background — popula o
            // cache enquanto o usuário começa a navegar. Cada categoria
            // visitada antes do worker chegar nela cai no fetch on-demand
            // do load*() (~2s).
            com.iptv.app.work.CatalogRefreshWorker.enqueueOneShot(ctx)
            onLogged()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.login_brand), style = MaterialTheme.typography.displayMedium)
            Text(
                stringResource(R.string.login_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            TvSafeTextField(
                value = host,
                onValueChange = { host = it },
                label = stringResource(R.string.login_host),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().focusRequester(hostFocus)
            )
            TvSafeTextField(
                value = user,
                onValueChange = { user = it },
                label = stringResource(R.string.login_user),
                modifier = Modifier.fillMaxWidth()
            )
            TvSafeTextField(
                value = pass,
                onValueChange = { pass = it },
                label = stringResource(R.string.login_pass),
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TouchableButton(
                onClick = { vm.login(host, user, pass, fieldsRequired) },
                selected = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    stringResource(if (state.loading) R.string.login_connecting else R.string.login_button),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
