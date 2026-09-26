package com.example.phoneagent.ui.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
private fun AboutPreviewFrame(expandedLicenseKinds: Set<LicenseKind> = emptySet()) {
    val previewLicenseText = stringResource(R.string.preview_about_license_text)
    AppTheme(useDynamicColor = false) {
        AboutScreen(
            versionName = "0.1.0",
            licenseTexts = LicenseKind.entries.associateWith { previewLicenseText },
            expandedLicenseKinds = expandedLicenseKinds,
            onToggleLicense = {},
            onOpenProject = {},
            onBack = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AboutCollapsedPreview() = AboutPreviewFrame()

@PreviewLightDark
@Composable
private fun AboutExpandedPreview() = AboutPreviewFrame(setOf(LicenseKind.APACHE_2_0))

@PreviewLightDark
@PreviewFontScale
@Composable
private fun AboutLargeFontPreview() = AboutPreviewFrame()
