package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isRunning: Boolean = false,
    val error: AgentError? = null,
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
)

enum class ChatRole {
    USER,
    ASSISTANT,

    /** Renders as a plain line for now; stage-4/tool-call-ui's ToolCallCard will replace this rendering. */
    TOOL,
}

sealed interface ChatUiEvent {
    data class Submit(
        val text: String,
    ) : ChatUiEvent

    data object Stop : ChatUiEvent

    data object DismissError : ChatUiEvent
}
