package com.example.phoneagent.voice

import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.info
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

public class PermissionGatedSpeechInput(
    private val delegate: SpeechInput,
    private val interaction: UserInteraction,
) : SpeechInput {
    override fun listen(request: SpeechRequest): Flow<SpeechEvent> =
        flow {
            when (interaction.ensure(Capability.MICROPHONE)) {
                CapabilityResult.Available -> emitAll(delegate.listen(request))
                CapabilityResult.Denied -> emit(SpeechEvent.Failed(SpeechFailure.PERMISSION_DENIED))
            }
        }
}

public class FallbackSpeechInput(
    private val primary: SpeechInput,
    private val fallback: SpeechInput,
    private val logger: AppLogger,
) : SpeechInput {
    override fun listen(request: SpeechRequest): Flow<SpeechEvent> =
        flow {
            var hasProgress = false
            var hasListening = false
            var fallbackReason: SpeechFailure? = null

            primary.listen(request).collect { event ->
                if (fallbackReason != null) return@collect
                when (event) {
                    SpeechEvent.Listening -> {
                        if (!hasListening) {
                            hasListening = true
                            emit(event)
                        }
                    }

                    is SpeechEvent.Partial, is SpeechEvent.Level, is SpeechEvent.Final -> {
                        hasProgress = true
                        emit(event)
                    }

                    is SpeechEvent.Failed -> {
                        if (!hasProgress && event.reason in FALLBACK_FAILURES) {
                            fallbackReason = event.reason
                        } else {
                            emit(event)
                        }
                    }
                }
            }

            if (fallbackReason != null) {
                logger.info(
                    TAG,
                    LOG_MESSAGE,
                    mapOf("layer" to LAYER, "outcome" to SWITCH_OUTCOME),
                )
                fallback.listen(request).collect { event ->
                    if (event == SpeechEvent.Listening) {
                        if (!hasListening) {
                            hasListening = true
                            emit(event)
                        }
                    } else {
                        emit(event)
                    }
                }
            }
        }

    private companion object {
        val FALLBACK_FAILURES =
            setOf(SpeechFailure.UNAVAILABLE, SpeechFailure.LANGUAGE_UNAVAILABLE, SpeechFailure.PERMISSION_DENIED)
        const val TAG = "FallbackSpeechInput"
        const val LOG_MESSAGE = "Speech input switched to system dialog"
        const val LAYER = "fallback"
        const val SWITCH_OUTCOME = "switched_to_system_dialog"
    }
}
