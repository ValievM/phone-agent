package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import com.example.phoneagent.domain.platform.info
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "ToolPipeline"
private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
private const val DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024
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
) {
    /** For [com.example.phoneagent.domain.agent.AgentLoop] to build a UI-facing event before running the call. */
    public fun describe(
        name: String,
        args: JsonObject,
    ): CallDescription? = registry.find(name)?.describe(args)

    /** Null for an unregistered name; SAFE tools never trigger [UserInteraction.confirm]. */
    public fun riskOf(name: String): Risk? = registry.find(name)?.spec?.risk

    public suspend fun run(
        name: String,
        args: JsonObject,
    ): ToolOutcome {
        val tool = registry.find(name)
        val outcome = if (tool == null) ToolOutcome.Failed("unknown tool: $name") else runKnownTool(tool, args)
        logger.info(TAG, "tool call finished", fields = mapOf("name" to name, "outcome" to outcome.kind()))
        return outcome
    }

    private suspend fun runKnownTool(
        tool: Tool,
        args: JsonObject,
    ): ToolOutcome {
        val missing = missingRequiredFields(tool.spec.inputSchema, args)
        return if (missing.isNotEmpty()) {
            ToolOutcome.Failed("missing required fields: ${missing.joinToString()}")
        } else if (!approve(tool, args)) {
            ToolOutcome.Rejected("user declined")
        } else {
            execute(tool, args)
        }
    }

    private suspend fun approve(
        tool: Tool,
        args: JsonObject,
    ): Boolean =
        when (tool.spec.risk) {
            Risk.SAFE -> true
            Risk.CONFIRM, Risk.SENSITIVE -> userInteraction.confirm(ApprovalRequest(tool.describe(args), tool.spec.risk))
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
