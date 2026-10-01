package com.example.phoneagent.voice

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecognizerOutcomeMapperTest {
    @Test
    fun `successful result uses first recognition candidate`() {
        val data = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("Поставь будильник", "второй"))

        assertEquals(SpeechEvent.Final("Поставь будильник"), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_OK, data))
    }

    @Test
    fun `successful result trims recognition text`() {
        val data = results("  привет  ")

        assertEquals(SpeechEvent.Final("привет"), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_OK, data))
    }

    @Test
    fun `successful result skips empty candidates`() {
        val data = results("", "второй")

        assertEquals(SpeechEvent.Final("второй"), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_OK, data))
    }

    @Test
    fun `successful result without a nonblank candidate reports no match`() {
        val missingExtra = Intent()
        val emptyResults = results()
        val blankResults = results("  ")

        assertEquals(SpeechEvent.Failed(SpeechFailure.NO_MATCH), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_OK, null))
        assertEquals(SpeechEvent.Failed(SpeechFailure.NO_MATCH), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_OK, missingExtra))
        assertEquals(SpeechEvent.Failed(SpeechFailure.NO_MATCH), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_OK, emptyResults))
        assertEquals(SpeechEvent.Failed(SpeechFailure.NO_MATCH), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_OK, blankResults))
    }

    @Test
    fun `cancelled result remains cancelled even with recognition candidates`() {
        assertEquals(SpeechEvent.Failed(SpeechFailure.CANCELLED), RecognizerOutcomeMapper.map(android.app.Activity.RESULT_CANCELED, null))
        assertEquals(
            SpeechEvent.Failed(SpeechFailure.CANCELLED),
            RecognizerOutcomeMapper.map(android.app.Activity.RESULT_CANCELED, results("результат")),
        )
    }

    @Test
    fun `recognizer error codes map to their speech failures`() {
        val cases =
            listOf(
                RecognizerIntent.RESULT_NO_MATCH to SpeechFailure.NO_MATCH,
                RecognizerIntent.RESULT_NETWORK_ERROR to SpeechFailure.NETWORK,
                RecognizerIntent.RESULT_SERVER_ERROR to SpeechFailure.UNAVAILABLE,
                RecognizerIntent.RESULT_AUDIO_ERROR to SpeechFailure.AUDIO,
                RecognizerIntent.RESULT_CLIENT_ERROR to SpeechFailure.UNKNOWN,
                999 to SpeechFailure.UNKNOWN,
            )

        cases.forEach { (resultCode, failure) ->
            assertEquals(SpeechEvent.Failed(failure), RecognizerOutcomeMapper.map(resultCode, null))
        }
    }

    private fun results(vararg candidates: String) =
        Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, ArrayList(candidates.toList()))
}
