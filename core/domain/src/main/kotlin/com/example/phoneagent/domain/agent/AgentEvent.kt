package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.ToolOutcome

public data class AgentConfig(
    public val maxSteps: Int = 5,
)

/** What the UI feed renders as [AgentLoop] runs a turn; the actual approval prompt is a separate concern (UserInteractionBridge). */
public sealed interface AgentEvent {
    public data class TextDelta(
        public val text: String,
    ) : AgentEvent

    public data class ToolCallStarted(
        public val call: ToolCall,
        public val description: CallDescription,
    ) : AgentEvent

    public data class ApprovalRequested(
        public val request: ApprovalRequest,
    ) : AgentEvent

    /** The tool cards of [callIds] wait for one shared answer (see [com.example.phoneagent.domain.tools.ToolPipeline.confirmBatch]). */
    public data class BatchApprovalRequested(
        public val callIds: List<String>,
    ) : AgentEvent

    public data class ToolCallFinished(
        public val call: ToolCall,
        public val outcome: ToolOutcome,
    ) : AgentEvent

    public data class TurnCompleted(
        public val newItems: List<HistoryItem>,
    ) : AgentEvent

    /**
     * [newItems]: whatever the turn had already produced before it failed (at least the user's
     * message), so the caller doesn't lose it from history.
     */
    public data class TurnFailed(
        public val error: AgentError,
        public val newItems: List<HistoryItem>,
    ) : AgentEvent
}
