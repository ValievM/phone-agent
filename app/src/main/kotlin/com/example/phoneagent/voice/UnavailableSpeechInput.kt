package com.example.phoneagent.voice

import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Temporary placeholder, replaced by the real adapter in V2a (#69). */
public class UnavailableSpeechInput
    @Inject
    constructor() : SpeechInput {
        override fun listen(request: SpeechRequest): Flow<SpeechEvent> = flowOf(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
    }
