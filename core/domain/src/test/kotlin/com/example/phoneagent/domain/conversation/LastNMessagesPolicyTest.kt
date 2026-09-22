package com.example.phoneagent.domain.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LastNMessagesPolicyTest {
    @Test
    fun `history shorter than the limit is returned unchanged`() {
        val policy = LastNMessagesPolicy(maxItems = 3)
        val history = listOf(userMessage("1"), userMessage("2"))

        assertEquals(history, policy.apply(history))
    }

    @Test
    fun `history longer than the limit keeps only the most recent items, in order`() {
        val policy = LastNMessagesPolicy(maxItems = 2)
        val history = listOf(userMessage("1"), userMessage("2"), userMessage("3"))

        assertEquals(listOf(userMessage("2"), userMessage("3")), policy.apply(history))
    }

    @Test
    fun `maxItems must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { LastNMessagesPolicy(maxItems = 0) }
    }

    private fun userMessage(text: String): HistoryItem = HistoryItem.UserMessage(text, text)
}
