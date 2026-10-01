package com.example.phoneagent.voice

import android.content.Intent
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

public data class PendingSpeech(
    public val id: Long,
    public val request: SpeechRequest,
    public val intent: Intent,
    public val launched: Boolean,
)

public sealed interface RecognizerOutcome {
    public data class Returned(
        public val resultCode: Int,
        public val data: Intent?,
    ) : RecognizerOutcome

    public data object LaunchFailed : RecognizerOutcome

    public data object Busy : RecognizerOutcome
}

/**
 * Mediates a single speech request between an adapter and the UI host that launches its system intent.
 *
 * [request] immediately publishes one pending request and suspends for its outcome; while it is pending,
 * later requests return [RecognizerOutcome.Busy] without replacing it. Cancellation removes that request.
 * [markLaunched] and [complete] affect only the current request, and completion is accepted only after it
 * has been marked launched; stale IDs and completions for absent or unlaunched requests are ignored.
 *
 * A late result for a cancelled request arriving after another request launches is indistinguishable from
 * the new request's result and will be attributed to it. This cannot occur through the UI because the
 * system dialog remains in front until its result is delivered.
 */
@Singleton
public class SpeechRequestBridge
    @Inject
    constructor() {
        private val lock = Any()
        private val mutablePending = MutableStateFlow<PendingSpeech?>(null)
        private var nextId = 1L
        private var currentCompletion: CompletableDeferred<RecognizerOutcome>? = null

        public val pending: StateFlow<PendingSpeech?> = mutablePending.asStateFlow()

        public suspend fun request(request: SpeechRequest): RecognizerOutcome {
            val entry: PendingSpeech
            val completion: CompletableDeferred<RecognizerOutcome>
            synchronized(lock) {
                if (mutablePending.value != null) return RecognizerOutcome.Busy
                entry = PendingSpeech(nextId++, request, RecognizerIntentFactory.create(request), launched = false)
                completion = CompletableDeferred()
                currentCompletion = completion
                mutablePending.value = entry
            }

            try {
                return completion.await()
            } finally {
                synchronized(lock) {
                    if (mutablePending.value?.id == entry.id) {
                        mutablePending.value = null
                        currentCompletion = null
                    }
                }
            }
        }

        public fun markLaunched(id: Long) {
            synchronized(lock) {
                val current = mutablePending.value ?: return
                if (current.id == id && !current.launched) mutablePending.value = current.copy(launched = true)
            }
        }

        public fun complete(outcome: RecognizerOutcome) {
            synchronized(lock) {
                val current = mutablePending.value ?: return
                if (current.launched) currentCompletion?.complete(outcome)
            }
        }
    }
