package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopContinueTest {
    @Test
    fun `continuation sends the saved history once and returns only new items`() =
        runTest {
            val callId = CallId("saved-call")
            val history =
                listOf(
                    HistoryItem.UserMessage(ItemId("question"), "find something"),
                    HistoryItem.ToolCallItem(ItemId("call-item"), callId, "find_files", buildJsonObject {}),
                    HistoryItem.ToolOutput(ItemId("output-item"), callId, "found"),
                )
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()) }

            val events = loop(gateway).continueTurn(history).toList()

            assertEquals(history, gateway.requests.single().history)
            val completed = events.last() as AgentEvent.TurnCompleted
            assertEquals(1, completed.newItems.size)
            assertTrue(completed.newItems.single() is HistoryItem.AssistantMessage)
        }

    @Test
    fun `a text continuation does not execute a completed side effect again`() =
        runTest {
            val call = ToolCall(CallId("action-call"), "record_action", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
                    responseQueue.addLast(listOf(ModelEvent.Failed(AgentError.Offline)))
                    responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
                }
            var executions = 0
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("record_action") {
                            executions++
                            ToolResult.Success("recorded")
                        },
                    ),
                )
            val agentLoop = loop(gateway, registry)

            val failed = agentLoop.run(emptyList(), "record this").toList().last() as AgentEvent.TurnFailed
            assertEquals(1, executions)

            val events = agentLoop.continueTurn(failed.newItems).toList()

            assertEquals(1, executions)
            assertEquals(failed.newItems, gateway.requests[2].history)
            assertEquals(1, (events.last() as AgentEvent.TurnCompleted).newItems.size)
        }

    @Test
    fun `continuation executes only tools requested by the new model response`() =
        runTest {
            val previousCallId = CallId("previous-call")
            val history =
                listOf(
                    HistoryItem.UserMessage(ItemId("question"), "do both"),
                    HistoryItem.ToolCallItem(ItemId("previous-item"), previousCallId, "old_tool", buildJsonObject {}),
                    HistoryItem.ToolOutput(ItemId("previous-output"), previousCallId, "old result"),
                )
            val newCall = ToolCall(CallId("new-call"), "new_tool", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(newCall), ModelEvent.Completed()))
                    responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
                }
            val executed = mutableListOf<String>()
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("old_tool") {
                            executed += "old_tool"
                            ToolResult.Success("old")
                        },
                        fakeTool("new_tool") {
                            executed += "new_tool"
                            ToolResult.Success("new")
                        },
                    ),
                )

            val events = loop(gateway, registry).continueTurn(history).toList()

            assertEquals(listOf("new_tool"), executed)
            val newItems = (events.last() as AgentEvent.TurnCompleted).newItems
            assertEquals(listOf(newCall.callId), newItems.filterIsInstance<HistoryItem.ToolCallItem>().map { it.callId })
            assertEquals(listOf(newCall.callId), newItems.filterIsInstance<HistoryItem.ToolOutput>().map { it.callId })
        }

    @Test
    fun `a model error in continuation reports only newly generated items`() =
        runTest {
            val callId = CallId("saved-call")
            val history =
                listOf(
                    HistoryItem.UserMessage(ItemId("question"), "continue"),
                    HistoryItem.ToolCallItem(ItemId("call-item"), callId, "find_files", buildJsonObject {}),
                    HistoryItem.ToolOutput(ItemId("output-item"), callId, "found"),
                )
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.Failed(AgentError.Offline)) }

            val failed = loop(gateway).continueTurn(history).toList().last() as AgentEvent.TurnFailed

            assertEquals(AgentError.Offline, failed.error)
            assertEquals(emptyList<HistoryItem>(), failed.newItems)
        }

    @Test
    fun `continuation starts with fresh step and tool-call budgets`() =
        runTest {
            val oldCallA = CallId("old-a")
            val oldCallB = CallId("old-b")
            val history =
                listOf(
                    HistoryItem.UserMessage(ItemId("question"), "continue"),
                    HistoryItem.ToolCallItem(ItemId("old-a-item"), oldCallA, "old_tool", buildJsonObject {}),
                    HistoryItem.ToolOutput(ItemId("old-a-output"), oldCallA, "a"),
                    HistoryItem.AssistantMessage(ItemId("intermediate"), "working"),
                    HistoryItem.ToolCallItem(ItemId("old-b-item"), oldCallB, "old_tool", buildJsonObject {}),
                    HistoryItem.ToolOutput(ItemId("old-b-output"), oldCallB, "b"),
                )
            val newCall = ToolCall(CallId("fresh-call"), "new_tool", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(newCall), ModelEvent.Completed()))
                    responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
                }
            var executions = 0
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("new_tool") {
                            executions++
                            ToolResult.Success("new")
                        },
                    ),
                )

            val events =
                loop(
                    gateway,
                    registry,
                    AgentConfig(maxSteps = 2, maxToolCallsPerTurn = 1),
                ).continueTurn(history).toList()

            assertTrue(events.last() is AgentEvent.TurnCompleted)
            assertEquals(1, executions)
            assertEquals(
                listOf(
                    newCall.callId,
                ),
                (events.last() as AgentEvent.TurnCompleted).newItems.filterIsInstance<HistoryItem.ToolCallItem>().map {
                    it.callId
                },
            )
        }

    private fun loop(
        gateway: FakeModelGateway,
        registry: ToolRegistry = ToolRegistry(emptySet()),
        config: AgentConfig = AgentConfig(),
    ): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline =
                ToolPipeline(
                    registry = registry,
                    userInteraction = FakeUserInteraction(),
                    clock = FakeClock(),
                    logger = FakeAppLogger(),
                ),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
            config = config,
        )

    private fun fakeTool(
        name: String,
        execute: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec = ToolSpec(name, "test tool", buildJsonObject {}, Risk.SAFE)

            override fun describe(args: JsonObject): CallDescription = CallDescription(name, "calling $name")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = execute()
        }
}
