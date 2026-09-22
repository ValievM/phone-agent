package com.example.phoneagent.domain.conversation

/** Decides how much of the conversation to send on the next request (store=false: history is always sent in full). */
public fun interface ContextWindowPolicy {
    public fun apply(history: List<HistoryItem>): List<HistoryItem>
}

public class LastNMessagesPolicy(
    private val maxItems: Int,
) : ContextWindowPolicy {
    init {
        require(maxItems > 0) { "maxItems must be positive, was $maxItems" }
    }

    override fun apply(history: List<HistoryItem>): List<HistoryItem> = history.takeLast(maxItems)
}
