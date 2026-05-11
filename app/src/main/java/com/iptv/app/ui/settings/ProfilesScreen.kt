package com.iptv.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptv.app.R
import com.iptv.app.data.prefs.Profile
import com.iptv.app.data.prefs.ProviderType
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.TouchableButton
import com.iptv.app.ui.common.rememberTvDim

@Composable
fun ProfilesScreen(
    vm: SettingsViewModel,
    settingsStoreFlow: kotlinx.coroutines.flow.Flow<com.iptv.app.data.prefs.AppSettings>,
    onClose: () -> Unit,
    onProfileActivated: () -> Unit
) {
    BackHandler(onBack = onClose)
    val s by settingsStoreFlow.collectAsState(initial = com.iptv.app.data.prefs.AppSettings())
    val dim = rememberTvDim()
    val snackbar = LocalSnackbar.current
    val savedMsg = stringResource(R.string.snack_profile_saved)
    val deletedMsg = stringResource(R.string.snack_profile_deleted)
    val switchedMsg = stringResource(R.string.snack_profile_switched)
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        ProfileEditor(
            initial = editing,
            onCancel = { creating = false; editing = null },
            onSave = { p ->
                vm.addProfile(p)
                creating = false; editing = null
                snackbar?.show(savedMsg)
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineSmall)

        if (s.profiles.isEmpty()) {
            Text(stringResource(R.string.profiles_empty), style = MaterialTheme.typography.bodyMedium)
        }

        val orderedProfiles = s.profiles.sortedWith(
            compareByDescending<Profile> { it.id == s.activeProfileId }
                .thenByDescending { it.lastUsedAt }
                .thenBy { it.name.lowercase() }
        )
        orderedProfiles.forEach { p ->
            val isActive = p.id == s.activeProfileId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        if (isActive) {
                            Text(
                                "  • ${stringResource(R.string.profiles_active_badge)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "${p.host} · ${p.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (p.pin != null) {
                        Text(
                            stringResource(R.string.profiles_has_pin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isActive) {
                            TouchableButton(onClick = {
                                vm.activateProfile(p.id) {
                                    snackbar?.show(switchedMsg)
                                    onProfileActivated()
                                }
                            }) { Text(stringResource(R.string.profiles_use)) }
                        }
                        TouchableButton(onClick = { editing = p }) {
                            Text(stringResource(R.string.profiles_edit))
                        }
                        if (s.profiles.size > 1) {
                            TouchableButton(onClick = {
                                vm.deleteProfile(p.id)
                                snackbar?.show(deletedMsg)
                            }) {
                                Text(stringResource(R.string.profiles_delete))
                            }
                        }
                    }
                }
            }
        }

        TouchableButton(onClick = { creating = true }) {
            Text(stringResource(R.string.profiles_add))
        }
        TouchableButton(onClick = onClose) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun ProfileEditor(
    initial: Profile?,
    onCancel: () -> Unit,
    onSave: (Profile) -> Unit
) {
    BackHandler(onBack = onCancel)
    val dim = rememberTvDim()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember { mutableStateOf(initial?.host.orEmpty()) }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var pass by remember { mutableStateOf(initial?.password.orEmpty()) }
    var pin by remember { mutableStateOf(initial?.pin.orEmpty()) }
    var kids by remember { mutableStateOf(initial?.kids ?: false) }
    var provider by remember { mutableStateOf(initial?.provider ?: ProviderType.XTREAM) }
    val isM3u = provider == ProviderType.M3U

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = dim.ScreenPadding, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(if (initial == null) R.string.profiles_new else R.string.profiles_edit),
            style = MaterialTheme.typography.headlineSmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TouchableButton(
                onClick = { provider = ProviderType.XTREAM },
                selected = provider == ProviderType.XTREAM
            ) { Text(stringResource(R.string.profile_provider_xtream)) }
            TouchableButton(
                onClick = { provider = ProviderType.M3U },
                selected = provider == ProviderType.M3U
            ) { Text(stringResource(R.string.profile_provider_m3u)) }
        }

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.profiles_name_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = {
                Text(stringResource(
                    if (isM3u) R.string.profile_m3u_url_label else R.string.login_host
                ))
            },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        if (!isM3u) {
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text(stringResource(R.string.login_user)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text(stringResource(R.string.login_pass)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
        OutlinedTextField(
            value = pin, onValueChange = { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.profiles_pin_optional)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchableButton(onClick = { kids = !kids }, selected = kids) {
                Text(stringResource(R.string.profiles_kids))
            }
            Text(
                stringResource(R.string.profiles_kids_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchableButton(
                enabled = name.isNotBlank() && host.isNotBlank() &&
                    (isM3u || (user.isNotBlank() && pass.isNotBlank())),
                onClick = {
                    val cleanHost = host.trim().let {
                        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
                    }.trimEnd('/')
                    onSave(
                        Profile(
                            id = initial?.id ?: Profile.newId(),
                            name = name.trim(),
                            host = cleanHost,
                            username = if (isM3u) "" else user.trim(),
                            password = if (isM3u) "" else pass.trim(),
                            pin = pin.ifBlank { null },
                            lastUsedAt = initial?.lastUsedAt ?: 0L,
                            kids = kids,
                            allowedCategories = initial?.allowedCategories.orEmpty(),
                            provider = provider
                        )
                    )
                }
            ) { Text(stringResource(R.string.profiles_save)) }
            TouchableButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}
