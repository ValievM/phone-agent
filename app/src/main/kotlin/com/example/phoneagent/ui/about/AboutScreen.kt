package com.example.phoneagent.ui.about

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.rememberReducedMotion
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.layout.screenMargin
import com.example.phoneagent.ui.designsystem.theme.AppMotion
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import com.example.phoneagent.ui.settings.SettingsSection

internal const val ABOUT_CONTENT_LIST_TAG = "about-content-list"
internal const val ABOUT_APPLICATION_SECTION_TAG = "about-application-section"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    licenseTexts: Map<LicenseKind, String>,
    expandedLicenseKinds: Set<LicenseKind>,
    onToggleLicense: (LicenseKind) -> Unit,
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
            )
        }
        item(key = "about_icons") {
            LicenseEntriesSection(
                title = stringResource(R.string.about_section_icons),
                entries = LicenseCatalog.icons,
            )
        }
        item(key = "about_libraries") {
            LicenseEntriesSection(
                title = stringResource(R.string.about_section_libraries),
                entries = LicenseCatalog.libraries,
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
    SettingsSection(title = stringResource(R.string.about_section_application)) {
        Column(modifier = Modifier.fillMaxWidth().testTag(ABOUT_APPLICATION_SECTION_TAG)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_version, versionName),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.about_local_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LicenseEntriesSection(
    title: String,
    entries: List<LicenseEntry>,
) {
    SettingsSection(title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)) {
            entries.forEach { entry ->
                LicenseEntryContent(entry)
            }
        }
    }
}

@Composable
private fun LicenseEntryContent(entry: LicenseEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
        Text(entry.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("about-entry-${entry.name}"))
        LicenseAttributionContent(
            holder = entry.holder,
            license = entry.license,
            testTag = "about-license-${entry.name}-0",
        )
        entry.additionalNotices.forEachIndexed { index, notice ->
            LicenseAttributionContent(
                holder = notice.holder,
                license = notice.license,
                testTag = "about-license-${entry.name}-${index + 1}",
            )
        }
        Text(
            stringResource(R.string.about_project_source, entry.source),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicenseAttributionContent(
    holder: String,
    license: LicenseKind,
    testTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
        Text(
            stringResource(R.string.about_rights_holder, holder),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("$testTag-holder"),
        )
        val licenseName = stringResource(LicenseTextResources.nameResourceIds.getValue(license))
        Text(
            stringResource(R.string.about_license_name, licenseName),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun LicenseTextToggle(
    licenseName: String,
    expanded: Boolean,
    stateLabel: String,
    chevronRotation: Float,
    onToggle: () -> Unit,
) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.semantics { stateDescription = stateLabel },
    ) {
        Text(stringResource(if (expanded) R.string.about_hide_license else R.string.about_show_license, licenseName))
        Icon(
            painter = painterResource(AppIcons.expandMore),
            contentDescription = null,
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}

@Composable
private fun LicenseTextSection(
    kind: LicenseKind,
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val licenseName = stringResource(LicenseTextResources.nameResourceIds.getValue(kind))
    val stateLabel = stringResource(if (expanded) R.string.about_expanded_state else R.string.about_collapsed_state)
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec =
                if (reducedMotion) {
                    snap()
                } else {
                    tween(durationMillis = AppMotion.EXPAND_MILLIS, easing = AppMotion.Standard)
                },
            label = "license-chevron",
        )
    Surface(
        modifier =
            Modifier
                .contentColumn()
                .animateContentSize(
                    animationSpec =
                        if (reducedMotion) {
                            snap()
                        } else {
                            tween(durationMillis = AppMotion.EXPAND_MILLIS, easing = AppMotion.Standard)
                        },
                ),
        shape = AppTheme.shapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact),
        ) {
            Text(
                stringResource(R.string.about_license_text_title, licenseName),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            LicenseTextToggle(
                licenseName = licenseName,
                expanded = expanded,
                stateLabel = stateLabel,
                chevronRotation = chevronRotation,
                onToggle = onToggle,
            )
            if (expanded) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
