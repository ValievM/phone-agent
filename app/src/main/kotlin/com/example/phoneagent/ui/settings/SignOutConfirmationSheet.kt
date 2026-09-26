package com.example.phoneagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.ConfirmationSheet
import com.example.phoneagent.ui.designsystem.components.PrimaryButton
import com.example.phoneagent.ui.designsystem.components.QuietButton
import com.example.phoneagent.ui.designsystem.components.SecondaryButton
import com.example.phoneagent.ui.designsystem.components.SheetLayout
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
internal fun SignOutConfirmationSheet(
    onDeleteHistory: () -> Unit,
    onKeepHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationSheet(onDismissRequest = onDismiss) {
        SheetLayout(
            title = stringResource(R.string.settings_sign_out_confirm_title),
            footer = {
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
                    QuietButton(
                        onClick = onDeleteHistory,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.settings_sign_out_delete_history))
                    }
                    PrimaryButton(onClick = onKeepHistory, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_sign_out_keep_history))
                    }
                    SecondaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.file_cancel_action))
                    }
                }
            },
        ) {
            Text(
                text = stringResource(R.string.settings_sign_out_confirm_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsSignOutConfirmation(
    visible: Boolean,
    onSignOut: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (visible) {
        SignOutConfirmationSheet(
            onDeleteHistory = { onSignOut(true) },
            onKeepHistory = { onSignOut(false) },
            onDismiss = onDismiss,
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun SignOutConfirmationPreview() {
    AppTheme(useDynamicColor = false) {
        SignOutConfirmationSheet(onDeleteHistory = {}, onKeepHistory = {}, onDismiss = {})
    }
}
