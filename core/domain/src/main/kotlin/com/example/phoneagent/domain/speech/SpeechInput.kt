package com.example.phoneagent.domain.speech

import kotlinx.coroutines.flow.Flow

public interface SpeechInput {
    /**
     * One recognition session. Cold: collecting starts it, cancelling the collector stops it and releases resources.
     * Emits [SpeechEvent.Listening] first, then any [SpeechEvent.Partial] and [SpeechEvent.Level], then exactly one terminal event
     * ([SpeechEvent.Final] or [SpeechEvent.Failed]) and completes. Never throws for expected failures.
     * [SpeechEvent.Level.normalized] is normalized to the range 0..1.
     * [SpeechEvent.Final.text] is never blank.
     * A session rejected because another one is active ends with [SpeechFailure.BUSY] and may omit [SpeechEvent.Listening].
     */
    public fun listen(request: SpeechRequest): Flow<SpeechEvent>
}

/**
 * [offlineOnly] = true asks the system recognizer to use ONLY an offline engine ([RecognizerIntent.EXTRA_PREFER_OFFLINE]);
 * without an offline language pack that can fail even with a working network, so the default is false.
 */
public data class SpeechRequest(
    public val languageTag: String,
    public val offlineOnly: Boolean = false,
)

public sealed interface SpeechEvent {
    public data object Listening : SpeechEvent

    public data class Partial(
        public val text: String,
    ) : SpeechEvent

    /** The final recognized text; never blank. */
    public data class Final(
        public val text: String,
    ) : SpeechEvent

    /** Normalized voice level, in the inclusive range 0..1. */
    public data class Level(
        public val normalized: Float,
    ) : SpeechEvent

    public data class Failed(
        public val reason: SpeechFailure,
    ) : SpeechEvent
}

public enum class SpeechFailure {
    CANCELLED,
    NO_MATCH,
    NO_SPEECH,
    NETWORK,
    AUDIO,
    BUSY,
    PERMISSION_DENIED,
    LANGUAGE_UNAVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}
