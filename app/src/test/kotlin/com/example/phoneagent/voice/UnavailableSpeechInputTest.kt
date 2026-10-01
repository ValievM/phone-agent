package com.example.phoneagent.voice

import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnavailableSpeechInputTest {
    @Test
    fun `listening produces one unavailable failure and completes`() =
        runTest {
            val events = UnavailableSpeechInput().listen(SpeechRequest("ru-RU")).toList()

            assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE)), events)
        }

    @Test
    fun `request language and offline preference do not change unavailable result`() =
        runTest {
            val events = UnavailableSpeechInput().listen(SpeechRequest("en-US", offlineOnly = true)).toList()

            assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE)), events)
        }
}
