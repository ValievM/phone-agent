package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    private val gateway = FakeModelGateway()
    private val interaction = FakeUserInteraction()
    private val executedCalls = mutableListOf<String>()

    @Test
    fun `text-only reply completes the turn without any tool calls`() =
        runTest {
            gateway.events = listOf(ModelEvent.TextDelta("Hi"), ModelEvent.Completed())

            val events = loop().run(emptyList(), "hello").toList()

            assertEquals(
                listOf(
                    AgentEvent.TextDelta("Hi"),
                    AgentEvent.TurnCompleted(
                        listOf(HistoryItem.UserMessage("id-1", "hello"), HistoryItem.AssistantMessage("id-2", "Hi")),
                    ),
                ),
                events,
            )
        }

    @Test
    fun `a single tool call is executed and its output feeds the next model step`() =
        runTest {
            val call = ToolCall("call-1", "get_time", buildJsonObject {}, itemId = "fc-1")
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("It's noon"), ModelEvent.Completed()))
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Success("noon") }))

            val events = loop(registry).run(emptyList(), "what time is it").toList()

            assertEquals(listOf("get_time"), executedCalls)
            assertEquals(
                listOf(
                    AgentEvent.ToolCallStarted(call, CallDescription("get_time", "calling get_time")),
                    AgentEvent.ToolCallFinished(call, ToolOutcome.Success("noon")),
                    AgentEvent.TextDelta("It's noon"),
                    AgentEvent.TurnCompleted(
                        listOf(
                            HistoryItem.UserMessage("id-1", "what time is it"),
                            HistoryItem.ToolCallItem("fc-1", "call-1", "get_time", buildJsonObject {}),
                            HistoryItem.ToolOutput("id-2", "call-1", "noon"),
                            HistoryItem.AssistantMessage("id-3", "It's noon"),
                        ),
                    ),
                ),
                events,
            )
            assertEquals(2, gateway.requests.size)
            val secondRequestHistory = gateway.requests[1].history
            assertTrue(secondRequestHistory.any { it is HistoryItem.ToolOutput && it.output == "noon" })
        }

    @Test
    fun `a failing tool does not abort the turn — its failure is fed back to the model`() =
        runTest {
            val call = ToolCall("call-1", "get_time", buildJsonObject {})
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("sorry, failed"), ModelEvent.Completed()))
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Failed("network down") }))

            val events = loop(registry).run(emptyList(), "what time is it").toList()

            val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
            assertEquals(ToolOutcome.Failed("network down"), finished.outcome)
            assertTrue(events.last() is AgentEvent.TurnCompleted)
        }

    @Test
    fun `an unknown tool name does not abort the turn either`() =
        runTest {
            val call = ToolCall("call-1", "nonexistent", buildJsonObject {})
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("I can't do that"), ModelEvent.Completed()))

            val events = loop(ToolRegistry(emptySet())).run(emptyList(), "do the thing").toList()

            val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
            assertEquals(ToolOutcome.Failed("unknown tool: nonexistent"), finished.outcome)
            assertTrue(events.last() is AgentEvent.TurnCompleted)
        }

    @Test
    fun `several tool calls in one response are executed in order before the next model step`() =
        runTest {
            val callA = ToolCall("call-a", "tool_a", buildJsonObject {})
            val callB = ToolCall("call-b", "tool_b", buildJsonObject {})
            gateway.responseQueue.addLast(
                listOf(ModelEvent.ToolCallRequested(callA), ModelEvent.ToolCallRequested(callB), ModelEvent.Completed()),
            )
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("tool_a") { ToolResult.Success("a-done") },
                        fakeTool("tool_b") { ToolResult.Success("b-done") },
                    ),
                )

            loop(registry).run(emptyList(), "do both").toList()

            assertEquals(listOf("tool_a", "tool_b"), executedCalls)
        }

    @Test
    fun `the user declining a CONFIRM tool rejects it without executing, and the turn continues`() =
        runTest {
            interaction.approvalResult = false
            val call = ToolCall("call-1", "set_alarm", buildJsonObject {})
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("ok, not setting it"), ModelEvent.Completed()))
            val registry =
                ToolRegistry(setOf(fakeTool("set_alarm", risk = Risk.CONFIRM) { ToolResult.Success("set") }))

            val events = loop(registry).run(emptyList(), "set an alarm").toList()

            val expectedRequest = ApprovalRequest(CallDescription("set_alarm", "calling set_alarm"), Risk.CONFIRM, buildJsonObject {})
            assertTrue(events.contains(AgentEvent.ApprovalRequested(expectedRequest)))
            val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
            assertEquals(ToolOutcome.Rejected("user declined"), finished.outcome)
            assertTrue(executedCalls.isEmpty())
        }

    @Test
    fun `a model that keeps requesting tools forever is stopped at the step limit`() =
        runTest {
            val call = ToolCall("call-1", "get_time", buildJsonObject {})
            gateway.events = listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed())
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Success("noon") }))

            val events = loop(registry, AgentConfig(maxSteps = 3)).run(emptyList(), "loop forever").toList()

            val failed = events.last() as AgentEvent.TurnFailed
            assertEquals(AgentError.StepLimit, failed.error)
            assertEquals(HistoryItem.UserMessage("id-1", "loop forever"), failed.newItems.first())
            assertEquals(3, executedCalls.size)
        }

    @Test
    fun `a model error mid-stream ends the turn but keeps the user message for history`() =
        runTest {
            gateway.events = listOf(ModelEvent.TextDelta("partial"), ModelEvent.Failed(AgentError.Offline))

            val events = loop().run(emptyList(), "hello").toList()

            assertEquals(
                listOf(
                    AgentEvent.TextDelta("partial"),
                    AgentEvent.TurnFailed(AgentError.Offline, listOf(HistoryItem.UserMessage("id-1", "hello"))),
                ),
                events,
            )
        }

    @Test
    fun `cancelling the collector stops the loop instead of hanging`() =
        runTest {
            val hangingGateway =
                object : ModelGateway {
                    override fun stream(request: ModelRequest): Flow<ModelEvent> =
                        flow {
                            emit(ModelEvent.TextDelta("partial"))
                            awaitCancellation()
                        }
                }
            val agentLoop =
                AgentLoop(
                    modelGateway = hangingGateway,
                    toolPipeline = pipeline(ToolRegistry(emptySet())),
                    contextWindowPolicy = ContextWindowPolicy { it },
                    idGenerator = FakeIdGenerator(),
                )

            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    val collecting = async { agentLoop.run(emptyList(), "hello").toList() }
                    delay(50) // let the first delta actually emit before cancelling
                    collecting.cancel()
                    collecting.join()
                }
            }
        }

    private fun loop(
        registry: ToolRegistry = ToolRegistry(emptySet()),
        config: AgentConfig = AgentConfig(),
    ): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline = pipeline(registry),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
            config = config,
        )

    private fun pipeline(registry: ToolRegistry): ToolPipeline =
        ToolPipeline(
            registry = registry,
            userInteraction = interaction,
            clock = FakeClock(),
            logger = FakeAppLogger(),
        )

    private fun fakeTool(
        name: String,
        risk: Risk = Risk.SAFE,
        execute: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec = ToolSpec(name, "does $name", buildJsonObject {}, risk)

            override fun describe(args: JsonObject): CallDescription = CallDescription(name, "calling $name")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult {
                executedCalls += name
                return execute()
            }
        }
}
