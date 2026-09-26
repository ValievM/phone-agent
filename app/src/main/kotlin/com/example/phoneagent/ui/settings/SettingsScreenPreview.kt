package com.example.phoneagent.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.auth.AppSignInMethod
import com.example.phoneagent.ui.MAX_UNTRUSTED_UI_TEXT_CHARACTERS
import com.example.phoneagent.ui.designsystem.theme.AppTheme

internal val PreviewModels = listOf("gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol")

@Composable
internal fun SettingsPreviewFrame(
    state: SettingsUiState,
    selectedFolderName: String? = null,
) {
    AppTheme(useDynamicColor = false) {
        SettingsScreen(
            state = state,
            selectedFolderName = selectedFolderName,
            onEvent = {},
            onChooseFolder = {},
            onBack = {},
            aboutEntry = SettingsAboutEntry(versionName = "0.1.0", onOpen = {}),
        )
    }
}

internal val ReadyState =
    SettingsUiState(
        isLoading = false,
        model = "gpt-5.6-luna",
        availableModels = PreviewModels,
        isLoadingModels = false,
        searchFolderSelected = true,
        isSignedIn = true,
        signInMethod = AppSignInMethod.Browser,
    )

@PreviewLightDark
@PreviewFontScale
@Composable
private fun SettingsSignedInPreview() = SettingsPreviewFrame(ReadyState)

@PreviewLightDark
@Composable
private fun SettingsSignedOutPreview() =
    SettingsPreviewFrame(ReadyState.copy(isSignedIn = false, signInMethod = null, searchFolderSelected = false))

@PreviewLightDark
@Composable
private fun SettingsSessionExpiredPreview() =
    SettingsPreviewFrame(ReadyState.copy(isSignedIn = false, signInMethod = null, sessionExpired = true))

@PreviewLightDark
@Composable
private fun SettingsLoadingPreview() = SettingsPreviewFrame(SettingsUiState(isLoading = true))

@PreviewLightDark
@PreviewFontScale
@Composable
private fun SettingsLongModelNamePreview() =
    SettingsPreviewFrame(
        ReadyState.copy(
            model = "gpt-5.6-luna-extended-context-preview-with-a-very-long-name",
            availableModels = PreviewModels + "gpt-5.6-luna-extended-context-preview-with-a-very-long-name",
        ),
    )

@PreviewLightDark
@PreviewFontScale
@Composable
private fun SettingsFolderSelectedPreview() = SettingsPreviewFrame(ReadyState, selectedFolderName = "Telegram")

@PreviewLightDark
@PreviewFontScale
@Preview(name = "320dp at 200% font", widthDp = 320, fontScale = 2f)
@Composable
private fun SettingsFolderLongNamePreview() =
    SettingsPreviewFrame(
        ReadyState,
        selectedFolderName = "Документы из папки Telegram и загрузок с длинным названием".take(MAX_UNTRUSTED_UI_TEXT_CHARACTERS),
    )
