package com.example.phoneagent.domain.conversation

import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPlanTest {
    @Test
    fun `a turn without tool calls restarts`() {
        val history = listOf(user("question"), HistoryItem.AssistantMessage(ItemId("answer"), "partial"))

        assertEquals(RetryPlan.Restart, classifyRetry(history))
    }

    @Test
    fun `a turn with every tool call paired continues`() {
        val history = listOf(user("question"), call("call-1"), output("call-1"), call("call-2"), output("call-2"))

        assertEquals(RetryPlan.Continue, classifyRetry(history))
    }

    @Test
    fun `an unmatched tool call restarts`() {
        val history = listOf(user("question"), call("call-1"), output("other-call"))

        assertEquals(RetryPlan.Restart, classifyRetry(history))
    }

    @Test
    fun `only the latest user turn is classified`() {
        val history =
            listOf(
                user("old-question"),
                call("old-unmatched"),
                user("latest-question"),
                call("latest-call"),
                output("latest-call"),
            )

        assertEquals(RetryPlan.Continue, classifyRetry(history))
    }

    @Test
    fun `parallel calls pair with outputs by call id`() {
        val history =
            listOf(
                user("question"),
                call("call-1"),
                call("call-2"),
                output("call-1"),
                output("call-2"),
            )

        assertEquals(RetryPlan.Continue, classifyRetry(history))
    }

    @Test
    fun `empty and user-only histories restart`() {
        assertEquals(RetryPlan.Restart, classifyRetry(emptyList()))
        assertEquals(RetryPlan.Restart, classifyRetry(listOf(user("question"))))
    }

    private fun user(id: String) = HistoryItem.UserMessage(ItemId(id), "question")

    private fun call(id: String) = HistoryItem.ToolCallItem(ItemId("$id-item"), CallId(id), "find_files", buildJsonObject {})

    private fun output(id: String) = HistoryItem.ToolOutput(ItemId("$id-output"), CallId(id), "output")
}
