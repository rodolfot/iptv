package com.iptv.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.ui.home.HomeScreen
import com.iptv.app.ui.legal.OnboardingScreen
import com.iptv.app.ui.login.LoginScreen
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerScreen
import com.iptv.app.ui.common.LocalSnackbar
import com.iptv.app.ui.common.SnackbarController
import com.iptv.app.ui.player.ActivePlaybackHolder
import com.iptv.app.ui.player.LocalPlaybackHolder
import com.iptv.app.ui.theme.IptvTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** When true, leaving the app while playing puts the player into PiP. */
    var pipEnabled: Boolean = false

    val playbackHolder = ActivePlaybackHolder()

    /**
     * Aplica o locale persistido na Configuration da Activity. ComponentActivity
     * não tem o hook do AppCompat, então sem isso as strings continuariam
     * resolvendo no idioma do sistema mesmo após o usuário escolher outro.
     */
    override fun attachBaseContext(newBase: Context) {
        val localeTag = newBase.getSharedPreferences(IptvApp.LOCALE_PREFS, Context.MODE_PRIVATE)
            .getString(IptvApp.LOCALE_KEY, null)
        super.attachBaseContext(IptvApp.applyLocaleToContext(newBase, localeTag))
    }

    override fun onDestroy() {
        playbackHolder.release()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // On TV there's no PiP and no mini-player overlay to host playback
        // when the app goes to background. Pausing wasn't enough — when the
        // process is kept alive by the system, the ExoPlayer occasionally
        // resumed audio playback. Release fully when we're not in PiP and
        // the activity is actually finishing or backgrounded.
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        if (!inPip) {
            if (isFinishing) {
                playbackHolder.release()
            } else {
                playbackHolder.player?.pause()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!pipEnabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Listeners on the Compose side react via DisposableEffect, no extra code needed here.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // TV/Android entra em standby após ~10min sem input. Como app de
        // streaming, queremos manter a tela acordada o tempo todo enquanto
        // a Activity estiver em primeiro plano — não só durante o player.
        // O sistema limpa o flag automaticamente quando a Activity é
        // pausada/destruída.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            IptvTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val snackbarHostState = remember { SnackbarHostState() }
                    val scope = rememberCoroutineScope()
                    val controller = remember { SnackbarController(snackbarHostState, scope) }
                    CompositionLocalProvider(
                        LocalSnackbar provides controller,
                        LocalPlaybackHolder provides playbackHolder
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNav()
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class RootState(
    val termsAccepted: Boolean = false,
    val loggedIn: Boolean = false,
    val deviceProfile: com.iptv.app.data.prefs.DeviceProfile? = null,
    /** Falso até o primeiro emit do DataStore. Sem isso, o NavHost arranca em
     *  "onboarding" com base no estado default e dá um flash da tela de
     *  boas-vindas antes do estado real chegar (termos já aceitos). */
    val loaded: Boolean = false
)

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsStore
) : ViewModel() {
    val state = settings.flow
        .map {
            RootState(
                termsAccepted = it.termsAccepted,
                loggedIn = it.isLoggedIn && it.host.isNotBlank(),
                deviceProfile = it.deviceProfile,
                loaded = true
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState())
}

@Composable
fun AppNav(vm: RootViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by vm.state.collectAsState()
    // Override do form factor escolhido no onboarding aplica em toda a árvore.
    CompositionLocalProvider(
        com.iptv.app.ui.common.LocalDeviceProfile provides state.deviceProfile
    ) {
        // Espera o primeiro emit do DataStore + mínimo de 2 segundos antes
        // de montar o NavHost. Sem o mínimo, em TVs rápidas o splash piscava
        // (200ms); com 2s o usuário tem tempo de ver a animação e os
        // outros subsistemas (Room, WorkManager) terminam de inicializar.
        var minSplashElapsed by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            minSplashElapsed = true
        }
        if (!state.loaded || !minSplashElapsed) {
            com.iptv.app.ui.common.SplashScreen()
            return@CompositionLocalProvider
        }
        val nav = rememberNavController()
        AppNavRoutes(state, nav)
    }
}

@Composable
private fun AppNavRoutes(state: RootState, nav: androidx.navigation.NavHostController) {
    val start = when {
        !state.termsAccepted -> "onboarding"
        state.loggedIn -> "home"
        else -> "login"
    }
    NavHost(navController = nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(onAccepted = {
                nav.navigate(if (state.loggedIn) "home" else "login") {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        composable("login") {
            LoginScreen(onLogged = {
                nav.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen(
                onPlay = { args -> nav.navigate(PlayerArgs.toRoute(args)) },
                onLogout = {
                    nav.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable(PlayerArgs.ROUTE) { backStack ->
            val args = PlayerArgs.fromBackStack(backStack)
            PlayerScreen(args = args, onClose = { nav.popBackStack() })
        }
    }
}
