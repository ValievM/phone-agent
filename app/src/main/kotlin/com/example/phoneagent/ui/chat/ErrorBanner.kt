package com.example.phoneagent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.phoneagent.R
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
internal fun ErrorBanner(
    error: AgentError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message =
        when (error) {
            AgentError.Offline -> stringResource(R.string.error_offline)
            else -> stringResource(R.string.error_generic)
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(modifier = Modifier.padding(AppTheme.spacing.extraSmall))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.dismiss_error))
        }
    }
}
