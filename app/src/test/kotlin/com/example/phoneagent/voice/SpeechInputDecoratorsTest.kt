package com.example.phoneagent.voice

import com.example.phoneagent.domain.contracts.SpeechInputContract
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeSpeechInput
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechInputDecoratorsTest {
    private val request = SpeechRequest("ru-RU")

    @Test
    fun `permission available passes delegate events`() =
        runTest {
            val delegate = FakeSpeechInput().apply { script(SpeechEvent.Partial("hello"), SpeechEvent.Final("hello")) }
            val interaction = FakeUserInteraction()

            val events = PermissionGatedSpeechInput(delegate, interaction).listen(request).toList()

            assertEquals(
                listOf(SpeechEvent.Listening, SpeechEvent.Partial("hello"), SpeechEvent.Final("hello")),
                events,
            )
            assertEquals(listOf(Capability.MICROPHONE), interaction.capabilityRequests)
            assertEquals(listOf(request), delegate.requests)
        }

    @Test
    fun `permission denied fails without starting delegate`() =
        runTest {
            val delegate = FakeSpeechInput()
            val interaction = FakeUserInteraction().apply { capabilityResult = CapabilityResult.Denied }

            val events = PermissionGatedSpeechInput(delegate, interaction).listen(request).toList()

            assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.PERMISSION_DENIED)), events)
            assertTrue(delegate.requests.isEmpty())
        }

    @Test
    fun `cancelling gated session releases delegate`() =
        runTest {
            val delegate = HoldingSpeechInput()
            val collector = async { PermissionGatedSpeechInput(delegate, FakeUserInteraction()).listen(request).toList() }
            delegate.started.await()

            collector.cancelAndJoin()

            assertEquals(1, delegate.released)
        }

    @Test
    fun `unavailable primary falls back`() =
        runTest {
            assertFallback(SpeechFailure.UNAVAILABLE)
        }

    @Test
    fun `unavailable language primary falls back`() =
        runTest {
            assertFallback(SpeechFailure.LANGUAGE_UNAVAILABLE)
        }

    @Test
    fun `permission denied primary falls back`() =
        runTest {
            assertFallback(SpeechFailure.PERMISSION_DENIED)
        }

    @Test
    fun `no match does not fall back`() =
        runTest {
            val primary = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.NO_MATCH))
            val fallback = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Final("fallback"))

            val events = FallbackSpeechInput(primary, fallback, FakeAppLogger()).listen(request).toList()

            assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.NO_MATCH)), events)
            assertTrue(fallback.requests.isEmpty())
        }

    @Test
    fun `eligible failure after partial does not fall back`() =
        runTest {
            val primary =
                ScriptedSpeechInput(
                    SpeechEvent.Listening,
                    SpeechEvent.Partial("secret partial"),
                    SpeechEvent.Failed(SpeechFailure.UNAVAILABLE),
                )
            val fallback = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Final("fallback"))

            val events = FallbackSpeechInput(primary, fallback, FakeAppLogger()).listen(request).toList()

            assertEquals(
                listOf(SpeechEvent.Listening, SpeechEvent.Partial("secret partial"), SpeechEvent.Failed(SpeechFailure.UNAVAILABLE)),
                events,
            )
            assertTrue(fallback.requests.isEmpty())
        }

    @Test
    fun `listening is not duplicated across fallback`() =
        runTest {
            val primary = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
            val fallback = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Final("done"))

            val events = FallbackSpeechInput(primary, fallback, FakeAppLogger()).listen(request).toList()

            assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("done")), events)
        }

    @Test
    fun `cancelling fallback releases active input`() =
        runTest {
            val primary = ScriptedSpeechInput(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
            val fallback = HoldingSpeechInput()
            val collector = async { FallbackSpeechInput(primary, fallback, FakeAppLogger()).listen(request).toList() }
            fallback.started.await()

            collector.cancelAndJoin()

            assertEquals(1, fallback.released)
        }

    @Test
    fun `final from primary does not fall back`() =
        runTest {
            val primary = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Final("done"))
            val fallback = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Final("fallback"))

            val events = FallbackSpeechInput(primary, fallback, FakeAppLogger()).listen(request).toList()

            assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("done")), events)
            assertTrue(fallback.requests.isEmpty())
        }

    @Test
    fun `fallback logs switch without speech text`() =
        runTest {
            val secret = "sensitive spoken text"
            val primary = ScriptedSpeechInput(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
            val fallback = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Final(secret))
            val logger = FakeAppLogger()

            FallbackSpeechInput(primary, fallback, logger).listen(request).toList()

            assertEquals(1, logger.events.size)
            assertEquals(LogLevel.INFO, logger.events.single().level)
            assertEquals(mapOf("layer" to "fallback", "outcome" to "switched_to_system_dialog"), logger.events.single().fields)
            assertTrue(logger.events.none { secret in it.message || it.fields.values.any(secret::contains) })
        }

    private suspend fun assertFallback(reason: SpeechFailure) {
        val primary = ScriptedSpeechInput(SpeechEvent.Failed(reason))
        val fallback = ScriptedSpeechInput(SpeechEvent.Listening, SpeechEvent.Final("fallback"))

        val events = FallbackSpeechInput(primary, fallback, FakeAppLogger()).listen(request).toList()

        assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("fallback")), events)
        assertEquals(listOf(request), fallback.requests)
    }

    private class ScriptedSpeechInput(
        vararg val events: SpeechEvent,
    ) : SpeechInput {
        val requests = mutableListOf<SpeechRequest>()

        override fun listen(request: SpeechRequest): Flow<SpeechEvent> =
            flow {
                requests += request
                events.forEach { emit(it) }
            }
    }

    private class HoldingSpeechInput : SpeechInput {
        val started = CompletableDeferred<Unit>()
        var released = 0
            private set

        override fun listen(request: SpeechRequest): Flow<SpeechEvent> =
            flow {
                emit(SpeechEvent.Listening)
                started.complete(Unit)
                try {
                    awaitCancellationSignal()
                } finally {
                    released++
                }
            }

        private suspend fun awaitCancellationSignal(): Nothing = kotlinx.coroutines.awaitCancellation()
    }
}

class FallbackSpeechInputContractTest : SpeechInputContract() {
    private val primary = FakeSpeechInput()
    private val fallback = FakeSpeechInput()
    private val input = FallbackSpeechInput(primary, fallback, FakeAppLogger())

    override fun subject(): SpeechInput = input

    override fun offer(vararg events: SpeechEvent) {
        primary.script(*events)
    }

    override fun recordedRequests(): List<SpeechRequest> = primary.requests

    override fun assertReleased() {
        primary.assertReleased()
        fallback.assertReleased()
    }
}
