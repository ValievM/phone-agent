package com.example.phoneagent.ui.chat

import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus

/** Appends streamed text to the assistant message [id], creating the message on the first chunk. */
internal fun ChatUiState.withAssistantDelta(
    id: String,
    delta: String,
): ChatUiState {
    val existing = messages.firstOrNull { it.id == id }
    val updated =
        if (existing == null) {
            messages + ChatMessage(id, ChatRole.ASSISTANT, delta)
        } else {
            messages.map { message -> if (message.id == id) message.copy(text = message.text + delta) else message }
        }
    return copy(messages = updated)
}

/** Sets the status of the tool call card [id]; [fileHits] replaces the attached hits only when given. */
internal fun ChatUiState.withToolCallStatus(
    id: String,
    status: ToolCallStatus,
    fileHits: List<FileHit>? = null,
): ChatUiState = copy(messages = messages.mapToolCall(id) { it.copy(status = status, fileHits = fileHits ?: it.fileHits) })

internal fun ChatUiState.withToolCallToggled(id: String): ChatUiState =
    copy(messages = messages.mapToolCall(id) { it.copy(expanded = !it.expanded) })

private fun List<ChatMessage>.mapToolCall(
    id: String,
    transform: (ToolCallMessage) -> ToolCallMessage,
): List<ChatMessage> =
    map { message ->
        val toolCall = message.toolCall
        if (message.id == id && toolCall != null) message.copy(toolCall = transform(toolCall)) else message
    }
