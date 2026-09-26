package com.example.phoneagent.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.layout.screenMargin
import com.example.phoneagent.ui.designsystem.theme.AppTheme

internal const val ABOUT_CONTENT_LIST_TAG = "about-content-list"
internal const val ABOUT_APPLICATION_SECTION_TAG = "about-application-section"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    licenseTexts: Map<LicenseKind, String>,
    expandedLicenseKinds: Set<LicenseKind>,
    onToggleLicense: (LicenseKind) -> Unit,
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { AboutTopAppBar(onBack) },
    ) { insets ->
        AboutContent(
            insets = insets,
            versionName = versionName,
            licenseTexts = licenseTexts,
            expandedLicenseKinds = expandedLicenseKinds,
            onToggleLicense = onToggleLicense,
            onOpenProject = onOpenProject,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutTopAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.about_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(AppIcons.back),
                    contentDescription = stringResource(R.string.go_back),
                )
            }
        },
    )
}

@Composable
private fun AboutContent(
    insets: PaddingValues,
    versionName: String,
    licenseTexts: Map<LicenseKind, String>,
    expandedLicenseKinds: Set<LicenseKind>,
    onToggleLicense: (LicenseKind) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val margin = screenMargin()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ABOUT_CONTENT_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = insets.calculateStartPadding(direction) + margin,
                end = insets.calculateEndPadding(direction) + margin,
                top = insets.calculateTopPadding() + AppTheme.spacing.small,
                bottom = insets.calculateBottomPadding() + AppTheme.spacing.large,
            ),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        item(key = "about_application") {
            AboutApplicationSection(versionName)
        }
        item(key = "about_fonts") {
            LicenseEntriesSection(
                title = stringResource(R.string.about_section_fonts),
                entries = LicenseCatalog.fonts,
                onOpenProject = onOpenProject,
            )
        }
        item(key = "about_icons") {
            LicenseEntriesSection(
                title = stringResource(R.string.about_section_icons),
                entries = LicenseCatalog.icons,
                onOpenProject = onOpenProject,
            )
        }
        item(key = "about_libraries") {
            LicenseEntriesSection(
                title = stringResource(R.string.about_section_libraries),
                entries = LicenseCatalog.libraries,
                onOpenProject = onOpenProject,
            )
        }
        item(key = "about_license_texts_heading") {
            Text(
                text = stringResource(R.string.about_license_texts_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.contentColumn().semantics { heading() },
            )
        }
        LicenseKind.entries.forEach { kind ->
            item(key = "license_${kind.name}") {
                LicenseTextSection(
                    kind = kind,
                    text = licenseTexts.getValue(kind),
                    expanded = kind in expandedLicenseKinds,
                    onToggle = { onToggleLicense(kind) },
                )
            }
        }
    }
}

@Composable
private fun AboutApplicationSection(versionName: String) {
    Surface(
        modifier = Modifier.contentColumn().testTag(ABOUT_APPLICATION_SECTION_TAG),
        shape = AppTheme.shapeTokens.card,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.about_summary), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_local_data), style = MaterialTheme.typography.bodySmall)
        }
    }
}
