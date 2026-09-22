package com.example.phoneagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.domain.agent.AgentEvent
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.tools.ToolOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
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
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ChatUiState())
        private var nextMessageId = 0L
        private var runningJob: Job? = null

        // Authoritative conversation history for the model, owned entirely by AgentLoop's return
        // values (TurnCompleted/TurnFailed) — ChatMessage below is a UI-only rendering concern and
        // is never converted back into HistoryItem.
        private var history: List<HistoryItem> = emptyList()
        private val toolMessageIdByCallId = mutableMapOf<String, String>()

        val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

        fun onEvent(event: ChatUiEvent) {
            when (event) {
                is ChatUiEvent.Submit -> submit(event.text)
                ChatUiEvent.Stop -> runningJob?.cancel()
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
                    try {
                        var assistantMessageId: String? = null
                        agentLoop.run(history, trimmed).collect { event ->
                            when (event) {
                                is AgentEvent.TextDelta -> {
                                    val id = assistantMessageId ?: nextId().also { assistantMessageId = it }
                                    appendAssistantText(id, event.text)
                                }

                                is AgentEvent.ToolCallStarted -> {
                                    assistantMessageId = null
                                    val id = nextId()
                                    toolMessageIdByCallId[event.call.id] = id
                                    val summary = "${event.description.title}: ${event.description.summary}"
                                    mutableState.update { it.copy(messages = it.messages + ChatMessage(id, ChatRole.TOOL, summary)) }
                                }

                                is AgentEvent.ToolCallFinished -> {
                                    val id = toolMessageIdByCallId.remove(event.call.id)
                                    if (id != null) replaceMessageText(id, event.outcome.toDisplayText())
                                }

                                is AgentEvent.ApprovalRequested -> {
                                    // Rendered by the app-wide InteractionHost, not the chat feed itself.
                                    Unit
                                }

                                is AgentEvent.TurnCompleted -> {
                                    history = history + event.newItems
                                }

                                is AgentEvent.TurnFailed -> {
                                    history = history + event.newItems
                                    mutableState.update { it.copy(error = event.error) }
                                }
                            }
                        }
                    } finally {
                        // Runs on normal completion, a Failed event, and on Stop (job.cancel()) alike.
                        mutableState.update { it.copy(isRunning = false) }
                    }
                }
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

        private fun replaceMessageText(
            id: String,
            text: String,
        ) {
            mutableState.update { state ->
                state.copy(messages = state.messages.map { message -> if (message.id == id) message.copy(text = text) else message })
            }
        }

        private fun nextId(): String = (++nextMessageId).toString()

        private fun ToolOutcome.toDisplayText(): String =
            when (this) {
                is ToolOutcome.Success -> text
                is ToolOutcome.Rejected -> ChatCopy.toolRejected(reason)
                is ToolOutcome.Failed -> ChatCopy.toolFailed(reason)
            }
    }
