package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.ConfirmResult
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

/** Stable tool-timeout reason; the UI maps this marker to localized copy. */
public const val TOOL_EXECUTION_TIMEOUT_REASON: String = "tool execution timed out"

/** Result of confirming a call: the pipeline itself, an earlier shared question, or no question shown. */
public enum class Approval { ASK, GRANTED, DECLINED, NOT_PRESENTED }

private const val TAG = "ToolPipeline"
private const val DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024
private const val DEFAULT_MAX_BATCH_SIZE = 15
private const val BATCH_DESCRIPTION_UNAVAILABLE_REASON =
    "not shown to user: one action description could not be built; " +
        "no calls in the batch were run"
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
    private val timeoutMillis: Long = DEFAULT_TOOL_EXECUTION_TIMEOUT_MILLIS,
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
    ): CallDescription? {
        val tool = registry.find(name) ?: return null
        // A broken describe must not break the turn: SAFE keeps running under a neutral card,
        // and a refusing call is handled by the risk guard in [runKnownTool].
        return describeSafely(tool, args) ?: CallDescription(title = tool.spec.name, summary = "")
    }

    /** Null for an unregistered name; SAFE tools never trigger [UserInteraction.confirm]. */
    public fun riskOf(name: String): Risk? = registry.find(name)?.spec?.risk

    /** Restores optional typed content through the tool that owns the saved output format. */
    public fun restoreContent(
        name: String,
        outputText: String,
    ): ToolContent? {
        val tool = registry.find(name) ?: return null
        return try {
            tool.restoreContent(outputText)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            logger.warn(
                TAG,
                "tool content restore failed",
                fields = mapOf("name" to name, "error" to exception::class.simpleName.orEmpty()),
            )
            null
        }
    }

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

    /**
     * Asks once for the whole [group] (from [batchGroups]); `true` approves every call, `false` declines every call.
     * A shared question is only for known plain-CONFIRM calls, as [batchGroups] promises: anything else in the group
     * declines it without showing a sheet. Every description is built before [onPrepared] lets the agent publish an
     * approval event to the UI, and a group with a description that could not be built is never shown.
     */
    public suspend fun confirmBatch(
        group: List<ToolCall>,
        onPrepared: suspend (List<CallDescription?>) -> Unit = {},
    ): Boolean {
        val tools = group.map { call -> registry.find(call.name)?.takeIf { it.spec.risk == Risk.CONFIRM } }
        if (tools.any { it == null }) {
            logger.warn(TAG, "batch rejected: not a plain-CONFIRM group", fields = mapOf("size" to group.size.toString()))
            return false
        }
        val descriptions = group.indices.map { index -> describeSafely(checkNotNull(tools[index]), group[index].arguments) }
        onPrepared(descriptions)
        return descriptions.none { it == null } && askBatch(userInteraction, logger, group, descriptions)
    }

    public suspend fun run(
        name: String,
        args: JsonObject,
        approval: Approval = Approval.ASK,
        requireConfirmation: Boolean = false,
        onApprovalResolved: suspend () -> Unit = {},
        onArgumentsResolved: suspend (JsonObject) -> Unit = {},
    ): ToolOutcome {
        val tool = registry.find(name)
        val outcome =
            if (tool == null) {
                ToolOutcome.Failed("unknown tool: $name")
            } else {
                runKnownTool(RunContext(tool, args, approval, requireConfirmation, onApprovalResolved, onArgumentsResolved))
            }
        logger.info(TAG, "tool call finished", fields = mapOf("name" to name, "outcome" to outcome.kind()))
        return outcome
    }

    private suspend fun runKnownTool(context: RunContext): ToolOutcome {
        val missing = missingRequiredFields(context.tool.spec.inputSchema, context.args)
        return when {
            missing.isNotEmpty() -> ToolOutcome.Failed("missing required fields: ${missing.joinToString()}")
            context.approval == Approval.NOT_PRESENTED -> ToolOutcome.NotShown(BATCH_DESCRIPTION_UNAVAILABLE_REASON)
            else -> confirmAndRun(context)
        }
    }

    private suspend fun confirmAndRun(context: RunContext): ToolOutcome {
        // A confirmation request needs a usable description; SAFE tools reach this path only when explicitly repeated.
        val needsDescription =
            (context.tool.spec.risk != Risk.SAFE || context.requireConfirmation) && context.approval != Approval.DECLINED
        val description = if (needsDescription) describeSafely(context.tool, context.args) else null
        if (needsDescription && description == null) {
            return ToolOutcome.Failed("the tool could not describe its action, so it was not run")
        }
        val editor =
            if (context.tool.spec.risk == Risk.CONFIRM && context.approval == Approval.ASK) {
                context.tool.argumentEditor(context.args)
            } else {
                null
            }
        val request = description?.let { ApprovalRequest(it, context.tool.spec.risk, context.args, editor) }
        return when (val answer = approve(userInteraction, context, request)) {
            ConfirmResult.Rejected -> ToolOutcome.Rejected("user declined")
            is ConfirmResult.Approved -> runApproved(context, editor, answer.arguments)
        }
    }

    private suspend fun runApproved(
        context: RunContext,
        editor: ToolArgumentEditor?,
        finalArgs: JsonObject,
    ): ToolOutcome {
        val validationError = approvalError(context, editor, finalArgs)
        if (validationError != null) return ToolOutcome.Failed(validationError)
        context.onArgumentsResolved(finalArgs)
        return deniedCapability(userInteraction, context.tool)?.let { ToolOutcome.Failed(context.tool.deniedMessage(it)) }
            ?: execute(context.tool, finalArgs)
    }

    private suspend fun execute(
        tool: Tool,
        args: JsonObject,
    ): ToolOutcome {
        val executionTimeoutMillis = tool.spec.executionTimeoutMillis ?: timeoutMillis
        return try {
            withTimeout(executionTimeoutMillis) {
                when (
                    val result =
                        tool.execute(
                            args,
                            ToolContext(clock, logger, executionTimeoutMillis),
                        )
                ) {
                    is ToolResult.Success -> ToolOutcome.Success(truncateOutput(result.text, maxOutputBytes), result.content)
                    is ToolResult.Failed -> ToolOutcome.Failed(result.reason)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            logger.warn(
                TAG,
                "tool timed out",
                fields =
                    mapOf(
                        "name" to tool.spec.name,
                        "exceptionType" to timeout::class.simpleName.orEmpty(),
                        "timeoutMillis" to executionTimeoutMillis.toString(),
                    ),
            )
            ToolOutcome.Failed(TOOL_EXECUTION_TIMEOUT_REASON)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // A bug in one tool must not end the whole turn: the model gets a normal failure. Only the
            // exception type is reported, its message can carry user data (paths, file names).
            logger.warn(TAG, "tool threw", fields = mapOf("name" to tool.spec.name), throwable = exception)
            ToolOutcome.Failed("tool failed: ${exception::class.simpleName}")
        }
    }

    private companion object {
        private fun ToolOutcome.kind(): String =
            when (this) {
                is ToolOutcome.Success -> "success"
                is ToolOutcome.Rejected -> "rejected"
                is ToolOutcome.NotShown -> "not_shown"
                is ToolOutcome.Failed -> "failed"
            }
    }
}

private data class RunContext(
    val tool: Tool,
    val args: JsonObject,
    val approval: Approval,
    val requireConfirmation: Boolean,
    val onApprovalResolved: suspend () -> Unit,
    val onArgumentsResolved: suspend (JsonObject) -> Unit,
)

/** Asked before execution so a system permission dialog does not consume the tool's timeout. */
private suspend fun deniedCapability(
    userInteraction: UserInteraction,
    tool: Tool,
): Capability? = tool.spec.requiredCapabilities.firstOrNull { userInteraction.ensure(it) is CapabilityResult.Denied }

private suspend fun approve(
    userInteraction: UserInteraction,
    context: RunContext,
    request: ApprovalRequest?,
): ConfirmResult =
    when {
        context.approval == Approval.GRANTED -> {
            ConfirmResult.Approved(context.args)
        }

        context.approval == Approval.DECLINED || context.approval == Approval.NOT_PRESENTED -> {
            ConfirmResult.Rejected
        }

        context.tool.spec.risk == Risk.SAFE && !context.requireConfirmation -> {
            ConfirmResult.Approved(context.args)
        }

        else -> {
            val approved = userInteraction.confirm(checkNotNull(request))
            context.onApprovalResolved()
            approved
        }
    }

private fun approvalError(
    context: RunContext,
    editor: ToolArgumentEditor?,
    finalArgs: JsonObject,
): String? {
    val missing = missingRequiredFields(context.tool.spec.inputSchema, finalArgs)
    return when {
        finalArgs != context.args && editor == null -> "edited arguments are not allowed"
        missing.isNotEmpty() -> "missing required fields: ${missing.joinToString()}"
        else -> editor?.review(finalArgs)?.validationError
    }
}

private fun missingRequiredFields(
    schema: JsonObject,
    args: JsonObject,
): List<String> {
    val required = (schema["required"] as? JsonArray) ?: return emptyList()
    return required
        .mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.jsonPrimitive?.content }
        .filterNot(args::containsKey)
}

