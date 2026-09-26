package com.example.phoneagent.domain.tools

/** The end result of a call after the whole pipeline (validation, approval, execution, truncation). */
public sealed interface ToolOutcome {
    public data class Success(
        public val text: String,
        public val content: ToolContent? = null,
    ) : ToolOutcome

    public data class Rejected(
        public val reason: String,
    ) : ToolOutcome

    public data class NotShown(
        public val reason: String,
    ) : ToolOutcome

    public data class Failed(
        public val reason: String,
    ) : ToolOutcome
}

private const val REJECTED_LABEL = "Rejected: "
private const val NOT_SHOWN_LABEL = "Not shown: "
private const val FAILED_LABEL = "Failed: "

/**
 * Text saved in history and sent to the model on the next turn. Success keeps its own text; other outcomes use a
 * prefix so the chat can restore their status from that same saved value.
 */
public fun ToolOutcome.toOutputText(): String =
    when (this) {
        is ToolOutcome.Success -> text
        is ToolOutcome.Rejected -> REJECTED_LABEL + reason
        is ToolOutcome.NotShown -> NOT_SHOWN_LABEL + reason
        is ToolOutcome.Failed -> FAILED_LABEL + reason
    }

/**
 * Reads a saved result back, so the chat can draw the card of a call again after the app is reopened.
 * Only a label at the very start counts. A success text that itself begins with one of the labels can restore as a
 * non-success card, but the saved text sent to the model remains unchanged.
 */
public fun toolOutcomeFromOutputText(text: String): ToolOutcome =
    when {
        text.startsWith(REJECTED_LABEL) -> ToolOutcome.Rejected(text.removePrefix(REJECTED_LABEL))
        text.startsWith(NOT_SHOWN_LABEL) -> ToolOutcome.NotShown(text.removePrefix(NOT_SHOWN_LABEL))
        text.startsWith(FAILED_LABEL) -> ToolOutcome.Failed(text.removePrefix(FAILED_LABEL))
        else -> ToolOutcome.Success(text)
    }
