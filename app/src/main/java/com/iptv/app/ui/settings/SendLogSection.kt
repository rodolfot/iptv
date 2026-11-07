package com.iptv.app.ui.settings

import com.iptv.app.ui.common.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.R
import com.iptv.app.diag.DiagnosticsUploader
import com.iptv.app.ui.common.TouchableButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SendLogViewModel @Inject constructor(
    private val uploader: DiagnosticsUploader
) : ViewModel() {
    sealed interface UiState {
        data object Idle : UiState
        data object Sending : UiState
        data class Done(val result: DiagnosticsUploader.Result) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state = _state.asStateFlow()

    fun send() {
        if (_state.value == UiState.Sending) return
        _state.value = UiState.Sending
        viewModelScope.launch { _state.value = UiState.Done(uploader.send()) }
    }

    fun dismiss() {
        _state.value = UiState.Idle
    }
}

/**
 * "Enviar log" (Configurações → Sobre): manda o diário do app ao portal e
 * mostra o código para passar ao suporte — útil na central multimídia, onde
 * não há como ligar um cabo e ler o logcat.
 */
@Composable
fun SendLogButton(vm: SendLogViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val sending = state == SendLogViewModel.UiState.Sending
    TouchableButton(compact = true, onClick = { vm.send() }) {
        Text(stringResource(if (sending) R.string.diag_sending else R.string.diag_send_log))
    }
    val done = state as? SendLogViewModel.UiState.Done ?: return
    AlertDialog(
        onDismissRequest = vm::dismiss,
        confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(android.R.string.ok)) } },
        title = { Text(stringResource(R.string.diag_send_log)) },
        text = {
            when (val r = done.result) {
                is DiagnosticsUploader.Result.Sent -> Text(
                    stringResource(R.string.diag_sent, r.code),
                    style = MaterialTheme.typography.titleMedium
                )
                is DiagnosticsUploader.Result.Failed -> Text(stringResource(R.string.diag_send_failed, r.reason))
            }
        }
    )
}
