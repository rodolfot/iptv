package com.iptv.app.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.R
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.ui.common.FormFactor
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.rememberTvDim
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@Composable
fun OnboardingScreen(
    onAccepted: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    var viewing by remember { mutableStateOf<LegalDoc?>(null) }
    val dim = rememberTvDim()
    val isPhone = dim.formFactor == FormFactor.Phone
    // Reading the activity allows the "Decline" button to actually close the
    // screen without taking the whole process down — `exitProcess` was killing
    // the JVM, which on some launchers leaves the splash on top of the home.
    val activity = LocalContext.current as? android.app.Activity

    if (viewing != null) {
        LegalViewerScreen(doc = viewing!!, onClose = { viewing = null })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(R.string.onboarding_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Stack vertically on phones (narrow viewport) so labels never wrap
            // into the next button. TV/tablet still gets the side-by-side row.
            if (isPhone) {
                FullWidthButton(
                    label = stringResource(R.string.onboarding_read_terms),
                    onClick = { viewing = LegalDoc.TERMS }
                )
                FullWidthButton(
                    label = stringResource(R.string.onboarding_read_privacy),
                    onClick = { viewing = LegalDoc.PRIVACY }
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TouchableButton(onClick = { viewing = LegalDoc.TERMS }) {
                        Text(stringResource(R.string.onboarding_read_terms))
                    }
                    TouchableButton(onClick = { viewing = LegalDoc.PRIVACY }) {
                        Text(stringResource(R.string.onboarding_read_privacy))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Primary CTA is full-width and prominent. The "Decline" sits below
            // as a less-emphatic option — kept reachable but not the focus.
            FullWidthButton(
                label = stringResource(R.string.onboarding_accept),
                onClick = { vm.accept(onAccepted) },
                primary = true
            )
            FullWidthButton(
                label = stringResource(R.string.onboarding_decline),
                onClick = { activity?.finishAndRemoveTask() }
            )
        }
    }
}

@Composable
private fun FullWidthButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    TouchableButton(
        onClick = onClick,
        selected = primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
