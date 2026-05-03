package com.iptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
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

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsStore
) : ViewModel() {
    val isLoggedIn = settings.flow
        .map { it.isLoggedIn && it.host.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

@Composable
fun AppNav(vm: RootViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val nav = rememberNavController()
    val logged by vm.isLoggedIn.collectAsState()
    val start = if (logged) "home" else "login"
    NavHost(navController = nav, startDestination = start) {
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
