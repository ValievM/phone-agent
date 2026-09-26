package com.example.phoneagent.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.R
import com.example.phoneagent.ui.readAppVersionName

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val unavailableVersionLabel = stringResource(R.string.about_version_unavailable)
    val appVersionName =
        remember(context.packageManager, context.packageName, unavailableVersionLabel) {
            readAppVersionName(context.packageManager, context.packageName, unavailableVersionLabel)
        }
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                val accessFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, accessFlags)
                    viewModel.onEvent(SettingsUiEvent.SaveSearchFolder(uri.toString()))
                } catch (_: SecurityException) {
                    viewModel.onEvent(SettingsUiEvent.SearchFolderPermissionUnavailable)
                }
            }
        }

    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onChooseFolder = { folderPicker.launch(null) },
        onBack = onBack,
        aboutEntry = SettingsAboutEntry(versionName = appVersionName, onOpen = onOpenAbout),
        modifier = modifier,
    )
}
