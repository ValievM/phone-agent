package com.example.phoneagent.domain.conversation

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

public sealed interface HistoryItem {
    public val id: String

    public data class UserMessage(
        override val id: String,
        public val text: String,
    ) : HistoryItem

    public data class AssistantMessage(
        override val id: String,
        public val text: String,
    ) : HistoryItem

    public data class ToolCallItem(
        override val id: String,
        public val callId: String,
        public val name: String,
        public val arguments: JsonObject,
    ) : HistoryItem

    public data class ToolOutput(
        override val id: String,
        public val callId: String,
        public val output: String,
    ) : HistoryItem

    public data class Opaque(
        override val id: String,
        public val provider: String,
        public val payload: JsonElement,
    ) : HistoryItem
}

public data class ToolCall(
    public val id: String,
    public val name: String,
    public val arguments: JsonObject,
)
