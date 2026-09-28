package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.ConfirmResult
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.tools.ArgumentReview
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.EditableField
import com.example.phoneagent.domain.tools.EditableFieldKind
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolArgumentEditor
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentLoopEditableApprovalTest {
    @Test
    fun `the edited call is executed, shown in the finished event and sent to the model`() =
        runTest {
            val original = buildJsonObject { put("minute", 30) }
            val edited = buildJsonObject { put("minute", 45) }
            val call = ToolCall(CallId("alarm-1"), "alarm", original)
            val gateway =
                FakeModelGateway().apply {
                    responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
                    responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
                }
            val interaction = FakeUserInteraction().apply { scriptedConfirmResults.addLast(ConfirmResult.Approved(edited)) }
            val tool = AlarmLikeTool()
            val loop =
                AgentLoop(
                    gateway,
                    ToolPipeline(ToolRegistry(setOf(tool)), interaction, FakeClock(), FakeAppLogger()),
                    ContextWindowPolicy { it },
                    FakeIdGenerator(),
                )

            val events = loop.run(emptyList(), "set alarm").toList()

            assertEquals(edited, tool.executed)
            assertEquals(
                edited,
                events
                    .filterIsInstance<AgentEvent.ToolCallFinished>()
                    .single()
                    .call.arguments,
            )
            val saved = (events.last() as AgentEvent.TurnCompleted).newItems.filterIsInstance<HistoryItem.ToolCallItem>().single()
            assertEquals(edited, saved.arguments)
            val forwarded =
                gateway.requests[1]
                    .history
                    .filterIsInstance<HistoryItem.ToolCallItem>()
                    .single()
            assertEquals(edited, forwarded.arguments)
        }

    @Test
    fun `rejection retains the original call in history`() =
        runTest {
            val original = buildJsonObject { put("minute", 30) }
            val call = ToolCall(CallId("alarm-1"), "alarm", original)
            val gateway =
                FakeModelGateway().apply {
                    responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
                    responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
                }
            val interaction = FakeUserInteraction().apply { scriptedConfirmResults.addLast(ConfirmResult.Rejected) }
            val tool = AlarmLikeTool()
            val loop =
                AgentLoop(
                    gateway,
                    ToolPipeline(ToolRegistry(setOf(tool)), interaction, FakeClock(), FakeAppLogger()),
                    ContextWindowPolicy { it },
                    FakeIdGenerator(),
                )

            val events = loop.run(emptyList(), "set alarm").toList()

            assertEquals(null, tool.executed)
            assertEquals(
                original,
                (events.last() as AgentEvent.TurnCompleted)
                    .newItems
                    .filterIsInstance<HistoryItem.ToolCallItem>()
                    .single()
                    .arguments,
            )
        }

    private class AlarmLikeTool : Tool {
        var executed: JsonObject? = null
        override val spec = ToolSpec("alarm", "test", buildJsonObject {}, Risk.CONFIRM)

        override fun describe(args: JsonObject) = CallDescription("alarm", args["minute"]!!.jsonPrimitive.content)

        override fun argumentEditor(args: JsonObject): ToolArgumentEditor =
            object : ToolArgumentEditor {
                override val fields = listOf(EditableField("minute", "Минуты", EditableFieldKind.INTEGER))

                override fun review(args: JsonObject) = ArgumentReview(describe(args), null)
            }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult {
            executed = args
            return ToolResult.Success("set")
        }
    }
}
