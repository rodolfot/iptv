package com.iptv.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.ui.home.HomeScreen
import com.iptv.app.ui.legal.OnboardingScreen
import com.iptv.app.ui.login.LoginScreen
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerScreen
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

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            IptvTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppNav()
                }
            }
        }
    }
}

data class RootState(val termsAccepted: Boolean = false, val loggedIn: Boolean = false)

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsStore
) : ViewModel() {
    val state = settings.flow
        .map { RootState(termsAccepted = it.termsAccepted, loggedIn = it.isLoggedIn && it.host.isNotBlank()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState())
}

@Composable
fun AppNav(vm: RootViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
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
