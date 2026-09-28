package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.platform.IdGenerator
import com.example.phoneagent.domain.tools.Approval
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.toOutputText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

private data class ToolInvocation(
    val name: String,
    val arguments: kotlinx.serialization.json.JsonObject,
)

private fun completedInvocationsFrom(history: List<HistoryItem>): MutableSet<ToolInvocation> {
    val currentTurnStart = history.indexOfLast { it is HistoryItem.UserMessage } + 1
    val completedCallIds = mutableSetOf<CallId>()
    for (index in currentTurnStart until history.size) {
        val item = history[index]
        if (item is HistoryItem.ToolOutput) completedCallIds += item.callId
    }

    val invocations = mutableSetOf<ToolInvocation>()
    for (index in currentTurnStart until history.size) {
        val item = history[index]
        if (item is HistoryItem.ToolCallItem && item.callId in completedCallIds) {
            invocations += ToolInvocation(item.name, item.arguments)
        }
    }
    return invocations
}

/**
 * Drives or continues one user turn to completion: sends the model a request, executes any tool calls it asks
 * for (in order, per architecture/core.md — the model may request several in one response), feeds
 * their outputs back, and repeats until the model replies with no more calls, fails, or the step
 * limit is hit. A cold [Flow] — not tied to a ViewModel or Android; cancelling the collector
 * cancels the in-flight model call and tool execution with it (structured concurrency).
 */
