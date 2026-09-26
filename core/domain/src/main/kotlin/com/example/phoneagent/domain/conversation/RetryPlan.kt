package com.example.phoneagent.domain.conversation

public sealed interface RetryPlan {
    public data object Restart : RetryPlan

    public data object Continue : RetryPlan
}

/** Selects whether retry should restart the last user turn or continue its completed tool history. */
public fun classifyRetry(history: List<HistoryItem>): RetryPlan {
    val lastUserIndex = history.indexOfLast { it is HistoryItem.UserMessage }
    val callIds = mutableSetOf<CallId>()
    val outputIds = mutableSetOf<CallId>()
    var hasToolCall = false

    for (index in lastUserIndex + 1 until history.size) {
        when (val item = history[index]) {
            is HistoryItem.ToolCallItem -> {
                hasToolCall = true
                callIds += item.callId
            }

            is HistoryItem.ToolOutput -> {
                outputIds += item.callId
            }

            else -> {
                Unit
            }
        }
    }

    return if (hasToolCall && outputIds.containsAll(callIds)) RetryPlan.Continue else RetryPlan.Restart
}
