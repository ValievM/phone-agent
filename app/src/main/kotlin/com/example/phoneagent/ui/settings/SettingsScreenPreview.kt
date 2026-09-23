package com.example.phoneagent.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.phoneagent.auth.AppSignInMethod
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Preview(showBackground = true)
@Composable
private fun SettingsScreenSignedInPreview() {
    AppTheme {
        SettingsScreen(
            state =
                SettingsUiState(
                    isLoading = false,
                    model = "gpt-5.6-luna",
                    availableModels = listOf("gpt-5.6-luna", "gpt-5.6-terra"),
                    isLoadingModels = false,
                    searchFolderSelected = true,
                    isSignedIn = true,
                    signInMethod = AppSignInMethod.Browser,
                ),
            onEvent = {},
            onChooseFolder = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenSignedOutPreview() {
    AppTheme {
        SettingsScreen(
            state =
                SettingsUiState(
                    isLoading = false,
                    availableModels = listOf("gpt-5.6-luna", "gpt-5.6-terra"),
                    isLoadingModels = false,
                    sessionExpired = true,
                ),
            onEvent = {},
            onChooseFolder = {},
            onBack = {},
        )
    }
}
