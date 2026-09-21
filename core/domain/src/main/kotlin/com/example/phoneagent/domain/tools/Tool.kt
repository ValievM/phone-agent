package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import kotlinx.serialization.json.JsonObject

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
)

public data class CallDescription(
    public val title: String,
    public val summary: String,
    public val details: List<String> = emptyList(),
)

public data class ToolContext(
    public val clock: Clock,
    public val logger: AppLogger,
)

public sealed interface ToolResult {
    public data class Success(
        public val text: String,
    ) : ToolResult

    public data class Failed(
        public val reason: String,
    ) : ToolResult
}

public interface Tool {
    public val spec: ToolSpec

    public fun describe(args: JsonObject): CallDescription

    public suspend fun execute(
        args: JsonObject,
        context: ToolContext,
    ): ToolResult
}