public class AgentLoop(
    private val modelGateway: ModelGateway,
    private val toolPipeline: ToolPipeline,
    private val contextWindowPolicy: ContextWindowPolicy,
    private val idGenerator: IdGenerator,
    private val config: AgentConfig = AgentConfig(),
) {
    public fun run(
        history: List<HistoryItem>,
        input: String,
        onApprovalResolved: suspend (List<CallId>) -> Unit = {},
    ): Flow<AgentEvent> =
        flow {
            val context =
                TurnContext(
                    history = history,
                    newItems = mutableListOf(HistoryItem.UserMessage(idGenerator.newItemId(), input)),
                    completedInvocations = mutableSetOf(),
                )
            runTurn(context, onApprovalResolved)
        }

    /** Resumes an existing turn without adding a user message; terminal events contain only new items. */
    public fun continueTurn(
        history: List<HistoryItem>,
        onApprovalResolved: suspend (List<CallId>) -> Unit = {},
    ): Flow<AgentEvent> =
        flow {
            val context =
                TurnContext(
                    history = history,
                    newItems = mutableListOf(),
                    completedInvocations = completedInvocationsFrom(history),
                )
            runTurn(context, onApprovalResolved)
        }

    private suspend fun FlowCollector<AgentEvent>.runModelTurn(history: List<HistoryItem>): ModelTurn {
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        var failure: AgentError? = null
        val request = ModelRequest(contextWindowPolicy.apply(history), toolPipeline.specs)
        modelGateway.stream(request).collect { event ->
            when (event) {
                is ModelEvent.TextDelta -> {
                    text.append(event.text)
                    emit(AgentEvent.TextDelta(event.text))
                }

                is ModelEvent.ToolCallRequested -> {
                    toolCalls += event.call
                }

                is ModelEvent.Failed -> {
                    failure = event.error
                }

                is ModelEvent.Completed, is ModelEvent.OpaqueItem -> {
                    Unit
                }
            }
        }
        return ModelTurn(text.toString(), toolCalls, failure)
    }

    /**
     * Asks one shared question per group of confirm-level calls (before anything runs), so the user
     * answers once for a batch instead of once per call. Returns the answer for each grouped call.
     */
    private suspend fun FlowCollector<AgentEvent>.confirmGroups(
        calls: List<ToolCall>,
        onApprovalResolved: suspend (List<CallId>) -> Unit,
    ): Map<CallId, Approval> {
        val decisions = mutableMapOf<CallId, Approval>()
        for (group in toolPipeline.batchGroups(calls)) {
            var approvalWasPresented = false
            val approved =
                toolPipeline.confirmBatch(group) { descriptions ->
                    group.zip(descriptions).forEach { (call, description) ->
                        emit(
                            AgentEvent.ToolCallStarted(
                                call,
                                description ?: CallDescription(call.name, ""),
                                toolPipeline.riskOf(call.name) ?: Risk.SAFE,
                            ),
                        )
                    }
                    if (descriptions.all { it != null }) {
                        emit(AgentEvent.BatchApprovalRequested(group.map { it.callId }))
                        approvalWasPresented = true
                    }
                }
            val decision =
                when {
                    !approvalWasPresented -> Approval.NOT_PRESENTED
                    approved -> Approval.GRANTED
                    else -> Approval.DECLINED
                }
            if (approvalWasPresented) onApprovalResolved(group.map { it.callId })
            group.forEach { decisions[it.callId] = decision }
        }
        return decisions
    }

    /** What the card and the confirmation of [call] say; a tool that no longer exists is shown under its name. */
    public fun describe(call: ToolCall): CallDescription =
        toolPipeline.describe(call.name, call.arguments) ?: unknownToolDescription(call.name)

    /** Restores a tool's typed result from the unchanged output text stored in conversation history. */
    public fun restoreToolContent(
        name: String,
        outputText: String,
    ): ToolContent? = toolPipeline.restoreContent(name, outputText)

    /** [sharedDecision] is null when the call was not part of a shared question and is asked on its own. */
    private suspend fun FlowCollector<AgentEvent>.runToolCall(
        call: ToolCall,
        sharedDecision: Approval?,
        requireConfirmation: Boolean,
        onApprovalResolved: suspend (List<CallId>) -> Unit,
    ): Pair<ToolCall, HistoryItem.ToolOutput> {
        if (sharedDecision == null) {
            val description = describe(call)
            val risk = toolPipeline.riskOf(call.name) ?: Risk.SAFE
            emit(AgentEvent.ToolCallStarted(call, description, risk))
            risk.takeIf { it != Risk.SAFE || requireConfirmation }?.let { approvalRisk ->
                emit(AgentEvent.ApprovalRequested(ApprovalRequest(description, approvalRisk, call.arguments)))
            }
        }
        var resolvedCall = call
        val outcome =
            toolPipeline.run(
                name = call.name,
                args = call.arguments,
                approval = sharedDecision ?: Approval.ASK,
                requireConfirmation = requireConfirmation,
                onApprovalResolved = { onApprovalResolved(listOf(call.callId)) },
                onArgumentsResolved = { resolvedCall = call.copy(arguments = it) },
            )
        emit(AgentEvent.ToolCallFinished(resolvedCall, outcome))
        return resolvedCall to HistoryItem.ToolOutput(idGenerator.newItemId(), call.callId, outcome.toOutputText())
    }

    private fun unknownToolDescription(name: String): CallDescription = CallDescription(title = name, summary = "Unknown tool")

    private suspend fun FlowCollector<AgentEvent>.runTurn(
        context: TurnContext,
        onApprovalResolved: suspend (List<CallId>) -> Unit,
    ) {
        var terminalEvent: AgentEvent? = null
        while (terminalEvent == null) {
            terminalEvent = runModelStep(context, onApprovalResolved)
        }
        emit(checkNotNull(terminalEvent))
    }

    private suspend fun FlowCollector<AgentEvent>.runModelStep(
        context: TurnContext,
        onApprovalResolved: suspend (List<CallId>) -> Unit,
    ): AgentEvent? {
        context.step++
        val turn =
            if (context.step > config.maxSteps) {
                null
            } else {
                runModelTurn(context.history + context.newItems)
            }
        val failure = turn?.failure ?: if (turn == null) AgentError.StepLimit else null
        if (failure != null) {
            return AgentEvent.TurnFailed(failure, context.newItems.toList())
        }

        val completedTurn = checkNotNull(turn)
        if (completedTurn.text.isNotEmpty()) {
            context.newItems += HistoryItem.AssistantMessage(idGenerator.newItemId(), completedTurn.text)
        }
        return when {
            completedTurn.toolCalls.isEmpty() -> {
                AgentEvent.TurnCompleted(context.newItems.toList())
            }

            context.toolCallsMade + completedTurn.toolCalls.size > config.maxToolCallsPerTurn -> {
                AgentEvent.TurnFailed(AgentError.StepLimit, context.newItems.toList())
            }

            else -> {
                context.toolCallsMade += completedTurn.toolCalls.size
                val sharedDecisions = confirmGroups(completedTurn.toolCalls, onApprovalResolved)
                for (call in completedTurn.toolCalls) {
                    val invocation = ToolInvocation(call.name, call.arguments)
                    val requireConfirmation = invocation in context.completedInvocations
                    val (resolvedCall, output) =
                        runToolCall(call, sharedDecisions[call.callId], requireConfirmation, onApprovalResolved)
                    context.newItems += HistoryItem.ToolCallItem(call.itemId, call.callId, call.name, resolvedCall.arguments)
                    context.newItems += output
                    context.completedInvocations += ToolInvocation(resolvedCall.name, resolvedCall.arguments)
                }
                null
            }
        }
    }

    private data class TurnContext(
        val history: List<HistoryItem>,
        val newItems: MutableList<HistoryItem>,
        val completedInvocations: MutableSet<ToolInvocation>,
        var step: Int = 0,
        var toolCallsMade: Int = 0,
    )

    private data class ModelTurn(
        val text: String,
        val toolCalls: List<ToolCall>,
        val failure: AgentError?,
    )
}
