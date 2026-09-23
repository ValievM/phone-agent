package com.example.phoneagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.agent.AgentEvent
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val agentLoop: AgentLoop,
        private val conversationRepository: ConversationRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ChatUiState())
        private var nextMessageId = 0L
        private var runningJob: Job? = null

        // Persisted snapshots are authoritative for history; ChatMessage remains a UI-only rendering
        // concern and is never converted back into HistoryItem.
        private var history: List<HistoryItem> = emptyList()
        private val initialHistoryLoaded = CompletableDeferred<Unit>()
        private var pendingOwnHistoryEmission: List<HistoryItem>? = null
        private val toolMessageIdByCallId = mutableMapOf<String, String>()

        // AgentLoop runs tool calls strictly one at a time, so "the last one started" is always
        // the one an ApprovalRequested (which carries no call id of its own) belongs to.
        private var lastToolMessageId: String? = null

        val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                try {
                    conversationRepository.observe().collect { loadedHistory ->
                        val isInitialEmission = !initialHistoryLoaded.isCompleted
                        val isOwnHistoryEmission = loadedHistory == pendingOwnHistoryEmission
                        if (isOwnHistoryEmission) pendingOwnHistoryEmission = null
                        val interruptedActiveTurn =
                            !isInitialEmission && !isOwnHistoryEmission && runningJob?.isActive == true
                        if (interruptedActiveTurn) {
                            runningJob?.cancel()
                            runningJob = null
                        }
                        history = loadedHistory
                        mutableState.update { state ->
                            val messages =
                                if (isInitialEmission) {
                                    loadedHistory.toChatMessages() + state.messages
                                } else if (loadedHistory.isEmpty()) {
                                    emptyList()
                                } else {
                                    val toolMessages =
                                        if (interruptedActiveTurn) {
                                            emptyList()
                                        } else {
                                            state.messages.filter { it.role == ChatRole.TOOL }
                                        }
                                    loadedHistory.toChatMessages() + toolMessages
                                }
                            state.copy(messages = messages)
                        }
                        initialHistoryLoaded.complete(Unit)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    mutableState.update {
                        it.copy(error = AgentError.Unexpected("Failed to load conversation history (${exception::class.simpleName})"))
                    }
                } finally {
                    initialHistoryLoaded.complete(Unit)
                }
            }
        }

        fun onEvent(event: ChatUiEvent) {
            when (event) {
                is ChatUiEvent.Submit -> submit(event.text)
                ChatUiEvent.Stop -> runningJob?.cancel()
                is ChatUiEvent.ToggleToolCallDetails -> toggleToolCallDetails(event.messageId)
                ChatUiEvent.DismissError -> mutableState.update { it.copy(error = null) }
            }
        }

        private fun submit(text: String) {
            if (text.isBlank() || mutableState.value.isRunning) return
            val trimmed = text.trim()

            mutableState.update {
                it.copy(
                    messages = it.messages + ChatMessage(nextId(), ChatRole.USER, trimmed),
                    isRunning = true,
                    error = null,
                )
            }

            runningJob =
                viewModelScope.launch {
                    initialHistoryLoaded.await()
                    collectTurn(trimmed)
                }
        }

        private suspend fun collectTurn(text: String) {
            try {
                var assistantMessageId: String? = null
                agentLoop.run(history, text).collect { event ->
                    assistantMessageId = handleAgentEvent(event, assistantMessageId)
                    if (event is AgentEvent.TurnCompleted || event is AgentEvent.TurnFailed) {
                        pendingOwnHistoryEmission = history
                        try {
                            conversationRepository.replace(history)
                        } catch (exception: CancellationException) {
                            pendingOwnHistoryEmission = null
                            throw exception
                        } catch (_: Exception) {
                            pendingOwnHistoryEmission = null
                            mutableState.update {
                                it.copy(error = AgentError.Unexpected("Failed to save conversation history"))
                            }
                        }
                    }
                }
            } finally {
                val pendingIds = toolMessageIdByCallId.values.toList()
                toolMessageIdByCallId.clear()
                pendingIds.forEach { id -> updateToolCallStatus(id, ToolCallStatus.Cancelled) }
                mutableState.update { it.copy(isRunning = false) }
            }
        }

        private fun handleAgentEvent(
            event: AgentEvent,
            assistantMessageId: String?,
        ): String? =
            when (event) {
                is AgentEvent.TextDelta -> {
                    val id = assistantMessageId ?: nextId()
                    appendAssistantText(id, event.text)
                    id
                }

                is AgentEvent.ToolCallStarted -> {
                    val id = nextId()
                    toolMessageIdByCallId[event.call.id] = id
                    lastToolMessageId = id
                    val toolCall =
                        ToolCallMessage(
                            status = ToolCallStatus.Running,
                            description = event.description,
                            arguments = event.call.arguments,
                        )
                    mutableState.update {
                        it.copy(messages = it.messages + ChatMessage(id, ChatRole.TOOL, "", toolCall = toolCall))
                    }
                    null
                }

                is AgentEvent.ApprovalRequested -> {
                    lastToolMessageId?.let { updateToolCallStatus(it, ToolCallStatus.AwaitingApproval) }
                    assistantMessageId
                }

                is AgentEvent.ToolCallFinished -> {
                    finishToolCall(event)
                    assistantMessageId
                }

                is AgentEvent.TurnCompleted -> {
                    history = history + event.newItems
                    assistantMessageId
                }

                is AgentEvent.TurnFailed -> {
                    history = history + event.newItems
                    mutableState.update { it.copy(error = event.error) }
                    assistantMessageId
                }
            }

        private fun finishToolCall(event: AgentEvent.ToolCallFinished) {
            val id = toolMessageIdByCallId.remove(event.call.id) ?: return
            val fileHits = event.toFileHits()
            updateToolCallStatus(
                id = id,
                status = event.outcome.toCardStatus(event.call.name, fileHits.size),
                fileHits = fileHits,
            )
        }

        private fun appendAssistantText(
            id: String,
            delta: String,
        ) {
            mutableState.update { state ->
                val existing = state.messages.firstOrNull { it.id == id }
                val messages =
                    if (existing == null) {
                        state.messages + ChatMessage(id, ChatRole.ASSISTANT, delta)
                    } else {
                        state.messages.map { message -> if (message.id == id) message.copy(text = message.text + delta) else message }
                    }
                state.copy(messages = messages)
            }
        }

        private fun updateToolCallStatus(
            id: String,
            status: ToolCallStatus,
            fileHits: List<FileHit>? = null,
        ) {
            mutableState.update { state ->
                state.copy(
                    messages =
                        state.messages.map { message ->
                            val toolCall = message.toolCall
                            if (message.id == id && toolCall != null) {
                                message.copy(toolCall = toolCall.copy(status = status, fileHits = fileHits ?: toolCall.fileHits))
                            } else {
                                message
                            }
                        },
                )
            }
        }

        private fun toggleToolCallDetails(id: String) {
            mutableState.update { state ->
                state.copy(
                    messages =
                        state.messages.map { message ->
                            val toolCall = message.toolCall
                            if (message.id == id && toolCall != null) {
                                message.copy(toolCall = toolCall.copy(expanded = !toolCall.expanded))
                            } else {
                                message
                            }
                        },
                )
            }
        }

        private fun nextId(): String = (++nextMessageId).toString()
    }

private fun List<HistoryItem>.toChatMessages(): List<ChatMessage> =
    mapNotNull { item ->
        when (item) {
            is HistoryItem.UserMessage -> ChatMessage(item.id, ChatRole.USER, item.text)
            is HistoryItem.AssistantMessage -> ChatMessage(item.id, ChatRole.ASSISTANT, item.text)
            is HistoryItem.ToolCallItem, is HistoryItem.ToolOutput, is HistoryItem.Opaque -> null
        }
    }

private fun ToolOutcome.toCardStatus(
    toolName: String,
    fileHitCount: Int,
): ToolCallStatus =
    when (this) {
        is ToolOutcome.Success -> {
            if (toolName == SEARCH_FILES_TOOL_NAME) {
                ToolCallStatus.FilesFound(fileHitCount)
            } else {
                ToolCallStatus.Done(text)
            }
        }

        is ToolOutcome.Rejected -> {
            ToolCallStatus.Rejected
        }

        is ToolOutcome.Failed -> {
            ToolCallStatus.Failed(reason)
        }
    }

private const val SEARCH_FILES_TOOL_NAME = "search_files"
