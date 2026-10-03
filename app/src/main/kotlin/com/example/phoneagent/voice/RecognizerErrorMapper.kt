package com.example.phoneagent.voice

import android.speech.SpeechRecognizer
import com.example.phoneagent.domain.speech.SpeechFailure

public object RecognizerErrorMapper {
    public fun map(error: Int): SpeechFailure =
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> SpeechFailure.NO_MATCH

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NO_SPEECH

            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechFailure.NETWORK

            SpeechRecognizer.ERROR_SERVER -> SpeechFailure.UNAVAILABLE

            SpeechRecognizer.ERROR_AUDIO -> SpeechFailure.AUDIO

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.BUSY

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.PERMISSION_DENIED

            SpeechRecognizer.ERROR_CLIENT -> SpeechFailure.UNKNOWN

            // AOSP android-36 SpeechRecognizer.java:259-274; numeric values avoid resolving
            // platform fields newer than minSdk 26.
            ERROR_TOO_MANY_REQUESTS -> SpeechFailure.BUSY

            ERROR_SERVER_DISCONNECTED -> SpeechFailure.UNAVAILABLE

            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechFailure.LANGUAGE_UNAVAILABLE

            ERROR_CANNOT_CHECK_SUPPORT, ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> SpeechFailure.UNKNOWN

            else -> SpeechFailure.UNKNOWN
        }

    private const val ERROR_TOO_MANY_REQUESTS = 10
    private const val ERROR_SERVER_DISCONNECTED = 11
    private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    private const val ERROR_CANNOT_CHECK_SUPPORT = 14
    private const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15
}
