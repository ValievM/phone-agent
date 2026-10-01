@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)


package com.example.phoneagent.domain.contracts

import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared behavioral contract for speech input implementations. */
public abstract class SpeechInputContract {
    protected abstract fun subject(): SpeechInput

    protected abstract fun offer(vararg events: SpeechEvent)

    protected abstract fun recordedRequests(): List<SpeechRequest>

    protected abstract fun assertReleased()

    @Test
    public fun `a final result follows listening and completes`() = runTest {
        offer(SpeechEvent.Final("привет"))

        val events = subject().listen(request()).toList()

        assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("привет")), events)
    }

    @Test
    public fun `partial results retain order after listening`() = runTest {
        offer(SpeechEvent.Partial("при"), SpeechEvent.Partial("приве"), SpeechEvent.Final("привет"))

        val events = subject().listen(request()).toList()

        assertEquals(
            listOf(
                SpeechEvent.Listening,
                SpeechEvent.Partial("при"),
                SpeechEvent.Partial("приве"),
                SpeechEvent.Final("привет"),
            ),
            events,
        )
    }

    @Test
    public fun `events after the first terminal event are not emitted`() = runTest {
        offer(SpeechEvent.Final("готово"), SpeechEvent.Partial("лишнее"), SpeechEvent.Failed(SpeechFailure.NETWORK))

        val events = subject().listen(request()).toList()

        assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("готово")), events)
        assertEquals(1, events.count { it is SpeechEvent.Final || it is SpeechEvent.Failed })
    }

    @Test
    public fun `blank final results become no match failures`() = runTest {
        listOf("", " \t\n ").forEach { text ->
            offer(SpeechEvent.Final(text))

            val events = subject().listen(request()).toList()

            assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.NO_MATCH)), events)
            assertFalse(events.any { it is SpeechEvent.Final })
        }
    }

    @Test
    public fun `cancelling a collector stops events and releases its session`() = runTest {
        offer(SpeechEvent.Partial("при"), SpeechEvent.Final("привет"))
        val listening = CompletableDeferred<Unit>()
        val continueCollection = CompletableDeferred<Unit>()
        val collected = mutableListOf<SpeechEvent>()
        val collection = async {
            subject().listen(request()).collect { event ->
                collected += event
                if (event == SpeechEvent.Listening) {
                    listening.complete(Unit)
                    continueCollection.await()
                }
            }
        }

        advanceUntilIdle()
        listening.await()


        collection.cancel()
        advanceUntilIdle()

        assertEquals(listOf(SpeechEvent.Listening), collected)
        assertReleased()
    }

    @Test
    public fun `a concurrent session is busy without interrupting the active session`() = runTest {
        offer(SpeechEvent.Final("первый"))
        val listening = CompletableDeferred<Unit>()
        val continueFirst = CompletableDeferred<Unit>()
        val firstEvents = mutableListOf<SpeechEvent>()
        val first = async {
            subject().listen(request()).collect { event ->
                firstEvents += event
                if (event == SpeechEvent.Listening) {
                    listening.complete(Unit)
                    continueFirst.await()
                }
            }
        }
        advanceUntilIdle()
        listening.await()

        val second = subject().listen(request()).toList()
        continueFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.BUSY)), second)
        assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("первый")), firstEvents)
        first.await()
    }

    @Test
    public fun `requests preserve language and offline preference`() = runTest {
        offer(SpeechEvent.Final("привет"))
        val request = SpeechRequest(languageTag = "ru-RU", offlineOnly = true)

        subject().listen(request).toList()

        assertEquals(listOf(request), recordedRequests())
    }

    @Test
    public fun `expected failures are emitted as values`() = runTest {
        offer(SpeechEvent.Failed(SpeechFailure.NETWORK))

        val events = subject().listen(request()).toList()

        assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.NETWORK)), events)
    }

    @Test
    public fun `a completed session releases busy state for the next request`() = runTest {
        offer(SpeechEvent.Final("первый"))
        offer(SpeechEvent.Final("второй"))
        val input = subject()

        assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("первый")), input.listen(request()).toList())
        assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("второй")), input.listen(request()).toList())
    }

    private fun request(): SpeechRequest = SpeechRequest(languageTag = "ru-RU")
}
