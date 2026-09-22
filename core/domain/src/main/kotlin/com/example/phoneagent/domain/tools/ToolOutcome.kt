package com.example.phoneagent.domain.tools

/** The end result of a call after the whole pipeline (validation, approval, execution, truncation). */
public sealed interface ToolOutcome {
    public data class Success(
        public val text: String,
    ) : ToolOutcome

    public data class Rejected(
        public val reason: String,
    ) : ToolOutcome

    public data class Failed(
        public val reason: String,
    ) : ToolOutcome
}
