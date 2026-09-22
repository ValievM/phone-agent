package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.serialization.json.JsonObject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isRunning: Boolean = false,
    val error: AgentError? = null,
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    /** Non-null only for [ChatRole.TOOL]; drives [com.example.phoneagent.ui.designsystem.components.ToolCallCard]. */
    val toolCall: ToolCallMessage? = null,
)

data class ToolCallMessage(
    val status: ToolCallStatus,
    val description: CallDescription,
    val arguments: JsonObject,
    val expanded: Boolean = false,
    val fileHits: List<FileHit> = emptyList(),
)

enum class ChatRole {
    USER,
    ASSISTANT,
    TOOL,
}

sealed interface ChatUiEvent {
    data class Submit(
        val text: String,
    ) : ChatUiEvent

    data object Stop : ChatUiEvent

    data class ToggleToolCallDetails(
        val messageId: String,
    ) : ChatUiEvent

    data object DismissError : ChatUiEvent
}
