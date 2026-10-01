package com.example.phoneagent.voice

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure

public object RecognizerOutcomeMapper {
    public fun map(resultCode: Int, data: Intent?): SpeechEvent =
        when (resultCode) {
            Activity.RESULT_OK ->
                data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstNotNullOfOrNull { it.trim().takeIf(String::isNotEmpty) }
                    ?.let(SpeechEvent::Final)
                    ?: SpeechEvent.Failed(SpeechFailure.NO_MATCH)
            Activity.RESULT_CANCELED -> SpeechEvent.Failed(SpeechFailure.CANCELLED)
            RecognizerIntent.RESULT_NO_MATCH -> SpeechEvent.Failed(SpeechFailure.NO_MATCH)
            RecognizerIntent.RESULT_NETWORK_ERROR -> SpeechEvent.Failed(SpeechFailure.NETWORK)
            RecognizerIntent.RESULT_SERVER_ERROR -> SpeechEvent.Failed(SpeechFailure.UNAVAILABLE)
            RecognizerIntent.RESULT_AUDIO_ERROR -> SpeechEvent.Failed(SpeechFailure.AUDIO)
            else -> SpeechEvent.Failed(SpeechFailure.UNKNOWN)
        }
}
