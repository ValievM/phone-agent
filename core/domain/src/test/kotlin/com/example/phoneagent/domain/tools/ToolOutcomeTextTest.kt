package com.example.phoneagent.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The text a finished tool call leaves in the saved history. It is what the model reads next turn and
 * what the chat uses to draw the card again after the app is reopened, so both directions are tested.
 */
class ToolOutcomeTextTest {
    @Test
    fun `a success is saved as its own text`() {
        assertEquals("Будильник поставлен на 07:30", ToolOutcome.Success("Будильник поставлен на 07:30").toOutputText())
    }

    @Test
    fun `a rejection is saved with its label`() {
        assertEquals("Rejected: declined by the user", ToolOutcome.Rejected("declined by the user").toOutputText())
    }

    @Test
    fun `a failure is saved with its label`() {
        assertEquals("Failed: no clock app", ToolOutcome.Failed("no clock app").toOutputText())
    }

    @Test
    fun `a success text reads back as a success`() {
        assertEquals(ToolOutcome.Success("Будильник поставлен на 07:30"), toolOutcomeFromOutputText("Будильник поставлен на 07:30"))
    }

    @Test
    fun `a rejection reads back with its reason`() {
        assertEquals(ToolOutcome.Rejected("declined by the user"), toolOutcomeFromOutputText("Rejected: declined by the user"))
    }

    @Test
    fun `a failure reads back with its reason`() {
        assertEquals(ToolOutcome.Failed("no clock app"), toolOutcomeFromOutputText("Failed: no clock app"))
    }

    @Test
    fun `a not shown result round trips with Cyrillic and quotation marks`() {
        val expected = ToolOutcome.NotShown("описание действия не построилось для «Часы»")
        val text = expected.toOutputText()

        assertEquals("Not shown: описание действия не построилось для «Часы»", text)
        assertEquals(expected, toolOutcomeFromOutputText(text))
    }

    @Test
    fun `a legacy not shown rejection still reads as rejected`() {
        val reason = "not shown to user: one action description could not be built; no calls in the batch were run"

        assertEquals(ToolOutcome.Rejected(reason), toolOutcomeFromOutputText("Rejected: $reason"))
    }

    @Test
    fun `empty and unusual not shown reasons never read as success`() {
        val texts = listOf("Not shown: ", "Not shown: причина повреждена: Failed: нет")

        texts.forEach { text ->
            val outcome = toolOutcomeFromOutputText(text)

            assertEquals(ToolOutcome.NotShown(text.removePrefix("Not shown: ")), outcome)
            assertEquals(text, outcome.toOutputText())
        }
    }

    @Test
    fun `words that only mention a failure inside a success text do not turn it into one`() {
        val text = "Alarm set. Failed: none, Rejected: none"

        assertEquals(ToolOutcome.Success(text), toolOutcomeFromOutputText(text))
    }

    @Test
    fun `a label without a reason reads back with an empty reason`() {
        assertEquals(ToolOutcome.Failed(""), toolOutcomeFromOutputText("Failed: "))
    }
}
