package com.example.phoneagent.voice

import android.speech.RecognizerIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.speech.SpeechRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecognizerIntentFactoryTest {
    @Test
    fun `intent uses free form language model and requested language`() {
        val intent = RecognizerIntentFactory.create(SpeechRequest("ru-RU"))

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertEquals("ru-RU", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
    }

    @Test
    fun `intent carries offline preference`() {
        val onlineIntent = RecognizerIntentFactory.create(SpeechRequest("ru-RU", offlineOnly = false))
        val offlineIntent = RecognizerIntentFactory.create(SpeechRequest("ru-RU", offlineOnly = true))

        assertFalse(onlineIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        assertTrue(offlineIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test
    fun `intent requests three results without a prompt`() {
        val intent = RecognizerIntentFactory.create(SpeechRequest("ru-RU"))

        assertEquals(3, intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0))
        assertNull(intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT))
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_PROMPT))
    }
}
