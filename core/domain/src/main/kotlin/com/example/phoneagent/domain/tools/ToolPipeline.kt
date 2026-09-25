package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import com.example.phoneagent.domain.platform.info
import com.example.phoneagent.domain.platform.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Who answers the confirmation of one call: the pipeline itself, or an earlier shared question. */
public enum class Approval { ASK, GRANTED, DECLINED }

private const val TAG = "ToolPipeline"
private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
private const val DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024
private const val DEFAULT_MAX_BATCH_SIZE = 15
private const val UTF8_CONTINUATION_BYTE_MASK = 0xC0
private const val UTF8_CONTINUATION_BYTE_PATTERN = 0x80

/**
 * Every call goes through the same stages, regardless of which tool: exists in the registry, has
 * its required arguments, is approved per its [Risk], runs under a timeout, and has its success
 * text truncated — see architecture/core.md "Инструменты: плагины + конвейер". A tool never
 * implements any of this itself.
 */
public class ToolPipeline(
    private val registry: ToolRegistry,
    private val userInteraction: UserInteraction,
    private val clock: Clock,
    private val logger: AppLogger,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    private val maxBatchSize: Int = DEFAULT_MAX_BATCH_SIZE,
) {
    init {
        require(maxBatchSize >= 1) { "maxBatchSize must be at least 1" }
    }

    /** For [com.example.phoneagent.domain.agent.AgentLoop] to advertise available tools in each model request. */
    public val specs: List<ToolSpec> get() = registry.specs

    /** For [com.example.phoneagent.domain.agent.AgentLoop] to build a UI-facing event before running the call. */
    public fun describe(
        name: String,
        args: JsonObject,
    ): CallDescription? = registry.find(name)?.describe(args)

    /** Null for an unregistered name; SAFE tools never trigger [UserInteraction.confirm]. */
    public fun riskOf(name: String): Risk? = registry.find(name)?.spec?.risk

    /**
     * Calls of one model response that deserve a single shared question: plain-[Risk.CONFIRM] calls
     * with valid arguments, in groups of at most [maxBatchSize]. A group of one is not a batch (it
     * gets the ordinary question), and [Risk.SENSITIVE] calls are never grouped: their doc promises
     * a confirmation with details, one call at a time.
     */
    public fun batchGroups(calls: List<ToolCall>): List<List<ToolCall>> =
        calls
            .filter { call ->
                val tool = registry.find(call.name)
                tool != null && tool.spec.risk == Risk.CONFIRM && missingRequiredFields(tool.spec.inputSchema, call.arguments).isEmpty()
            }.chunked(maxBatchSize)
            .filter { it.size > 1 }

    /** Asks once for the whole [group] (from [batchGroups]); `true` approves every call, `false` declines every call. */
    public suspend fun confirmBatch(group: List<ToolCall>): Boolean {
        val requests =
            group.map { call ->
                val tool = checkNotNull(registry.find(call.name)) { "unknown tool in a batch: ${call.name}" }
                ApprovalRequest(tool.describe(call.arguments), tool.spec.risk, call.arguments)
            }
        val approved = userInteraction.confirmBatch(requests)
        logger.info(TAG, "batch confirmation answered", fields = mapOf("size" to group.size.toString(), "approved" to approved.toString()))
        return approved
    }

    public suspend fun run(
        name: String,
        args: JsonObject,
        approval: Approval = Approval.ASK,
        onApprovalResolved: suspend () -> Unit = {},
    ): ToolOutcome {
        val tool = registry.find(name)
        val outcome =
            if (tool ==
                null
            ) {
                ToolOutcome.Failed("unknown tool: $name")
            } else {
                runKnownTool(tool, args, approval, onApprovalResolved)
            }
        logger.info(TAG, "tool call finished", fields = mapOf("name" to name, "outcome" to outcome.kind()))
        return outcome
    }

    private suspend fun runKnownTool(
        tool: Tool,
        args: JsonObject,
        approval: Approval,
        onApprovalResolved: suspend () -> Unit,
    ): ToolOutcome {
        val missing = missingRequiredFields(tool.spec.inputSchema, args)
        return if (missing.isNotEmpty()) {
            ToolOutcome.Failed("missing required fields: ${missing.joinToString()}")
        } else if (!approve(tool, args, approval, onApprovalResolved)) {
            ToolOutcome.Rejected("user declined")
        } else {
            deniedCapability(tool)?.let { ToolOutcome.Failed(tool.deniedMessage(it)) } ?: execute(tool, args)
        }
    }

    /**
     * The first required permission the user denies. Asked before [execute] so that the time the user
     * spends on a system permission dialog does not count against the tool's timeout.
     */
    private suspend fun deniedCapability(tool: Tool): Capability? =
        tool.spec.requiredCapabilities.firstOrNull { userInteraction.ensure(it) is CapabilityResult.Denied }

    private suspend fun approve(
        tool: Tool,
        args: JsonObject,
        approval: Approval,
        onApprovalResolved: suspend () -> Unit,
    ): Boolean =
        when {
            approval == Approval.GRANTED -> {
                true
            }

            approval == Approval.DECLINED -> {
                false
            }

            tool.spec.risk == Risk.SAFE -> {
                true
            }

            else -> {
                val approved = userInteraction.confirm(ApprovalRequest(tool.describe(args), tool.spec.risk, args))
                onApprovalResolved()
                approved
            }
        }

    private suspend fun execute(
        tool: Tool,
        args: JsonObject,
    ): ToolOutcome =
        try {
            withTimeout(timeoutMillis) {
                when (val result = tool.execute(args, ToolContext(clock, logger))) {
                    is ToolResult.Success -> ToolOutcome.Success(truncate(result.text))
                    is ToolResult.Failed -> ToolOutcome.Failed(result.reason)
                }
            }
        } catch (_: TimeoutCancellationException) {
            ToolOutcome.Failed("timed out after ${timeoutMillis}ms")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // A bug in one tool must not end the whole turn: the model gets a normal failure. Only the
            // exception type is reported, its message can carry user data (paths, file names).
            logger.warn(TAG, "tool threw", fields = mapOf("name" to tool.spec.name), throwable = exception)
            ToolOutcome.Failed("tool failed: ${exception::class.simpleName}")
        }

    private fun truncate(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxOutputBytes) return text
        var end = maxOutputBytes
        while (end > 0 && bytes[end].isUtf8ContinuationByte()) end--
        return String(bytes, 0, end, Charsets.UTF_8)
    }

    private companion object {
        private fun Byte.isUtf8ContinuationByte(): Boolean = (toInt() and UTF8_CONTINUATION_BYTE_MASK) == UTF8_CONTINUATION_BYTE_PATTERN

        private fun missingRequiredFields(
            schema: JsonObject,
            args: JsonObject,
        ): List<String> {
            val required = (schema["required"] as? JsonArray) ?: return emptyList()
            return required
                .mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.jsonPrimitive?.content }
                .filterNot(args::containsKey)
        }

        private fun ToolOutcome.kind(): String =
            when (this) {
                is ToolOutcome.Success -> "success"
                is ToolOutcome.Rejected -> "rejected"
                is ToolOutcome.Failed -> "failed"
            }
    }
}
