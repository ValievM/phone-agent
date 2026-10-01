package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Scriptable speech-input fake that enforces the port's one-session-at-a-time contract. */
public class FakeSpeechInput : SpeechInput {
    private val scenarios: ArrayDeque<List<SpeechEvent>> = ArrayDeque()
    private var active: Boolean = false
    private var started: Int = 0

    public val requests: MutableList<SpeechRequest> = mutableListOf()
    public var released: Int = 0
        private set

    public fun script(vararg events: SpeechEvent) {
        synchronized(this) {
            scenarios.addLast(events.toList())
        }
    }

    override fun listen(request: SpeechRequest): Flow<SpeechEvent> = flow {
        val acquired = synchronized(this@FakeSpeechInput) {
            requests += request
            if (active) {
                false
            } else {
                active = true
                started += 1
                true
            }
        }
        if (!acquired) {
            emit(SpeechEvent.Failed(SpeechFailure.BUSY))
            return@flow
        }

        try {
            val events = synchronized(this@FakeSpeechInput) {
                scenarios.removeFirstOrNull()
            } ?: listOf(SpeechEvent.Failed(SpeechFailure.UNKNOWN))
            emit(SpeechEvent.Listening)
            for (event in events) {
                val normalized = if (event is SpeechEvent.Final && event.text.isBlank()) {
                    SpeechEvent.Failed(SpeechFailure.NO_MATCH)
                } else {
                    event
                }
                emit(normalized)
                if (normalized is SpeechEvent.Final || normalized is SpeechEvent.Failed) break
            }
        } finally {
            synchronized(this@FakeSpeechInput) {
                active = false
                released += 1
            }
        }
    }

    public fun assertReleased() {
        check(!active && released == started) {
            "Speech sessions still hold resources: started=$started, released=$released"
        }
    }
}
