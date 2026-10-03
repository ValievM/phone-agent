package com.example.phoneagent

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.interaction.UserInteractionBridge
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import com.example.phoneagent.ui.designsystem.theme.resolveTheme
import com.example.phoneagent.ui.interaction.InteractionHost
import com.example.phoneagent.ui.login.LoginRoute
import com.example.phoneagent.ui.navigation.AppNavigation
import com.example.phoneagent.ui.root.ThemeState
import com.example.phoneagent.ui.root.ThemeViewModel
import com.example.phoneagent.ui.root.ThemedRoot
import com.example.phoneagent.ui.root.settings
import com.example.phoneagent.ui.voice.VoiceHost
import com.example.phoneagent.voice.SpeechRequestBridge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userInteractionBridge: UserInteractionBridge

    @Inject
    lateinit var speechRequestBridge: SpeechRequestBridge

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var authCoordinator: AuthCoordinator

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Held until the saved theme is known, so the app never flashes in the wrong theme; but never
        // longer than SPLASH_MAX_MILLIS, so a slow read cannot leave the user on the splash forever.
        val splashDeadlineMillis = SystemClock.elapsedRealtime() + SPLASH_MAX_MILLIS
        installSplashScreen().setKeepOnScreenCondition {
            shouldKeepSplashVisible(
                themeState = themeViewModel.state.value,
                authStatus = authCoordinator.state.value,
                nowMillis = SystemClock.elapsedRealtime(),
                deadlineMillis = splashDeadlineMillis,
            )
        }
        super.onCreate(savedInstanceState)
        setContent {
            val themeState by themeViewModel.state.collectAsStateWithLifecycle()
            val settings = themeState.settings
            val theme =
                resolveTheme(
                    useDarkTheme = settings?.useDarkTheme,
                    systemDark = isSystemInDarkTheme(),
                    useDynamicColors = settings?.useDynamicColors ?: false,
                    sdkInt = Build.VERSION.SDK_INT,
                )
            // The system bars follow the theme the user chose, not only the system's: without this a
            // "light" choice on a dark phone would leave white status icons on a light screen.
            DisposableEffect(theme.dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { theme.dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { theme.dark },
                )
                onDispose {}
            }
            AppTheme(darkTheme = theme.dark, useDynamicColor = theme.dynamicColor) {
                ThemedRoot(state = themeState, onRetry = themeViewModel::retry) {
                    LoginRoute(authenticatedContent = { AppNavigation(dispatcherProvider = dispatcherProvider) })
                    InteractionHost(bridge = userInteractionBridge)
                    VoiceHost(bridge = speechRequestBridge)
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MAX_MILLIS = 1_500L

        // The scrims of the default edge-to-edge style, used by 3-button navigation only.
        val LIGHT_NAV_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_NAV_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
