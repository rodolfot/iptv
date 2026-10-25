package com.iptv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.R
import com.iptv.app.premium.ActivationResult
import com.iptv.app.premium.LicenseManager
import com.iptv.app.premium.LicenseToken
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.TvSafeTextField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val license: LicenseManager
) : ViewModel() {
    val state = license.state
    val isConfigured: Boolean get() = license.isConfigured

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _result = MutableStateFlow<ActivationResult?>(null)
    val result = _result.asStateFlow()

    init {
        viewModelScope.launch { license.load() }
    }

    fun activate(key: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _result.value = license.activate(key)
            _busy.value = false
        }
    }

    fun consumeResult() {
        _result.value = null
    }
}

/**
 * Configurações → Premium (#29): ID do aparelho no formato MAC (o que a loja
 * pede para emitir a chave), status da licença e campo para a chave de 16
 * caracteres. Ativou, os anúncios somem na hora — sem reiniciar.
 */
@Composable
fun PremiumSettingsContent(vm: PremiumViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val result by vm.result.collectAsState()
    val snackbar = LocalSnackbar.current
    var key by remember { mutableStateOf("") }

    val msgSuccess = stringResource(R.string.premium_activated)
    val msgInvalid = stringResource(R.string.premium_error_invalid_key)
    val msgBound = stringResource(R.string.premium_error_bound)
    val msgNotConfigured = stringResource(R.string.premium_error_not_configured)
    val msgFailed = stringResource(R.string.premium_error_failed)
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        snackbar?.show(
            when (r) {
                ActivationResult.Success -> msgSuccess
                ActivationResult.InvalidKey -> msgInvalid
                ActivationResult.BoundToAnotherDevice -> msgBound
                ActivationResult.NotConfigured -> msgNotConfigured
                is ActivationResult.Failed -> msgFailed
            }
        )
        if (r == ActivationResult.Success) key = ""
        vm.consumeResult()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.premium_device_id, state.deviceId.ifBlank { "…" }),
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.premium) {
            Text(
                stringResource(R.string.premium_status_active, state.key.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (state.expiresAt > 0) {
                Text(
                    stringResource(
                        R.string.premium_expires,
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(state.expiresAt * 1000))
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return@Column
        }
        Text(
            stringResource(R.string.premium_status_free),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!vm.isConfigured) {
            Text(
                stringResource(R.string.premium_error_not_configured),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        TvSafeTextField(
            value = key,
            onValueChange = { input ->
                key = LicenseToken.normalizeKey(input).take(LicenseToken.KEY_LENGTH)
            },
            label = stringResource(R.string.premium_key_label),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TouchableButton(
                compact = true,
                enabled = !busy && LicenseToken.isWellFormedKey(key),
                onClick = { vm.activate(key) }
            ) { Text(stringResource(R.string.premium_activate)) }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (key.isNotEmpty()) {
                Text(
                    LicenseToken.formatKey(key),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
