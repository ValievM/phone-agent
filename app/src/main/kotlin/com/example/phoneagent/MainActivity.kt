package com.example.phoneagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.phoneagent.interaction.UserInteractionBridge
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import com.example.phoneagent.ui.interaction.InteractionHost
import com.example.phoneagent.ui.login.LoginRoute
import com.example.phoneagent.ui.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userInteractionBridge: UserInteractionBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                LoginRoute(authenticatedContent = { AppNavigation() })
                InteractionHost(bridge = userInteractionBridge)
            }
        }
    }
}
