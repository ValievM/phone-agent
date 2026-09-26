package com.example.phoneagent.tools.notify

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

public class NotifyToolTest {
    @Test
    public fun `does not notify when notification permission is denied`() =
        runTest {
            val interaction = FakeUserInteraction().apply { capabilityResult = CapabilityResult.Denied }
            val notifier = RecordingNotifier()
            val tool = NotifyTool(notifier)
            val pipeline =
                ToolPipeline(
                    ToolRegistry(setOf(tool)),
                    interaction,
                    FakeClock(),
                    FakeAppLogger(),
                )

            val result = pipeline.run("notify", args(title = "Готово", message = "Файл сохранён"))

            assertEquals(ToolOutcome.Failed(NotifyCopy.PERMISSION_DENIED), result)
            assertEquals(listOf(Capability.NOTIFICATIONS), interaction.capabilityRequests)
            assertEquals(emptyList<NotifyArgs>(), notifier.notifications)
        }

    @Test
    public fun `shows a notification after permission is available`() =
        runTest {
            val interaction = FakeUserInteraction()
            val notifier = RecordingNotifier()
            val tool = NotifyTool(notifier)
            val notifyArgs = NotifyArgs(title = "Готово", message = "Файл сохранён")
            val pipeline =
                ToolPipeline(
                    ToolRegistry(setOf(tool)),
                    interaction,
                    FakeClock(),
                    FakeAppLogger(),
                )

            val result = pipeline.run("notify", args(title = notifyArgs.title, message = notifyArgs.message))

            assertEquals(ToolOutcome.Success(NotifyCopy.NOTIFICATION_SHOWN), result)
            assertEquals(listOf(Capability.NOTIFICATIONS), interaction.capabilityRequests)
            assertEquals(listOf(notifyArgs), notifier.notifications)
        }

    @Test
    public fun `a sixty second permission wait does not consume the execution timeout`() =
        runTest {
            val interaction = DelayedPermissionInteraction()
            val notifier = RecordingNotifier()
            val delegate = NotifyTool(notifier)
            val tool =
                object : Tool by delegate {
                    override suspend fun execute(
                        args: JsonObject,
                        context: ToolContext,
                    ): ToolResult {
                        delay(1_000)
                        return delegate.execute(args, context)
                    }
                }
            val pipeline =
                ToolPipeline(
                    registry = ToolRegistry(setOf(tool)),
                    userInteraction = interaction,
                    clock = FakeClock(),
                    logger = FakeAppLogger(),
                    timeoutMillis = 5_000,
                )
            val notifyArgs = NotifyArgs(title = "Готово", message = "Файл сохранён")

            val result = pipeline.run("notify", args(title = notifyArgs.title, message = notifyArgs.message))

            assertEquals(ToolOutcome.Success(NotifyCopy.NOTIFICATION_SHOWN), result)
            assertEquals(listOf(Capability.NOTIFICATIONS), interaction.requestedCapabilities)
            assertEquals(listOf(notifyArgs), notifier.notifications)
        }

    @Test
    public fun `invalid notification arguments fail without showing a notification`() =
        runTest {
            val notifier = RecordingNotifier()
            val tool = NotifyTool(notifier)

            val result = tool.execute(args(title = "", message = "Текст"), toolContext())

            assertEquals(ToolResult.Failed(NotifyCopy.validationError(NotifyValidationError.EMPTY_TITLE)), result)
            assertEquals(emptyList<NotifyArgs>(), notifier.notifications)
        }

    @Test
    public fun `declares a safe notification tool`() {
        val tool = NotifyTool(RecordingNotifier())

        assertEquals("notify", tool.spec.name)
        assertEquals(Risk.SAFE, tool.spec.risk)
    }

    private fun args(
        title: String,
        message: String,
    ) = buildJsonObject {
        put("title", JsonPrimitive(title))
        put("message", JsonPrimitive(message))
    }

    private fun toolContext(): ToolContext = ToolContext(clock = FakeClock(), logger = FakeAppLogger())
}

private class RecordingNotifier : Notifier {
    val notifications: MutableList<NotifyArgs> = mutableListOf()

    override fun notify(
        title: String,
        message: String,
    ) {
        notifications += NotifyArgs(title, message)
    }
}

private class DelayedPermissionInteraction : UserInteraction {
    val requestedCapabilities = mutableListOf<Capability>()

    override suspend fun confirm(request: ApprovalRequest): Boolean = true

    override suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean = true

    override suspend fun ensure(capability: Capability): CapabilityResult {
        requestedCapabilities += capability
        delay(60_000)
        return CapabilityResult.Available
    }
}
