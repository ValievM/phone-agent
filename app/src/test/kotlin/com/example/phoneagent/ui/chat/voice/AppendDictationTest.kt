package com.example.phoneagent.ui.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class AppendDictationTest {
    @Test
    fun `appending to an empty draft uses trimmed dictation`() {
        assertEquals("привет", appendDictation("", "привет"))
        assertEquals("привет", appendDictation("", "  привет  "))
    }

    @Test
    fun `appending to a nonempty draft separates words with one space`() {
        assertEquals("Поставь будильник", appendDictation("Поставь", "будильник"))
        assertEquals("abc x", appendDictation("abc ", "x"))
    }

    @Test
    fun `existing trailing line break separates without adding a space`() {
        assertEquals("a\nb", appendDictation("a\n", "b"))
    }

    @Test
    fun `blank dictation leaves draft unchanged`() {
        assertEquals("draft", appendDictation("draft", ""))
        assertEquals("draft", appendDictation("draft", "   "))
    }
}
