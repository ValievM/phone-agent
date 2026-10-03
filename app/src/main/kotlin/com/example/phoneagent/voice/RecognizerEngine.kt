package com.example.phoneagent.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

public interface RecognizerEngine {
    public fun start(
        intent: Intent,
        listener: RecognitionListener,
    )

    public fun cancel()

    public fun destroy()
}

public fun interface RecognizerEngineFactory {
    public fun create(offlineOnly: Boolean): RecognizerEngine?
}

public class AndroidRecognizerEngineFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RecognizerEngineFactory {
        @MainThread
        override fun create(offlineOnly: Boolean): RecognizerEngine? {
            val recognizer =
                if (offlineOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    SpeechRecognizer.createSpeechRecognizer(context)
                } else {
                    return null
                }
            return AndroidRecognizerEngine(recognizer)
        }
    }

private class AndroidRecognizerEngine(
    private val recognizer: SpeechRecognizer,
) : RecognizerEngine {
    @MainThread
    override fun start(
        intent: Intent,
        listener: RecognitionListener,
    ) {
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
    }

    @MainThread
    override fun cancel() {
        recognizer.cancel()
    }

    @MainThread
    override fun destroy() {
        recognizer.destroy()
    }
}
