package com.example.phoneagent.voice

import android.speech.SpeechRecognizer
import com.example.phoneagent.domain.speech.SpeechFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizerErrorMapperTest {
    @Test
    fun `recognizer errors map to their speech failures`() {
        val cases =
            listOf(
                SpeechRecognizer.ERROR_NO_MATCH to SpeechFailure.NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT to SpeechFailure.NO_SPEECH,
                SpeechRecognizer.ERROR_NETWORK to SpeechFailure.NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT to SpeechFailure.NETWORK,
                SpeechRecognizer.ERROR_SERVER to SpeechFailure.UNAVAILABLE,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED to SpeechFailure.UNAVAILABLE,
                SpeechRecognizer.ERROR_AUDIO to SpeechFailure.AUDIO,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY to SpeechFailure.BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS to SpeechFailure.BUSY,
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to SpeechFailure.PERMISSION_DENIED,
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED to SpeechFailure.LANGUAGE_UNAVAILABLE,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE to SpeechFailure.LANGUAGE_UNAVAILABLE,
                SpeechRecognizer.ERROR_CLIENT to SpeechFailure.UNKNOWN,
                SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT to SpeechFailure.UNKNOWN,
                SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS to SpeechFailure.UNKNOWN,
                Int.MIN_VALUE to SpeechFailure.UNKNOWN,
            )

        cases.forEach { (code, failure) -> assertEquals("error=$code", failure, RecognizerErrorMapper.map(code)) }
    }

    @Test
    fun `new error constants are handled as numeric values on min sdk`() {
        assertEquals(SpeechFailure.BUSY, RecognizerErrorMapper.map(10))
        assertEquals(SpeechFailure.UNAVAILABLE, RecognizerErrorMapper.map(11))
        assertEquals(SpeechFailure.LANGUAGE_UNAVAILABLE, RecognizerErrorMapper.map(12))
        assertEquals(SpeechFailure.LANGUAGE_UNAVAILABLE, RecognizerErrorMapper.map(13))
        assertEquals(SpeechFailure.UNKNOWN, RecognizerErrorMapper.map(14))
        assertEquals(SpeechFailure.UNKNOWN, RecognizerErrorMapper.map(15))
    }
}
