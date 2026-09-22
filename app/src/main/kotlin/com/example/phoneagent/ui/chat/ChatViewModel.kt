package com.example.phoneagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
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
        private val modelGateway: ModelGateway,
        private val contextWindowPolicy: ContextWindowPolicy,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ChatUiState())
        private var nextMessageId = 0L
        private var runningJob: Job? = null

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

            val userMessage = ChatMessage(nextId(), ChatRole.USER, text.trim())
            mutableState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    isRunning = true,
                    error = null,
                )
            }

            runningJob =
                viewModelScope.launch {
                    try {
                        val history = contextWindowPolicy.apply(mutableState.value.messages.map { it.toHistoryItem() })
                        var assistantMessageId: String? = null
                        modelGateway.stream(ModelRequest(history)).collect { event ->
                            when (event) {
                                is ModelEvent.TextDelta -> {
                                    val id = assistantMessageId ?: nextId().also { assistantMessageId = it }
                                    mutableState.update { state ->
                                        val existing = state.messages.firstOrNull { it.id == id }
                                        val messages =
                                            if (existing == null) {
                                                state.messages + ChatMessage(id, ChatRole.ASSISTANT, event.text)
                                            } else {
                                                state.messages.map { message ->
                                                    if (message.id == id) message.copy(text = message.text + event.text) else message
                                                }
                                            }
                                        state.copy(messages = messages)
                                    }
                                }

                                is ModelEvent.Failed -> {
                                    mutableState.update { it.copy(error = event.error) }
                                }

                                is ModelEvent.Completed,
                                is ModelEvent.OpaqueItem,
                                is ModelEvent.ToolCallRequested,
                                -> {
                                    Unit
                                }
                            }
                        }
                    } finally {
                        // Runs on normal completion, a Failed event, and on Stop (job.cancel()) alike.
                        mutableState.update { it.copy(isRunning = false) }
                    }
                }
        }

        private fun nextId(): String = (++nextMessageId).toString()

        private fun ChatMessage.toHistoryItem(): HistoryItem =
            when (role) {
                ChatRole.USER -> HistoryItem.UserMessage(id, text)
                ChatRole.ASSISTANT -> HistoryItem.AssistantMessage(id, text)
            }
    }
