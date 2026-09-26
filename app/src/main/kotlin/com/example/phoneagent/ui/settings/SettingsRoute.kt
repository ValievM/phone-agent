package com.example.phoneagent.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.R
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.ui.readAppVersionName
import kotlinx.coroutines.withContext

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    dispatcherProvider: DispatcherProvider,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val unavailableVersionLabel = stringResource(R.string.about_version_unavailable)
    val appVersionName =
        remember(context.packageManager, context.packageName, unavailableVersionLabel) {
            readAppVersionName(context.packageManager, context.packageName, unavailableVersionLabel)
        }
    val searchFolderDisplayName by
        produceState<String?>(
            initialValue = null,
            key1 = state.searchFolderUri,
            key2 = contentResolver,
        ) {
            value =
                state.searchFolderUri?.let { treeUri ->
                    withContext(dispatcherProvider.io) { readSearchFolderDisplayName(contentResolver, treeUri) }
                }
        }
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                val accessFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, accessFlags)
                    viewModel.onEvent(SettingsUiEvent.SaveSearchFolder(uri.toString()))
                } catch (_: SecurityException) {
                    viewModel.onEvent(SettingsUiEvent.SearchFolderPermissionUnavailable)
                }
            }
        }

    SettingsScreen(
        state = state,
        selectedFolderName = searchFolderDisplayName,
        onEvent = viewModel::onEvent,
        onChooseFolder = { folderPicker.launch(null) },
        onBack = onBack,
        aboutEntry = SettingsAboutEntry(versionName = appVersionName, onOpen = onOpenAbout),
        modifier = modifier,
    )
}
