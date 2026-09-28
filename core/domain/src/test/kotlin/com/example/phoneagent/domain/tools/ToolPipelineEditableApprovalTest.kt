package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.ConfirmResult
import com.example.phoneagent.domain.interaction.UserInteraction
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolPipelineEditableApprovalTest {
    private val original = buildJsonObject { put("minute", 30) }
    private val edited = buildJsonObject { put("minute", 45) }

    @Test
    fun `edited arguments are validated and used for execution`() =
        runTest {
            val interaction = Interaction(ConfirmResult.Approved(edited))
            val tool = RecordingTool(Risk.CONFIRM, editable = true)
            var resolved: JsonObject? = null

            val outcome = pipeline(tool, interaction).run("alarm", original, onArgumentsResolved = { resolved = it })

            assertEquals(ToolOutcome.Success("45"), outcome)
            assertEquals(edited, tool.executed)
            assertEquals(edited, resolved)
            assertEquals(
                45,
                interaction.request
                    ?.editor
                    ?.review(edited)
                    ?.description
                    ?.summary
                    ?.toInt(),
            )
        }

    @Test
    fun `invalid edited arguments cannot execute even if a caller approves them`() =
        runTest {
            val invalid = buildJsonObject { put("minute", 75) }
            val interaction = Interaction(ConfirmResult.Approved(invalid))
            val tool = RecordingTool(Risk.CONFIRM, editable = true)

            val outcome = pipeline(tool, interaction).run("alarm", original)

            assertEquals(ToolOutcome.Failed("minute must be 0..59"), outcome)
            assertNull(tool.executed)
            assertEquals(
                "minute must be 0..59",
                interaction.request
                    ?.editor
                    ?.review(invalid)
                    ?.validationError,
            )
        }

    @Test
    fun `rejecting after a draft edit preserves the original call without execution`() =
        runTest {
            val interaction = Interaction(ConfirmResult.Rejected)
            val tool = RecordingTool(Risk.CONFIRM, editable = true)
            var resolved: JsonObject? = null

            val outcome = pipeline(tool, interaction).run("alarm", original, onArgumentsResolved = { resolved = it })

            assertEquals(ToolOutcome.Rejected("user declined"), outcome)
            assertNull(tool.executed)
            assertNull(resolved)
        }

    @Test
    fun `a confirm tool without an editor keeps the original arguments`() =
        runTest {
            val interaction = Interaction(ConfirmResult.Approved(original))
            val tool = RecordingTool(Risk.CONFIRM, editable = false)

            val outcome = pipeline(tool, interaction).run("alarm", original)

            assertEquals(ToolOutcome.Success("30"), outcome)
            assertNull(interaction.request?.editor)
            assertEquals(original, tool.executed)
        }

    @Test
    fun `a sensitive tool cannot substitute edited arguments`() =
        runTest {
            val interaction = Interaction(ConfirmResult.Approved(edited))
            val tool = RecordingTool(Risk.SENSITIVE, editable = true)

            val outcome = pipeline(tool, interaction).run("alarm", original)

            assertNull(interaction.request?.editor)
            assertEquals(ToolOutcome.Failed("edited arguments are not allowed"), outcome)
            assertNull(tool.executed)
        }

    private fun pipeline(
        tool: Tool,
        interaction: Interaction,
    ): ToolPipeline = ToolPipeline(ToolRegistry(setOf(tool)), interaction, FakeClock(), FakeAppLogger())

    private class Interaction(
        private val answer: ConfirmResult,
    ) : UserInteraction {
        var request: ApprovalRequest? = null

        override suspend fun confirm(request: ApprovalRequest): ConfirmResult {
            this.request = request
            return answer
        }

        override suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean = true

        override suspend fun ensure(capability: Capability): CapabilityResult = CapabilityResult.Available
    }

    private class RecordingTool(
        risk: Risk,
        private val editable: Boolean,
    ) : Tool {
        var executed: JsonObject? = null
        override val spec = ToolSpec("alarm", "test", buildJsonObject {}, risk)

        override fun describe(args: JsonObject): CallDescription = CallDescription("alarm", args["minute"]!!.jsonPrimitive.content)

        override fun argumentEditor(args: JsonObject): ToolArgumentEditor? =
            if (!editable) {
                null
            } else {
                object : ToolArgumentEditor {
                    override val fields = listOf(EditableField("minute", "Минуты", EditableFieldKind.INTEGER))

                    override fun review(args: JsonObject): ArgumentReview {
                        val minute = args["minute"]?.jsonPrimitive?.int
                        return ArgumentReview(describe(args), if (minute == null || minute !in 0..59) "minute must be 0..59" else null)
                    }
                }
            }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult {
            executed = args
            return ToolResult.Success(args["minute"]!!.jsonPrimitive.content)
        }
    }
}
