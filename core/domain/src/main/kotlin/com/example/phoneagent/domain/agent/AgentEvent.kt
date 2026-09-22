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

    public data class ToolCallFinished(
        public val call: ToolCall,
        public val outcome: ToolOutcome,
    ) : AgentEvent

    public data class TurnCompleted(
        public val newItems: List<HistoryItem>,
    ) : AgentEvent

    public data class TurnFailed(
        public val error: AgentError,
    ) : AgentEvent
}
