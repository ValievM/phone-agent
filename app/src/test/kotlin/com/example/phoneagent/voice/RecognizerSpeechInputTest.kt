@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.phoneagent.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.contracts.SpeechInputContract
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecognizerSpeechInputTest {
    @Test
    fun `recognizer callbacks emit listening partials and final in order`() =
        runTest {
            val harness = Harness(testScheduler)
            val events = mutableListOf<SpeechEvent>()
            val collection = harness.collect(backgroundScope, events)
            runCurrent()
            val listener = harness.engine.listener

            listener.onReadyForSpeech(Bundle())
            listener.onPartialResults(results("  "))
            listener.onPartialResults(results(" при "))
            listener.onPartialResults(results("привет"))
            listener.onResults(results(" привет "))
            listener.onError(7)
            runCurrent()

            assertEquals(
                listOf(SpeechEvent.Listening, SpeechEvent.Partial("при"), SpeechEvent.Partial("привет"), SpeechEvent.Final("привет")),
                events,
            )
            assertEquals(1, harness.logger.events.size)
            assertEquals(1, harness.engine.cancelCalls)
            assertEquals(1, harness.engine.destroyCalls)
            collection.join()
        }

    @Test
    fun `recognizer error is emitted as one terminal failure`() =
        runTest {
            val harness = Harness(testScheduler)
            val events = mutableListOf<SpeechEvent>()
            val collection = harness.collect(backgroundScope, events)
            runCurrent()

            harness.engine.listener.onReadyForSpeech(Bundle())
            harness.engine.listener.onError(7)
            harness.engine.listener.onError(2)
            runCurrent()

            assertEquals(listOf(SpeechEvent.Listening, SpeechEvent.Failed(SpeechFailure.NO_MATCH)), events)
            collection.join()
        }

    @Test
    fun `blank final results become no match`() =
        runTest {
            val harness = Harness(testScheduler)
            val events = mutableListOf<SpeechEvent>()
            val collection = harness.collect(backgroundScope, events)
            runCurrent()

            harness.engine.listener.onResults(results("", "  "))
            runCurrent()

            assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.NO_MATCH)), events)
            collection.join()
        }

    @Test
    fun `unavailable recognizer emits only unavailable failure`() =
        runTest {
            val harness = Harness(testScheduler, engine = null)
            val events = mutableListOf<SpeechEvent>()
            val collection = async { harness.input.listen(SpeechRequest("ru-RU")).toList(events) }
            runCurrent()

            assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE)), events)
            collection.await()
        }

    @Test
    fun `cancelling collection releases engine once on main dispatcher`() =
        runTest {
            val harness = Harness(testScheduler)
            val collection = harness.collect(backgroundScope, mutableListOf())
            runCurrent()
            val startedOn = harness.engine.startedOn

            collection.cancelAndJoin()

            assertEquals(1, harness.engine.cancelCalls)
            assertEquals(1, harness.engine.destroyCalls)
            assertEquals(startedOn, harness.engine.cancelledOn)
            assertEquals(startedOn, harness.engine.destroyedOn)
        }

    @Test
    fun `offline preference reaches engine factory and intent options are preserved`() =
        runTest {
            val harness = Harness(testScheduler)
            val events = mutableListOf<SpeechEvent>()
            val collection = harness.collect(backgroundScope, events, SpeechRequest("ru-RU", offlineOnly = true))
            runCurrent()

            assertEquals(true, harness.offlineOnly)
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, harness.engine.intent.action)
            assertEquals(
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                harness.engine.intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL),
            )
            assertEquals("ru-RU", harness.engine.intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
            assertEquals(
                true,
                harness.engine.intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false),
            )
            assertEquals(
                true,
                harness.engine.intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false),
            )
            assertEquals(3, harness.engine.intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0))

            harness.engine.listener.onResults(results("готово"))
            runCurrent()
            collection.join()
        }

    @Test
    fun `voice levels are throttled and occur after listening before terminal event`() =
        runTest {
            val harness = Harness(testScheduler)
            val events = mutableListOf<SpeechEvent>()
            val collection = harness.collect(backgroundScope, events)
            runCurrent()
            val listener = harness.engine.listener

            listener.onReadyForSpeech(Bundle())
            listener.onRmsChanged(4f)
            listener.onRmsChanged(4.2f)
            listener.onRmsChanged(5f)
            listener.onRmsChanged(-2f)
            listener.onResults(results("готово"))
            runCurrent()

            assertEquals(
                listOf(
                    SpeechEvent.Listening,
                    SpeechEvent.Level(0.5f),
                    SpeechEvent.Level(0.5833333f),
                    SpeechEvent.Level(0f),
                    SpeechEvent.Final("готово"),
                ),
                events,
            )
            collection.join()
        }

    @Test
    fun `recognition text is never logged`() =
        runTest {
            val harness = Harness(testScheduler)
            val events = mutableListOf<SpeechEvent>()
            val collection = harness.collect(backgroundScope, events)
            runCurrent()

            harness.engine.listener.onResults(results("привет"))
            runCurrent()
            collection.join()

            assertTrue(harness.logger.events.isNotEmpty())
            assertFalse(
                harness.logger.events
                    .toString()
                    .contains("привет"),
            )
            assertEquals(
                mapOf("layer" to "in_app", "outcome" to "final"),
                harness.logger.events
                    .single()
                    .fields,
            )
        }

    private fun results(vararg candidates: String): Bundle =
        Bundle().apply { putStringArrayList(RecognizerIntent.EXTRA_RESULTS, ArrayList(candidates.toList())) }

    private class Harness(
        scheduler: TestCoroutineScheduler,
        engine: FakeEngine? = FakeEngine(),
    ) {
        val logger = FakeAppLogger()
        val engine = engine ?: FakeEngine()
        var offlineOnly: Boolean? = null
        private val engineAvailable = engine != null
        val input =
            RecognizerSpeechInput(
                factory =
                    RecognizerEngineFactory { offline ->
                        offlineOnly = offline
                        this.engine.takeIf { engineAvailable }
                    },
                dispatchers = FakeDispatcherProvider(StandardTestDispatcher(scheduler)),
                logger = logger,
            )

        fun collect(
            scope: CoroutineScope,
            events: MutableList<SpeechEvent>,
            request: SpeechRequest = SpeechRequest("ru-RU"),
        ): Job = scope.launch { input.listen(request).toList(events) }
    }

    private class FakeEngine : RecognizerEngine {
        lateinit var listener: RecognitionListener
        lateinit var intent: Intent
        var cancelCalls = 0
        var destroyCalls = 0
        var startedOn: Thread? = null
        var cancelledOn: Thread? = null
        var destroyedOn: Thread? = null

        override fun start(
            intent: Intent,
            listener: RecognitionListener,
        ) {
            this.intent = intent
            this.listener = listener
            startedOn = Thread.currentThread()
        }

        override fun cancel() {
            cancelCalls++
            cancelledOn = Thread.currentThread()
        }

        override fun destroy() {
            destroyCalls++
            destroyedOn = Thread.currentThread()
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecognizerSpeechInputContractTest : SpeechInputContract() {
    private val harness = ContractHarness()

    override val supportsPartialResults: Boolean = true

    override fun subject(): SpeechInput = harness.input

    override fun offer(vararg events: SpeechEvent) {
        harness.offered.addLast(events.toList())
    }

    override fun recordedRequests(): List<SpeechRequest> = harness.requests

    override fun assertReleased() {
        assertTrue(harness.engines.isNotEmpty() && harness.engines.all { it.destroyCalls == 1 })
    }

    override fun holdCompletion() {
        harness.hold = true
    }

    override fun releaseCompletion() {
        harness.hold = false
        harness.engines.forEach { it.release() }
    }

    private class ContractHarness {
        val offered = ArrayDeque<List<SpeechEvent>>()
        val requests = mutableListOf<SpeechRequest>()
        val engines = mutableListOf<ContractEngine>()
        var hold = false
        val input =
            RecognizerSpeechInput(
                factory =
                    RecognizerEngineFactory {
                        val engine = ContractEngine(this)
                        engines += engine
                        engine
                    },
                dispatchers = FakeDispatcherProvider(kotlinx.coroutines.Dispatchers.Unconfined),
                logger = FakeAppLogger(),
            )
    }

    private class ContractEngine(
        private val harness: ContractHarness,
    ) : RecognizerEngine {
        private lateinit var listener: RecognitionListener
        private var pendingTerminal: SpeechEvent? = null
        var destroyCalls = 0

        override fun start(
            intent: Intent,
            listener: RecognitionListener,
        ) {
            this.listener = listener
            harness.requests +=
                SpeechRequest(
                    intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE).orEmpty(),
                    intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false),
                )
            listener.onReadyForSpeech(Bundle())
            val events = harness.offered.removeFirstOrNull().orEmpty()
            for (event in events) {
                if (harness.hold && (event is SpeechEvent.Final || event is SpeechEvent.Failed)) {
                    pendingTerminal = event
                    break
                }
                deliver(event)
            }
        }

        override fun cancel() = Unit

        override fun destroy() {
            destroyCalls++
        }

        fun release() {
            pendingTerminal?.let(::deliver)
            pendingTerminal = null
        }

        private fun deliver(event: SpeechEvent) {
            when (event) {
                SpeechEvent.Listening -> listener.onReadyForSpeech(Bundle())
                is SpeechEvent.Level -> listener.onRmsChanged(event.normalized * 12f - 2f)
                is SpeechEvent.Partial -> listener.onPartialResults(result(event.text))
                is SpeechEvent.Final -> listener.onResults(result(event.text))
                is SpeechEvent.Failed -> listener.onError(errorCode(event.reason))
            }
        }

        private fun result(text: String) = Bundle().apply { putStringArrayList(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text)) }

        private fun errorCode(failure: SpeechFailure): Int =
            when (failure) {
                SpeechFailure.NO_MATCH -> 7
                SpeechFailure.NO_SPEECH -> 6
                SpeechFailure.NETWORK -> 2
                SpeechFailure.AUDIO -> 3
                SpeechFailure.BUSY -> 8
                SpeechFailure.PERMISSION_DENIED -> 9
                SpeechFailure.LANGUAGE_UNAVAILABLE -> 12
                SpeechFailure.UNAVAILABLE -> 4
                SpeechFailure.UNKNOWN -> 5
                SpeechFailure.CANCELLED -> 5
            }
    }
}
