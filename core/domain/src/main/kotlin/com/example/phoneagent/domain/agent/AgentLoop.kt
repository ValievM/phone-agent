package com.example.phoneagent.domain.agent

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
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.toOutputText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Drives one user turn to completion: sends the model a request, executes any tool calls it asks
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
    ): Flow<AgentEvent> =
        flow {
            val newItems = mutableListOf<HistoryItem>(HistoryItem.UserMessage(idGenerator.newId(), input))
            var step = 0
            while (true) {
                step++
                if (step > config.maxSteps) {
                    emit(AgentEvent.TurnFailed(AgentError.StepLimit, newItems.toList()))
                    return@flow
                }
                val turn = runModelTurn(history + newItems)
                if (turn.failure != null) {
                    emit(AgentEvent.TurnFailed(turn.failure, newItems.toList()))
                    return@flow
                }
                if (turn.text.isNotEmpty()) {
                    newItems += HistoryItem.AssistantMessage(idGenerator.newId(), turn.text)
                }
                if (turn.toolCalls.isEmpty()) {
                    emit(AgentEvent.TurnCompleted(newItems.toList()))
                    return@flow
                }
                val sharedDecisions = confirmGroups(turn.toolCalls)
                for (call in turn.toolCalls) {
                    newItems += HistoryItem.ToolCallItem(call.itemId, call.id, call.name, call.arguments)
                    newItems += runToolCall(call, sharedDecisions[call.id])
                }
            }
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
    private suspend fun FlowCollector<AgentEvent>.confirmGroups(calls: List<ToolCall>): Map<String, Approval> {
        val decisions = mutableMapOf<String, Approval>()
        for (group in toolPipeline.batchGroups(calls)) {
            group.forEach { emit(AgentEvent.ToolCallStarted(it, describe(it))) }
            emit(AgentEvent.BatchApprovalRequested(group.map { it.id }))
            val decision = if (toolPipeline.confirmBatch(group)) Approval.GRANTED else Approval.DECLINED
            group.forEach { decisions[it.id] = decision }
        }
        return decisions
    }

    /** What the card and the confirmation of [call] say; a tool that no longer exists is shown under its name. */
    public fun describe(call: ToolCall): CallDescription =
        toolPipeline.describe(call.name, call.arguments) ?: unknownToolDescription(call.name)

    /** [sharedDecision] is null when the call was not part of a shared question and is asked on its own. */
    private suspend fun FlowCollector<AgentEvent>.runToolCall(
        call: ToolCall,
        sharedDecision: Approval?,
    ): HistoryItem.ToolOutput {
        if (sharedDecision == null) {
            val description = describe(call)
            emit(AgentEvent.ToolCallStarted(call, description))
            toolPipeline.riskOf(call.name)?.takeIf { it != Risk.SAFE }?.let { risk ->
                emit(AgentEvent.ApprovalRequested(ApprovalRequest(description, risk, call.arguments)))
            }
        }
        val outcome = toolPipeline.run(call.name, call.arguments, sharedDecision ?: Approval.ASK)
        emit(AgentEvent.ToolCallFinished(call, outcome))
        return HistoryItem.ToolOutput(idGenerator.newId(), call.id, outcome.toOutputText())
    }

    private fun unknownToolDescription(name: String): CallDescription = CallDescription(title = name, summary = "Unknown tool")

    private data class ModelTurn(
        val text: String,
        val toolCalls: List<ToolCall>,
        val failure: AgentError?,
    )
}
