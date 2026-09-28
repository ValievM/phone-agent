package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import kotlinx.serialization.json.JsonObject

public const val DEFAULT_TOOL_EXECUTION_TIMEOUT_MILLIS: Long = 5_000L

public enum class Risk {
    SAFE,
    CONFIRM,
    SENSITIVE,
}

public data class ToolSpec(
    public val name: String,
    public val description: String,
    public val inputSchema: JsonObject,
    public val risk: Risk,
    /**
     * Permissions the tool needs. The pipeline asks for them before the timeout starts, so the
     * time the user spends on the system dialog does not count against it.
     */
    public val requiredCapabilities: Set<Capability> = emptySet(),
    /** Execution-only budget; null uses the pipeline default. */
    public val executionTimeoutMillis: Long? = null,
)

public data class CallDescription(
    public val title: String,
    public val summary: String,
    public val details: List<String> = emptyList(),
)

public data class ToolContext(
    public val clock: Clock,
    public val logger: AppLogger,
    /** Effective execution budget shared with the tool's internal stopping rule. */
    public val executionTimeoutMillis: Long = DEFAULT_TOOL_EXECUTION_TIMEOUT_MILLIS,
)

public sealed interface ToolResult {
    public data class Success(
        public val text: String,
        public val content: ToolContent? = null,
    ) : ToolResult

    public data class Failed(
        public val reason: String,
    ) : ToolResult
}

public interface Tool {
    public val spec: ToolSpec

    public fun describe(args: JsonObject): CallDescription

    /** Optional editor for a single CONFIRM call. Other risks are never offered one by the pipeline. */
    public fun argumentEditor(args: JsonObject): ToolArgumentEditor? = null

    /** What the model is told when the user denies [capability]. */
    public fun deniedMessage(capability: Capability): String = "permission denied: ${capability.name}"

    public suspend fun execute(
        args: JsonObject,
        context: ToolContext,
    ): ToolResult

    /** Recreates optional structured content from this tool's existing saved output format. */
    public fun restoreContent(outputText: String): ToolContent? = null
}
