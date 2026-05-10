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
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        ProfileEditor(
            initial = editing,
            onCancel = { creating = false; editing = null },
            onSave = { p ->
                vm.addProfile(p)
                creating = false; editing = null
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

        s.profiles.forEach { p ->
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
                                vm.activateProfile(p.id, onActivated = onProfileActivated)
                            }) { Text(stringResource(R.string.profiles_use)) }
                        }
                        TouchableButton(onClick = { editing = p }) {
                            Text(stringResource(R.string.profiles_edit))
                        }
                        if (s.profiles.size > 1) {
                            TouchableButton(onClick = { vm.deleteProfile(p.id) }) {
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
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.profiles_name_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text(stringResource(R.string.login_host)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
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
        OutlinedTextField(
            value = pin, onValueChange = { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.profiles_pin_optional)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchableButton(
                enabled = name.isNotBlank() && host.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
                onClick = {
                    val cleanHost = host.trim().let {
                        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
                    }.trimEnd('/')
                    onSave(
                        Profile(
                            id = initial?.id ?: Profile.newId(),
                            name = name.trim(),
                            host = cleanHost,
                            username = user.trim(),
                            password = pass.trim(),
                            pin = pin.ifBlank { null }
                        )
                    )
                }
            ) { Text(stringResource(R.string.profiles_save)) }
            TouchableButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}
