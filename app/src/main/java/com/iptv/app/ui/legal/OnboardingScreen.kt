package com.iptv.app.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R
import com.iptv.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore
) : ViewModel() {
    fun accept(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.acceptTerms()
            onDone()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onAccepted: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    var viewing by remember { mutableStateOf<LegalDoc?>(null) }

    if (viewing != null) {
        LegalViewerScreen(doc = viewing!!, onClose = { viewing = null })
        return
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(720.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                stringResource(R.string.onboarding_message),
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewing = LegalDoc.TERMS }) {
                    Text(stringResource(R.string.onboarding_read_terms))
                }
                Button(onClick = { viewing = LegalDoc.PRIVACY }) {
                    Text(stringResource(R.string.onboarding_read_privacy))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.accept(onAccepted) }) {
                    Text(stringResource(R.string.onboarding_accept))
                }
                Button(onClick = { exitProcess(0) }) {
                    Text(stringResource(R.string.onboarding_decline))
                }
            }
        }
    }
}
