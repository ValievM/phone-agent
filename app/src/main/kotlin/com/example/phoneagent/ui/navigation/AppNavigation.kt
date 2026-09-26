package com.example.phoneagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.ui.about.AboutRoute
import com.example.phoneagent.ui.chat.ChatRoute
import com.example.phoneagent.ui.settings.SettingsRoute

sealed interface AppDestination {
    data object Chat : AppDestination

    data object Settings : AppDestination

    data object About : AppDestination
}

@Composable
fun AppNavigation(
    dispatcherProvider: DispatcherProvider,
    modifier: Modifier = Modifier,
) {
    AppNavigationHost(
        modifier = modifier,
        chatContent = { onOpenSettings -> ChatRoute(onOpenSettings = onOpenSettings) },
        settingsContent = { onBack, onOpenAbout ->
            SettingsRoute(
                onBack = onBack,
                onOpenAbout = onOpenAbout,
                dispatcherProvider = dispatcherProvider,
            )
        },
        aboutContent = { onBack -> AboutRoute(onBack = onBack) },
    )
}

@Composable
internal fun AppNavigationHost(
    chatContent: @Composable (onOpenSettings: () -> Unit) -> Unit,
    settingsContent: @Composable (onBack: () -> Unit, onOpenAbout: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    aboutContent: @Composable (onBack: () -> Unit) -> Unit,
) {
    val backStack = remember { mutableStateListOf<AppDestination>(AppDestination.Chat) }
    // Navigation 3 scales predictive pops by default; use the same fade as an ordinary pop.
    val popTransition = remember { defaultPopTransitionSpec<AppDestination>() }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        popTransitionSpec = popTransition,
        predictivePopTransitionSpec = { popTransition.invoke(this) },
        entryProvider = { key ->
            when (key) {
                AppDestination.Chat -> {
                    NavEntry(key) {
                        chatContent { backStack += AppDestination.Settings }
                    }
                }

                AppDestination.Settings -> {
                    NavEntry(key) {
                        settingsContent(
                            { backStack.removeLastOrNull() },
                            { backStack += AppDestination.About },
                        )
                    }
                }

                AppDestination.About -> {
                    NavEntry(key) {
                        aboutContent { backStack.removeLastOrNull() }
                    }
                }
            }
        },
    )
}
