package com.example.phoneagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.SettingsRepository
import com.example.phoneagent.interaction.UserInteractionBridge
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import com.example.phoneagent.ui.interaction.InteractionHost
import com.example.phoneagent.ui.login.LoginRoute
import com.example.phoneagent.ui.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userInteractionBridge: UserInteractionBridge

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val appSettings = MutableStateFlow<AppSettings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Held until the saved theme is known, so the app never flashes in the wrong theme.
        installSplashScreen().setKeepOnScreenCondition { appSettings.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { settingsRepository.observe().collect { appSettings.value = it } }
        setContent {
            val settings by appSettings.collectAsStateWithLifecycle()
            settings?.let { current ->
                AppTheme(
                    darkTheme = current.useDarkTheme ?: isSystemInDarkTheme(),
                    useDynamicColor = current.useDynamicColors,
                ) {
                    LoginRoute(authenticatedContent = { AppNavigation() })
                    InteractionHost(bridge = userInteractionBridge)
                }
            }
        }
    }
}
