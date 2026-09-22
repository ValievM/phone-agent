package com.example.phoneagent.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.phoneagent.R
import com.example.phoneagent.ui.chat.ChatRoute
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppTheme

sealed interface AppDestination {
    data object Chat : AppDestination

    data object Settings : AppDestination
}

@Composable
fun AppNavigation(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = remember { mutableStateListOf<AppDestination>(AppDestination.Chat) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                AppDestination.Chat -> {
                    NavEntry(key) {
                        ChatRoute(onOpenSettings = { backStack += AppDestination.Settings })
                    }
                }

                AppDestination.Settings -> {
                    NavEntry(key) {
                        SettingsPlaceholder(onBack = { backStack.removeLastOrNull() }, onSignOut = onSignOut)
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPlaceholder(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(AppIcons.back),
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(AppTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_placeholder),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
            androidx.compose.material3.Button(onClick = onSignOut) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}
