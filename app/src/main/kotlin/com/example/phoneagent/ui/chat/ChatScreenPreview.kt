package com.example.phoneagent.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@PreviewLightDark
@Composable
private fun EmptyChatPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(ChatUiState(), {}, {})
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ConversationChatPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(
            state =
                ChatUiState(
                    messages =
                        listOf(
                            ChatMessage("1", ChatRole.USER, stringResource(R.string.preview_user_message)),
                            ChatMessage("2", ChatRole.ASSISTANT, stringResource(R.string.preview_assistant_message)),
                        ),
                ),
            onEvent = {},
            onOpenSettings = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun LoadingChatPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(ChatUiState(isRunning = true), {}, {})
    }
}

@PreviewLightDark
@Composable
private fun ErrorChatPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(ChatUiState(error = AgentError.Offline), {}, {})
    }
}
