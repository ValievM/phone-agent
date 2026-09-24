package com.example.phoneagent.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.AssistantMessage
import com.example.phoneagent.ui.designsystem.components.EmptyState
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallCard
import com.example.phoneagent.ui.designsystem.components.UserMessage
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
internal fun MessageList(
    messages: List<ChatMessage>,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    inlineError: (@Composable () -> Unit)? = null,
) {
    if (messages.isEmpty()) {
        // An error can happen before the first message (e.g. history failed to load); the inline
        // error must be visible here too, not only inside a non-empty feed.
        Column(modifier = modifier) {
            EmptyState(
                suggestions =
                    listOf(
                        stringResource(R.string.chat_suggestion_alarm),
                        stringResource(R.string.chat_suggestion_find_file),
                        stringResource(R.string.chat_suggestion_notify),
                    ),
                onSuggestionClick = onSuggestionClick,
                modifier = Modifier.weight(1f),
            )
            inlineError?.invoke()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            // The feed is reversed: the first item is drawn last, i.e. after the newest message.
            inlineError?.let { error -> item(key = INLINE_ERROR_KEY, contentType = INLINE_ERROR_KEY) { error() } }
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

private const val INLINE_ERROR_KEY = "inline_error"
