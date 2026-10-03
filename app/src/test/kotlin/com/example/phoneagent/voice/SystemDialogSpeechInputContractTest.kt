@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.phoneagent.voice

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.contracts.SpeechInputContract
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SystemDialogSpeechInputContractTest : SpeechInputContract() {
    private val bridge = SpeechRequestBridge()
    private val logger = FakeAppLogger()
    private val responses = Channel<RecognizerOutcome>(Channel.UNLIMITED)
    private val requests = mutableListOf<SpeechRequest>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val hostScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var completionGate: CompletableDeferred<Unit>? = null
    private val host =
        hostScope.launch {
            bridge.pending.filterNotNull().filter { !it.launched }.collect { pending ->
                bridge.markLaunched(pending.id)
                completionGate?.await()
                bridge.complete(responses.receive())
            }
        }

    override fun holdCompletion() {
        completionGate = CompletableDeferred()
    }

    override fun releaseCompletion() {
        completionGate?.complete(Unit)
        completionGate = null
    }

    override val supportsPartialResults: Boolean = false

    override fun subject(): SpeechInput =
        object : SpeechInput {
            private val delegate = SystemDialogSpeechInput(bridge, logger)

            override fun listen(request: SpeechRequest) =
                kotlinx.coroutines.flow.flow {
                    requests += request
                    delegate.listen(request).collect { emit(it) }
                }
        }

    override fun offer(vararg events: SpeechEvent) {
        events.forEach { event -> responses.trySend(toOutcome(event)).getOrThrow() }
    }

    override fun recordedRequests(): List<SpeechRequest> = requests

    override fun assertReleased() {
        assertNull(bridge.pending.value)
    }

    @Test
    fun `successful result emits listening then final text`() =
        runTest {
            offer(SpeechEvent.Final("привет"))

            val events = subject().listen(SpeechRequest("ru-RU")).toList()

            assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Final("привет")), events)
        }

    @Test
    fun `cancelled system result becomes cancelled failure`() =
        runTest {
            offer(SpeechEvent.Failed(SpeechFailure.CANCELLED))

            assertEquals(
                listOf(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.CANCELLED)),
                subject().listen(SpeechRequest("ru-RU")).toList(),
            )
        }

    @Test
    fun `launch failure becomes unavailable failure`() =
        runTest {
            offer(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))

            assertEquals(
                listOf(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.UNAVAILABLE)),
                subject().listen(SpeechRequest("ru-RU")).toList(),
            )
        }

    @Test
    fun `concurrent recognition becomes busy failure`() =
        runTest {
            val blocked = CompletableDeferred<Unit>()
            completionGate = blocked
            offer(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
            val first = async { subject().listen(SpeechRequest("ru-RU")).toList() }
            runCurrent()
            assertTrue(bridge.pending.value!!.launched)

            val second = subject().listen(SpeechRequest("ru-RU")).toList()

            assertEquals("system_dialog", logger.events.single().fields["layer"])
            assertEquals("BUSY", logger.events.single().fields["outcome"])
            assertFalse(second.any { it is SpeechEvent.Final })

            blocked.complete(Unit)
            runCurrent()
            first.await()
        }

    @Test
    fun `cancelling collector removes pending request`() =
        runTest {
            completionGate = CompletableDeferred()
            offer(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
            val collection = async { subject().listen(SpeechRequest("ru-RU")).toList() }
            runCurrent()
            assertTrue(bridge.pending.value != null)

            collection.cancel()
            runCurrent()

            assertNull(bridge.pending.value)
            bridge.complete(RecognizerOutcome.LaunchFailed)
        }

    @Test
    fun `final recognition text is never written to logs`() =
        runTest {
            offer(SpeechEvent.Final("привет"))

            subject().listen(SpeechRequest("ru-RU")).toList()

            val log = logger.events.single()
            assertEquals("system_dialog", log.fields["layer"])
            assertEquals("final", log.fields["outcome"])
            assertFalse(log.message.contains("привет"))
            assertFalse(log.fields.values.any { it.contains("привет") })
        }

    private fun toOutcome(event: SpeechEvent): RecognizerOutcome =
        when (event) {
            SpeechEvent.Listening -> {
                error("Listening is emitted by the adapter")
            }

            is SpeechEvent.Partial -> {
                error("The system dialog does not support partial results")
            }

            is SpeechEvent.Level -> {
                error("The system dialog does not support voice levels")
            }

            is SpeechEvent.Final -> {
                RecognizerOutcome.Returned(
                    Activity.RESULT_OK,
                    Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(event.text)),
                )
            }

            is SpeechEvent.Failed -> {
                when (event.reason) {
                    SpeechFailure.NETWORK -> RecognizerOutcome.Returned(RecognizerIntent.RESULT_NETWORK_ERROR, null)

                    SpeechFailure.NO_MATCH -> RecognizerOutcome.Returned(RecognizerIntent.RESULT_NO_MATCH, null)

                    SpeechFailure.CANCELLED -> RecognizerOutcome.Returned(Activity.RESULT_CANCELED, null)

                    SpeechFailure.UNAVAILABLE -> RecognizerOutcome.LaunchFailed

                    SpeechFailure.AUDIO -> RecognizerOutcome.Returned(RecognizerIntent.RESULT_AUDIO_ERROR, null)

                    SpeechFailure.UNKNOWN -> RecognizerOutcome.Returned(RecognizerIntent.RESULT_CLIENT_ERROR, null)

                    SpeechFailure.BUSY,
                    SpeechFailure.NO_SPEECH,
                    SpeechFailure.PERMISSION_DENIED,
                    SpeechFailure.LANGUAGE_UNAVAILABLE,
                    -> error("System dialog cannot represent ${event.reason}")
                }
            }
        }

    @After
    fun stopHost() {
        host.cancel()
        hostScope.cancel()
    }
}
