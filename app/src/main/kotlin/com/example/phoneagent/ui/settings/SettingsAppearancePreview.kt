package com.example.phoneagent.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
private fun AppearancePreview(state: SettingsUiState) {
    AppTheme(useDynamicColor = false) {
        AppearanceSection(state = state, onEvent = {})
    }
}

@Preview(name = "Appearance · ordinary", widthDp = 411, fontScale = 1f)
@Composable
private fun AppearanceOrdinaryPreview() = AppearancePreview(ReadyState)

@Preview(name = "Appearance · narrow", widthDp = 320, fontScale = 1f)
@Composable
private fun AppearanceNarrowPreview() = AppearancePreview(ReadyState)

@Preview(name = "Appearance · 200% font", widthDp = 411, fontScale = 2f)
@Composable
private fun AppearanceLargeFontPreview() = AppearancePreview(ReadyState)

@Preview(name = "Appearance · wide", widthDp = 600, fontScale = 1f)
@Composable
private fun AppearanceWidePreview() = AppearancePreview(ReadyState)
