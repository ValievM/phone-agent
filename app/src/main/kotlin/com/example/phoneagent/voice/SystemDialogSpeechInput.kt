package com.example.phoneagent.voice

import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.info
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

public class SystemDialogSpeechInput
    @Inject
    constructor(
        private val bridge: SpeechRequestBridge,
        private val logger: AppLogger,
    ) : SpeechInput {
        override fun listen(request: SpeechRequest): Flow<SpeechEvent> =
            flow {
                coroutineScope {
                    val outcome = async(start = CoroutineStart.UNDISPATCHED) { bridge.request(request) }
                    val immediateOutcome = if (outcome.isCompleted) outcome.await() else null
                    if (immediateOutcome != RecognizerOutcome.Busy) emit(SpeechEvent.Listening)

                    val terminal =
                        when (val result = immediateOutcome ?: outcome.await()) {
                            is RecognizerOutcome.Returned -> {
                                RecognizerOutcomeMapper.map(result.resultCode, result.data)
                            }

                            RecognizerOutcome.LaunchFailed -> {
                                SpeechEvent.Failed(SpeechFailure.UNAVAILABLE)
                            }

                            RecognizerOutcome.Busy -> {
                                SpeechEvent.Failed(SpeechFailure.BUSY)
                            }
                        }
                    val logOutcome =
                        if (terminal is SpeechEvent.Final) "final" else (terminal as SpeechEvent.Failed).reason.name
                    logger.info(
                        TAG,
                        LOG_MESSAGE,
                        mapOf("layer" to LAYER, "outcome" to logOutcome),
                    )
                    emit(terminal)
                }
            }

        private companion object {
            const val TAG = "SystemDialogSpeechInput"
            const val LOG_MESSAGE = "System speech recognition completed"
            const val LAYER = "system_dialog"
        }
    }
