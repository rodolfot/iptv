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
    val state = settings.flow

    fun setLocale(tag: String?) {
        viewModelScope.launch {
            settings.setAppLocale(tag)
            if (tag.isNullOrBlank()) com.iptv.app.ui.common.LocaleManager.resetToSystem()
            else com.iptv.app.ui.common.LocaleManager.apply(tag)
        }
    }

    fun setDeviceProfile(profile: com.iptv.app.data.prefs.DeviceProfile?) {
        viewModelScope.launch { settings.setDeviceProfile(profile) }
    }

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
    // Remember which document the user opened so we can return focus to the
    // matching button when they come back — otherwise the page re-renders
    // with focus at the very top, forcing the user to scroll down again.
    var lastViewed by remember { mutableStateOf<LegalDoc?>(null) }
    val dim = rememberTvDim()
    val isPhone = dim.formFactor == FormFactor.Phone
    val settingsState by vm.state.collectAsState(initial = com.iptv.app.data.prefs.AppSettings())
    val activity = LocalContext.current as? android.app.Activity

    val termsFocus = remember { FocusRequester() }
    val privacyFocus = remember { FocusRequester() }
    LaunchedEffect(viewing, lastViewed) {
        if (viewing == null && lastViewed != null) {
            runCatching {
                when (lastViewed) {
                    LegalDoc.TERMS -> termsFocus.requestFocus()
                    LegalDoc.PRIVACY -> privacyFocus.requestFocus()
                    null -> {}
                }
            }
            lastViewed = null
        }
    }

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
        // TopCenter (em vez de Center) garante que o topo da página fica
        // sempre visível em telas curtas — antes o título "Bem-vindo ao
        // TartaTV" era cortado pelo Center quando o conteúdo crescia.
        contentAlignment = Alignment.TopCenter
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

            // First-run language picker. Tapping a row immediately swaps the
            // app locale, so the rest of this very screen re-renders in the
            // chosen language as confirmation. Combo (one button + dialog)
            // keeps the screen short on TV — the old vertical list pushed the
            // primary CTAs below the fold.
            Text(
                stringResource(R.string.onboarding_language_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            run {
                val options = com.iptv.app.ui.common.LocaleManager.available.map {
                    com.iptv.app.ui.common.ComboOption(
                        id = it.tag ?: "__system__",
                        label = it.display
                    )
                }
                val currentKey = settingsState.appLocale ?: "__system__"
                val current = options.firstOrNull { it.id == currentKey }
                com.iptv.app.ui.common.ComboBox(
                    selected = current,
                    options = options,
                    onSelect = {
                        val tag = if (it.id == "__system__") null else it.id
                        vm.setLocale(tag)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Tipo de dispositivo: detecção automática às vezes erra
            // (TVs reportando smallestScreenWidthDp baixo, etc.). Deixar o
            // usuário escolher é o jeito mais confiável.
            Text(
                stringResource(R.string.onboarding_device_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
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
                val currentKey = settingsState.deviceProfile?.name ?: "__auto__"
                val current = options.firstOrNull { it.id == currentKey }
                com.iptv.app.ui.common.ComboBox(
                    selected = current,
                    options = options,
                    onSelect = {
                        val profile = if (it.id == "__auto__") null
                            else com.iptv.app.data.prefs.DeviceProfile.valueOf(it.id)
                        vm.setDeviceProfile(profile)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Stack vertically on phones (narrow viewport) so labels never wrap
            // into the next button. TV/tablet still gets the side-by-side row.
            if (isPhone) {
                FullWidthButton(
                    label = stringResource(R.string.onboarding_read_terms),
                    onClick = { lastViewed = LegalDoc.TERMS; viewing = LegalDoc.TERMS },
                    modifier = Modifier.focusRequester(termsFocus)
                )
                FullWidthButton(
                    label = stringResource(R.string.onboarding_read_privacy),
                    onClick = { lastViewed = LegalDoc.PRIVACY; viewing = LegalDoc.PRIVACY },
                    modifier = Modifier.focusRequester(privacyFocus)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TouchableButton(
                        onClick = { lastViewed = LegalDoc.TERMS; viewing = LegalDoc.TERMS },
                        modifier = Modifier.focusRequester(termsFocus)
                    ) {
                        Text(stringResource(R.string.onboarding_read_terms))
                    }
                    TouchableButton(
                        onClick = { lastViewed = LegalDoc.PRIVACY; viewing = LegalDoc.PRIVACY },
                        modifier = Modifier.focusRequester(privacyFocus)
                    ) {
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
    primary: Boolean = false,
    modifier: Modifier = Modifier
) {
    TouchableButton(
        onClick = onClick,
        selected = primary,
        modifier = Modifier.fillMaxWidth().then(modifier)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
