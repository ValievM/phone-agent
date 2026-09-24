package com.example.phoneagent.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.AssistantMessage
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallCard
import com.example.phoneagent.ui.designsystem.components.UserMessage
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
internal fun MessageList(
    messages: List<ChatMessage>,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        EmptyChat(modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            items(
                items = messages.asReversed(),
                key = ChatMessage::id,
                contentType = ChatMessage::role,
            ) { message ->
                Message(message, onEvent, onOpenFile)
            }
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            Text(
                text = stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.chat_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun Message(
    message: ChatMessage,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolCall = message.toolCall
    when {
        toolCall != null -> {
            ToolCallCard(
                state = toolCall.status,
                description = toolCall.description,
                arguments = toolCall.arguments,
                expanded = toolCall.expanded,
                onExpandToggle = { onEvent(ChatUiEvent.ToggleToolCallDetails(message.id)) },
                modifier = modifier,
                fileHits = toolCall.fileHits,
                onOpenFile = onOpenFile,
            )
        }

        message.role == ChatRole.USER -> {
            UserMessage(message.text, modifier)
        }

        else -> {
            AssistantMessage(message.text, modifier)
        }
    }
}
