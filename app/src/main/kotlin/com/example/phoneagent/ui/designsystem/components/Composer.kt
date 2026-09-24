package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppTheme

/**
 * Message input. While [isRunning] the text field is disabled and the button turns into "stop";
 * the draft survives configuration changes and process death via [rememberSaveable].
 */
@Composable
fun Composer(
    enabled: Boolean,
    isRunning: Boolean,
    onSubmit: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val submit = {
        if (draft.isNotBlank() && enabled) {
            onSubmit(draft)
            draft = ""
            keyboardController?.hide()
        }
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding()
                .padding(AppTheme.spacing.medium),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            label = { Text(stringResource(R.string.message_hint)) },
            maxLines = COMPOSER_MAX_LINES,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        FilledIconButton(
            onClick = { if (isRunning) onStop() else submit() },
            enabled = isRunning || (enabled && draft.isNotBlank()),
        ) {
            Icon(
                painter = painterResource(if (isRunning) AppIcons.stop else AppIcons.send),
                contentDescription =
                    stringResource(
                        if (isRunning) R.string.stop_generation else R.string.send_message,
                    ),
            )
        }
    }
}

private const val COMPOSER_MAX_LINES = 6
