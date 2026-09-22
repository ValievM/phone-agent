package com.example.phoneagent.ui.designsystem.components

import android.text.format.Formatter
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileOpenConfirmationSheet(
    fileHit: FileHit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        FileOpenConfirmationContent(
            fileHit = fileHit,
            onOpen = onOpen,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun FileOpenConfirmationContent(
    fileHit: FileHit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    val sizeLabel =
        fileHit.size?.let { size ->
            stringResource(R.string.file_size_format, Formatter.formatShortFileSize(context, size))
        } ?: stringResource(R.string.file_size_unknown)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.large)
                .padding(bottom = AppTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.file_open_confirm_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .focusable()
                    .semantics { heading() },
        )
        Text(
            text = fileHit.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = sizeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.file_cancel_action))
            }
            Button(
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.file_open_action))
            }
        }
    }
}
