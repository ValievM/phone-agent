package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.HistoryItem
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

/**
 * The feed after the saved history changed underneath it. On the first emission the restored
 * messages go before anything already typed; a later empty history means it was cleared elsewhere
 * (Settings). A history that ends with a user message on start-up means the app was closed before
 * the answer arrived: that is shown as an interrupted turn that can be retried.
 */
internal fun ChatUiState.withLoadedHistory(
    loaded: List<HistoryItem>,
    isInitial: Boolean,
    dropToolCards: Boolean,
): ChatUiState {
    val restored =
        when {
            isInitial -> loaded.toChatMessages() + messages
            loaded.isEmpty() -> emptyList()
            else -> loaded.toChatMessages() + if (dropToolCards) emptyList() else messages.filter { it.role == ChatRole.TOOL }
        }
    val unanswered = isInitial && !isRunning && loaded.lastOrNull() is HistoryItem.UserMessage
    return when {
        unanswered -> copy(messages = restored, error = AgentError.Interrupted, canRetry = true)
        !isInitial && loaded.isEmpty() -> copy(messages = restored, error = null, canRetry = false)
        else -> copy(messages = restored)
    }
}

internal fun List<HistoryItem>.toChatMessages(): List<ChatMessage> =
    mapNotNull { item ->
        when (item) {
            is HistoryItem.UserMessage -> ChatMessage(item.id, ChatRole.USER, item.text)
            is HistoryItem.AssistantMessage -> ChatMessage(item.id, ChatRole.ASSISTANT, item.text)
            is HistoryItem.ToolCallItem, is HistoryItem.ToolOutput, is HistoryItem.Opaque -> null
        }
    }