/** The one shared question of a batch whose every description was built; kept outside the class to keep it small. */
private suspend fun askBatch(
    userInteraction: UserInteraction,
    logger: AppLogger,
    group: List<ToolCall>,
    descriptions: List<CallDescription?>,
): Boolean {
    val requests =
        group.indices.map { index -> ApprovalRequest(checkNotNull(descriptions[index]), Risk.CONFIRM, group[index].arguments) }
    val approved = userInteraction.confirmBatch(requests)
    logger.info(
        TAG,
        "batch confirmation answered",
        fields = mapOf("size" to group.size.toString(), "approved" to approved.toString()),
    )
    return approved
}

private fun truncateOutput(
    text: String,
    maxOutputBytes: Int,
): String {
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxOutputBytes) return text
    var end = maxOutputBytes
    while (end > 0 && bytes[end].isUtf8ContinuationByte()) end--
    return String(bytes, 0, end, Charsets.UTF_8)
}

private fun Byte.isUtf8ContinuationByte(): Boolean = (toInt() and UTF8_CONTINUATION_BYTE_MASK) == UTF8_CONTINUATION_BYTE_PATTERN

/** Null when the tool's own describe blew up; [CancellationException] always propagates. */
private fun describeSafely(
    tool: Tool,
    args: JsonObject,
): CallDescription? =
    try {
        tool.describe(args)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (expected: Exception) {
        null
    }
