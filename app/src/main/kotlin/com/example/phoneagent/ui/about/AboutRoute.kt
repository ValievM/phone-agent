package com.example.phoneagent.ui.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.LinkConfirmationSheet
import com.example.phoneagent.ui.designsystem.components.LinkDecision
import com.example.phoneagent.ui.designsystem.components.evaluateLink
import com.example.phoneagent.ui.openLink
import com.example.phoneagent.ui.readAppVersionName

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unavailableVersionLabel = stringResource(R.string.about_version_unavailable)
    val versionName =
        remember(context.packageManager, context.packageName, unavailableVersionLabel) {
            readAppVersionName(context.packageManager, context.packageName, unavailableVersionLabel)
        }
    val licenseTexts = remember(context.resources) { LicenseTextResources.readAll(context.resources) }
    var pendingLink by remember { mutableStateOf<LinkDecision?>(null) }
    var expandedLicenseNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val expandedLicenseKinds = LicenseKind.entries.filter { it.name in expandedLicenseNames }.toSet()

    AboutScreen(
        versionName = versionName,
        licenseTexts = licenseTexts,
        expandedLicenseKinds = expandedLicenseKinds,
        onToggleLicense = { kind ->
            expandedLicenseNames =
                if (kind.name in expandedLicenseNames) {
                    expandedLicenseNames - kind.name
                } else {
                    expandedLicenseNames + kind.name
                }
        },
        onOpenProject = { url -> pendingLink = evaluateLink(url) },
        onBack = onBack,
        modifier = modifier,
    )

    pendingLink?.let { decision ->
        LinkConfirmationSheet(
            decision = decision,
            onOpen = { confirmedUrl ->
                pendingLink = null
                openLink(context, confirmedUrl)
            },
            onDismiss = { pendingLink = null },
        )
    }
}
