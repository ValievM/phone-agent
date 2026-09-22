package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPipelineTest {
    @Test
    fun `an unknown tool name fails without approving or executing`() =
        runTest {
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(emptySet()), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("unknown tool: set_alarm"), outcome)
            assertTrue(interaction.approvalRequests.isEmpty())
        }

    @Test
    fun `a missing required argument fails without approving or executing`() =
        runTest {
            var executed = false
            val tool =
                fakeTool("set_alarm", required = listOf("hour")) {
                    executed = true
                    ToolResult.Success("ok")
                }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("missing required fields: hour"), outcome)
            assertTrue(interaction.approvalRequests.isEmpty())
            assertTrue(!executed)
        }

    @Test
    fun `a SAFE tool executes without asking for approval`() =
        runTest {
            val interaction = FakeUserInteraction()
            val tool = fakeTool("find_files", risk = Risk.SAFE) { ToolResult.Success("found nothing") }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("find_files", buildJsonObject {})

            assertEquals(ToolOutcome.Success("found nothing"), outcome)
            assertTrue(interaction.approvalRequests.isEmpty())
        }

    @Test
    fun `a CONFIRM tool asks for approval and is rejected without executing when declined`() =
        runTest {
            var executed = false
            val interaction = FakeUserInteraction().apply { approvalResult = false }
            val tool =
                fakeTool("set_alarm", risk = Risk.CONFIRM) {
                    executed = true
                    ToolResult.Success("ok")
                }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Rejected("user declined"), outcome)
            assertEquals(1, interaction.approvalRequests.size)
            assertTrue(!executed)
        }

    @Test
    fun `a CONFIRM tool executes after the user approves`() =
        runTest {
            val interaction = FakeUserInteraction().apply { approvalResult = true }
            val tool = fakeTool("set_alarm", risk = Risk.CONFIRM) { ToolResult.Success("done") }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Success("done"), outcome)
        }

    @Test
    fun `a tool that fails reports the reason`() =
        runTest {
            val tool = fakeTool("find_files") { ToolResult.Failed("folder not selected") }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)))

            assertEquals(ToolOutcome.Failed("folder not selected"), pipeline.run("find_files", buildJsonObject {}))
        }

    @Test
    fun `output longer than the limit is truncated without splitting a multi-byte character`() =
        runTest {
            // "Привет" is all 2-byte Cyrillic UTF-8 characters; an odd byte limit forces the cut to
            // land mid-character, verifying the boundary gets pushed back rather than just sliced.
            val longText = "Привет".repeat(2000)
            val tool = fakeTool("find_files") { ToolResult.Success(longText) }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), maxOutputBytes = 4095)

            val outcome = pipeline.run("find_files", buildJsonObject {}) as ToolOutcome.Success

            val truncatedBytes = outcome.text.toByteArray(Charsets.UTF_8)
            assertTrue(truncatedBytes.size <= 4095)
            assertEquals(outcome.text, String(truncatedBytes, Charsets.UTF_8))
        }

    @Test
    fun `a tool that never returns is stopped after the timeout`() =
        runTest {
            val tool = fakeTool("find_files") { awaitCancellation() }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), timeoutMillis = 10)

            val outcome = pipeline.run("find_files", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("timed out after 10ms"), outcome)
        }

    private fun pipeline(
        registry: ToolRegistry,
        interaction: FakeUserInteraction = FakeUserInteraction(),
        timeoutMillis: Long = 1_000,
        maxOutputBytes: Int = 4096,
    ): ToolPipeline =
        ToolPipeline(
            registry = registry,
            userInteraction = interaction,
            clock = FakeClock(),
            logger = FakeAppLogger(),
            timeoutMillis = timeoutMillis,
            maxOutputBytes = maxOutputBytes,
        )

    private fun fakeTool(
        name: String,
        risk: Risk = Risk.SAFE,
        required: List<String> = emptyList(),
        execute: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec =
                ToolSpec(
                    name = name,
                    description = "test tool",
                    inputSchema =
                        buildJsonObject {
                            put("type", "object")
                            putJsonArray("required") { required.forEach { add(it) } }
                        },
                    risk = risk,
                )

            override fun describe(args: JsonObject): CallDescription = CallDescription(title = name, summary = "summary")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = execute()
        }
}
