@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.phoneagent.voice

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SpeechRequestBridgeTest {
    @Test
    fun `request publishes pending with its intent before launch`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val request = SpeechRequest("ru-RU", offlineOnly = true)
            val result = async { bridge.request(request) }
            runCurrent()

            val pending = bridge.pending.value
            assertNotNull(pending)
            assertEquals(1L, pending?.id)
            assertEquals(request, pending?.request)
            assertNotNull(pending?.intent)
            assertFalse(pending!!.launched)
            assertFalse(result.isCompleted)
            result.cancelAndJoin()
        }

    @Test
    fun `mark launched updates only the matching pending request`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val result = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()
            val id = bridge.pending.value!!.id

            bridge.markLaunched(id + 1)
            assertFalse(bridge.pending.value!!.launched)
            bridge.markLaunched(id)

            assertTrue(bridge.pending.value!!.launched)
            result.cancelAndJoin()
        }

    @Test
    fun `launched request completes with returned activity outcome`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val result = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()
            bridge.markLaunched(bridge.pending.value!!.id)
            val outcome = RecognizerOutcome.Returned(-1, Intent())

            bridge.complete(outcome)

            assertEquals(outcome, result.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `completion before launch is ignored`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val result = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()

            bridge.complete(RecognizerOutcome.LaunchFailed)
            runCurrent()

            assertFalse(result.isCompleted)
            assertNotNull(bridge.pending.value)
            result.cancelAndJoin()
        }

    @Test
    fun `busy request does not disturb active request`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val first = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()
            val firstPending = bridge.pending.value!!

            assertEquals(RecognizerOutcome.Busy, bridge.request(SpeechRequest("en-US")))
            assertEquals(firstPending, bridge.pending.value)

            bridge.markLaunched(firstPending.id)
            val outcome = RecognizerOutcome.Returned(-1, Intent())
            bridge.complete(outcome)
            assertEquals(outcome, first.await())
        }

    @Test
    fun `cancelling request clears pending and late completion is ignored`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val result = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()
            bridge.markLaunched(bridge.pending.value!!.id)

            result.cancelAndJoin()
            assertNull(bridge.pending.value)
            bridge.complete(RecognizerOutcome.LaunchFailed)
            assertNull(bridge.pending.value)
        }

    @Test
    fun `launched request propagates launch failure`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val result = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()
            bridge.markLaunched(bridge.pending.value!!.id)

            bridge.complete(RecognizerOutcome.LaunchFailed)

            assertEquals(RecognizerOutcome.LaunchFailed, result.await())
        }

    @Test
    fun `request ids increase after completion`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val first = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()
            val firstId = bridge.pending.value!!.id
            bridge.markLaunched(firstId)
            bridge.complete(RecognizerOutcome.LaunchFailed)
            first.await()

            val second = async { bridge.request(SpeechRequest("ru-RU")) }
            runCurrent()
            val secondId = bridge.pending.value!!.id

            assertTrue(secondId > firstId)
            second.cancelAndJoin()
        }
}
