package com.example.phoneagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.agent.AgentEvent
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.platform.RandomIdGenerator
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

        // What `history` was before the running turn appended its provisional question; the
        // finished turn rebuilds history from this, so the question is saved exactly once.
        private var historyBeforeTurn: List<HistoryItem> = emptyList()
        private val initialHistoryLoaded = CompletableDeferred<Unit>()

        // Set while the last persist() failed, so the next successful save knows to clear the
        // (now stale) save error - and only that error, never a newer turn error.
        private var saveFailed = false
        private val saveError = AgentError.Unexpected("Failed to save conversation history")

        // Every history we wrote and have not yet seen come back through observe(); a turn now saves
        // twice (the question, then the finished turn), so one slot could mistake the first echo for
        // an outside change and rebuild the feed from a stale list.
        private val pendingOwnEmissions = ArrayDeque<List<HistoryItem>>()
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
                        val ownEmissionIndex = pendingOwnEmissions.indexOf(loadedHistory)
                        val isOwnHistoryEmission = ownEmissionIndex >= 0
                        repeat(ownEmissionIndex + 1) { pendingOwnEmissions.removeFirstOrNull() }
                        val interruptedActiveTurn =
                            !isInitialEmission && !isOwnHistoryEmission && runningJob?.isActive == true
                        if (interruptedActiveTurn) {
                            runningJob?.cancel()
                            runningJob = null
                        }
                        history = loadedHistory
                        // The screen already shows our own turn exactly as it happened (text and
                        // tool cards in order), so rebuilding it from the saved history would only
                        // regroup the cards after the texts.
                        if (!isOwnHistoryEmission) {
                            mutableState.update {
                                it.withLoadedHistory(loadedHistory, isInitialEmission, dropToolCards = interruptedActiveTurn)
                            }
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
                is ChatUiEvent.Submit -> {
                    submit(event.text)
                }

                ChatUiEvent.Stop -> {
                    runningJob?.cancel()
                    // A cancel racing the very first step of the turn never runs collectTurn's
                    // finally, so the flag is cleared here; the finally just repeats it harmlessly.
                    mutableState.update { it.copy(isRunning = false) }
                }

                is ChatUiEvent.ToggleToolCallDetails -> {
                    mutableState.update { it.withToolCallToggled(event.messageId) }
                }

                ChatUiEvent.Retry -> {
                    retry()
                }

                ChatUiEvent.DismissError -> {
                    mutableState.update { it.copy(error = null, canRetry = false) }
                }
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
                    canRetry = false,
                )
            }

            // The question enters the in-memory history immediately - not via the storage echo -
            // so a Stop before the echo cannot lose it, and the next submit cannot overwrite it.
            historyBeforeTurn = history
            // The id must be unique: a stopped question stays in history, and the feed keys on it.
            history = history + HistoryItem.UserMessage("pending-${RandomIdGenerator.newId()}", trimmed)
            // Saved in its own job: stopping the turn must not cancel the save of the question.
            val questionSnapshot = history
            viewModelScope.launch {
                initialHistoryLoaded.await()
                persist(questionSnapshot)
            }
            runningJob =
                viewModelScope.launch {
                    initialHistoryLoaded.await()
                    collectTurn(trimmed)
                }
        }

        /**
         * Redoes the turn that just failed (or was cut short by a closed app): the failed exchange
         * (the user message and whatever the model managed to produce) is dropped from memory and
         * screen first, and [submit] saves the question alone, so the text is sent once and a Stop
         * during the retry cannot bring the failed attempt back.
         */
        private fun retry() {
            val current = mutableState.value
            if (!current.canRetry || current.isRunning) return
            val failedUserIndex = history.indexOfLast { it is HistoryItem.UserMessage }
            val failedUser = history.getOrNull(failedUserIndex) as? HistoryItem.UserMessage ?: return
            val keptHistory = history.take(failedUserIndex)
            val keptMessages = current.messages.take(current.messages.indexOfLast { it.role == ChatRole.USER }.coerceAtLeast(0))

            history = keptHistory
            mutableState.update { it.copy(messages = keptMessages, error = null, canRetry = false) }
            submit(failedUser.text)
        }

        private suspend fun persist(items: List<HistoryItem>) {
            pendingOwnEmissions.addLast(items)
            try {
                conversationRepository.replace(items)
                if (saveFailed) {
                    // A later successful save clears the stale save error - but only it, never a
                    // newer error that arrived in between.
                    saveFailed = false
                    mutableState.update { state -> if (state.error == saveError) state.copy(error = null) else state }
                }
            } catch (exception: CancellationException) {
                pendingOwnEmissions.remove(items)
                throw exception
            } catch (_: Exception) {
                pendingOwnEmissions.remove(items)
                saveFailed = true
                mutableState.update { it.copy(error = saveError) }
            }
        }

        private suspend fun collectTurn(text: String) {
            try {
                var assistantMessageId: String? = null
                agentLoop.run(historyBeforeTurn, text).collect { event ->
                    assistantMessageId = handleAgentEvent(event, assistantMessageId)
                    if (event is AgentEvent.TurnCompleted || event is AgentEvent.TurnFailed) persist(history)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // A bug in the loop, a gateway or a tool must not take the app down: keep the
                // question in history and offer a retry of the whole turn.
                mutableState.update {
                    it.copy(error = AgentError.Unexpected("turn failed (${exception::class.simpleName})"), canRetry = true)
                }
            } finally {
                val pendingIds = toolMessageIdByCallId.values.toList()
                toolMessageIdByCallId.clear()
                mutableState.update { state -> pendingIds.fold(state) { acc, id -> acc.withToolCallStatus(id, ToolCallStatus.Cancelled) } }
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
                    mutableState.update { it.withAssistantDelta(id, event.text) }
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

                is AgentEvent.BatchApprovalRequested -> {
                    val cardIds = event.callIds.mapNotNull { toolMessageIdByCallId[it] }
                    mutableState.update { state ->
                        cardIds.fold(state) { acc, id -> acc.withToolCallStatus(id, ToolCallStatus.AwaitingApproval) }
                    }
                    assistantMessageId
                }

                is AgentEvent.ApprovalRequested -> {
                    lastToolMessageId?.let { id -> mutableState.update { it.withToolCallStatus(id, ToolCallStatus.AwaitingApproval) } }
                    assistantMessageId
                }

                is AgentEvent.ToolCallFinished -> {
                    finishToolCall(event)
                    assistantMessageId
                }

                is AgentEvent.TurnCompleted -> {
                    // Rebuild from before the turn: newItems carries the real user message, which
                    // replaces the provisional one saved on submit.
                    history = historyBeforeTurn + event.newItems
                    assistantMessageId
                }

                is AgentEvent.TurnFailed -> {
                    history = historyBeforeTurn + event.newItems
                    val presentation = event.error.toPresentation()
                    mutableState.update {
                        it.copy(
                            error = event.error.takeIf { presentation != null },
                            canRetry = presentation?.retryable == true,
                        )
                    }
                    assistantMessageId
                }
            }

        private fun finishToolCall(event: AgentEvent.ToolCallFinished) {
            val id = toolMessageIdByCallId.remove(event.call.id) ?: return
            val fileHits = event.toFileHits()
            val status = event.outcome.toCardStatus(event.call.name, fileHits.size)
            mutableState.update { it.withToolCallStatus(id, status, fileHits) }
        }

        private fun nextId(): String = (++nextMessageId).toString()
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
