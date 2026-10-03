package com.example.phoneagent.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.info
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

public class RecognizerSpeechInput
    @Inject
    constructor(
        private val factory: RecognizerEngineFactory,
        private val dispatchers: DispatcherProvider,
        private val logger: AppLogger,
    ) : SpeechInput {
        private val activeSession = AtomicBoolean(false)

        override fun listen(request: SpeechRequest): Flow<SpeechEvent> =
            callbackFlow {
                var ownsSession = false
                var engine: RecognizerEngine? = null
                val terminal = AtomicBoolean(false)
                try {
                    withContext(dispatchers.main) {
                        if (!activeSession.compareAndSet(false, true)) {
                            trySend(SpeechEvent.Failed(SpeechFailure.BUSY))
                            close()
                            return@withContext
                        }
                        ownsSession = true

                        engine = factory.create(request.offlineOnly)
                        if (engine == null) {
                            complete(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE), terminal)
                            return@withContext
                        }

                        var listening = false
                        val levelThrottle = LevelThrottle()
                        val listener =
                            object : RecognitionListener {
                                override fun onReadyForSpeech(params: Bundle?) {
                                    if (!terminal.get() && !listening) {
                                        listening = true
                                        trySend(SpeechEvent.Listening)
                                    }
                                }

                                override fun onBeginningOfSpeech() = Unit

                                override fun onRmsChanged(rmsdB: Float) {
                                    if (listening && !terminal.get()) {
                                        val level = RecognizerLevel.normalizeRms(rmsdB)
                                        if (levelThrottle.shouldEmit(level)) trySend(SpeechEvent.Level(level))
                                    }
                                }

                                override fun onBufferReceived(buffer: ByteArray?) = Unit

                                override fun onEndOfSpeech() = Unit

                                override fun onError(error: Int) {
                                    complete(SpeechEvent.Failed(RecognizerErrorMapper.map(error)), terminal)
                                }

                                override fun onResults(results: Bundle?) {
                                    val text = results.firstNonblankResult()
                                    val event = text?.let(SpeechEvent::Final) ?: SpeechEvent.Failed(SpeechFailure.NO_MATCH)
                                    complete(event, terminal)
                                }

                                override fun onPartialResults(partialResults: Bundle?) {
                                    if (listening && !terminal.get()) {
                                        partialResults.firstNonblankResult()?.let { trySend(SpeechEvent.Partial(it)) }
                                    }
                                }

                                override fun onEvent(
                                    eventType: Int,
                                    params: Bundle?,
                                ) = Unit
                            }
                        engine?.start(createIntent(request), listener)
                    }
                    awaitClose { }
                } finally {
                    if (ownsSession) {
                        withContext(NonCancellable + dispatchers.main) {
                            try {
                                engine?.cancel()
                            } finally {
                                try {
                                    engine?.destroy()
                                } finally {
                                    activeSession.set(false)
                                }
                            }
                        }
                    }
                }
            }

        private fun ProducerScope<SpeechEvent>.complete(
            event: SpeechEvent,
            terminal: AtomicBoolean,
        ) {
            if (terminal.compareAndSet(false, true)) {
                val outcome = if (event is SpeechEvent.Final) "final" else (event as SpeechEvent.Failed).reason.name
                logger.info(TAG, LOG_MESSAGE, mapOf("layer" to LAYER, "outcome" to outcome))
                trySend(event)
                close()
            }
        }

        private fun createIntent(request: SpeechRequest): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, request.offlineOnly)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            }

        private fun Bundle?.firstNonblankResult(): String? =
            this
                ?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)
                ?.firstNotNullOfOrNull { candidate -> candidate.trim().takeIf(String::isNotEmpty) }

        private companion object {
            const val TAG = "RecognizerSpeechInput"
            const val LOG_MESSAGE = "In-app speech recognition completed"
            const val LAYER = "in_app"
            const val MAX_RESULTS = 3
        }
    }
