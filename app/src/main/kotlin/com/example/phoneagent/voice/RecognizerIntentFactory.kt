package com.example.phoneagent.voice

import android.content.Intent
import android.speech.RecognizerIntent
import com.example.phoneagent.domain.speech.SpeechRequest

public object RecognizerIntentFactory {
    public fun create(request: SpeechRequest): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, request.offlineOnly)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
        }

    private const val MAX_RESULTS = 3
}
