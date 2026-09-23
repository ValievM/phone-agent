package com.example.phoneagent.ui.settings

import com.example.phoneagent.auth.AppSignInMethod

sealed interface SettingsUiEvent {
    data class SelectModel(
        val model: String?,
    ) : SettingsUiEvent

    data object SignOut : SettingsUiEvent

    data class SaveSearchFolder(
        val uri: String?,
    ) : SettingsUiEvent

    data object ClearHistory : SettingsUiEvent

    data object SearchFolderPermissionUnavailable : SettingsUiEvent
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val model: String? = null,
    val availableModels: List<String> = emptyList(),
    val modelCatalogUnavailable: Boolean = false,
    val isLoadingModels: Boolean = true,
    val searchFolderSelected: Boolean = false,
    val folderPermissionUnavailable: Boolean = false,
    val historyClearUnavailable: Boolean = false,
    val settingsUnavailable: Boolean = false,
    val isSignedIn: Boolean = false,
    val sessionExpired: Boolean = false,
    val signInMethod: AppSignInMethod? = null,
)
