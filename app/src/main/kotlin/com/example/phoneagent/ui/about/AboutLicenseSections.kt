package com.example.phoneagent.ui.about

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.rememberReducedMotion
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.theme.AppCodeTextStyle
import com.example.phoneagent.ui.designsystem.theme.AppMotion
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import com.example.phoneagent.ui.settings.SettingsSection

internal const val ABOUT_PROJECT_LINK_TAG_PREFIX = "about-project-link"

@Composable
internal fun LicenseEntriesSection(
    title: String,
    entries: List<LicenseEntry>,
    onOpenProject: (String) -> Unit,
) {
    SettingsSection(title = title) {
        Column {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = AppTheme.spacing.extraSmall),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                LicenseEntryContent(entry = entry, onOpenProject = onOpenProject)
            }
        }
    }
}

@Composable
internal fun LicenseEntryContent(
    entry: LicenseEntry,
    onOpenProject: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).testTag("about-entry-${entry.name}"),
            )
            LicenseKindBadge(entry.license, "about-license-badge-${entry.name}-0")
        }
        LicenseHolder(entry.holder, "about-entry-${entry.name}-holder-0")
        entry.additionalNotices.forEachIndexed { index, notice ->
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
                LicenseKindBadge(notice.license, "about-license-badge-${entry.name}-${index + 1}")
                LicenseHolder(notice.holder, "about-entry-${entry.name}-holder-${index + 1}")
            }
        }
        val projectUrls = remember(entry.source) { entry.projectUrls() }
        projectUrls.forEachIndexed { index, url ->
            val description = stringResource(R.string.about_project_link_accessibility, entry.name)
            val label =
                if (projectUrls.size == 1) {
                    stringResource(R.string.about_project_link)
                } else {
                    stringResource(R.string.about_project_link_numbered, index + 1, projectUrls.size)
                }
            TextButton(
                onClick = { onOpenProject(url) },
                modifier =
                    Modifier
                        .testTag("$ABOUT_PROJECT_LINK_TAG_PREFIX-${entry.name}-$index")
                        .semantics { contentDescription = description },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun LicenseHolder(
    holder: String,
    tag: String,
) {
    Text(
        text = stringResource(R.string.about_rights_holder, holder),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(tag),
        softWrap = true,
    )
}

@Composable
private fun LicenseKindBadge(
    kind: LicenseKind,
    tag: String,
) {
    Surface(
        shape = AppTheme.shapeTokens.button,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(LicenseTextResources.badgeNameResourceIds.getValue(kind)),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.small, vertical = AppTheme.spacing.extraSmall).testTag(tag),
        )
    }
}

@Composable
internal fun LicenseTextSection(
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
                    style = AppCodeTextStyle,
                    modifier = Modifier.fillMaxWidth(),
                    softWrap = true,
                )
            }
        }
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

private fun LicenseEntry.projectUrls(): List<String> = source.split(';').map(String::trim).filter(String::isNotEmpty)
