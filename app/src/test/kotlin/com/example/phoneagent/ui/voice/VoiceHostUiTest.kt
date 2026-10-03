package com.example.phoneagent.ui.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.speech.SpeechRequest
import com.example.phoneagent.testing.FakeActivityResultRegistry
import com.example.phoneagent.testing.ProvideFakeActivityResultRegistry
import com.example.phoneagent.voice.RecognizerOutcome
import com.example.phoneagent.voice.SpeechRequestBridge
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class VoiceHostUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `pending request launches its exact intent once and marks it launched`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val registry = FakeActivityResultRegistry()
            val request = async(start = CoroutineStart.UNDISPATCHED) { bridge.request(SpeechRequest("ru-RU")) }
            val resultIntent = Intent("example.RESULT")

            composeRule.setContent {
                ProvideFakeActivityResultRegistry(registry) { VoiceHost(bridge) }
            }
            composeRule.waitForIdle()

            assertEquals(1, registry.launches.size)
            assertSame(bridge.pending.value!!.intent, registry.launches.single().input)
            assertEquals(true, bridge.pending.value!!.launched)

            registry.respondWith(Activity.RESULT_OK, resultIntent)
            composeRule.waitForIdle()
            val outcome = request.await() as RecognizerOutcome.Returned
            assertEquals(Activity.RESULT_OK, outcome.resultCode)
            assertSame(resultIntent, outcome.data)
            assertNull(bridge.pending.value)
        }

    @Test
    fun `activity not found completes request as launch failed without crashing`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val registry =
                FakeActivityResultRegistry().apply {
                    launchFailure = ActivityNotFoundException("Recognizer unavailable")
                }
            val request = async(start = CoroutineStart.UNDISPATCHED) { bridge.request(SpeechRequest("ru-RU")) }

            composeRule.setContent {
                ProvideFakeActivityResultRegistry(registry) { VoiceHost(bridge) }
            }
            composeRule.waitForIdle()

            assertEquals(RecognizerOutcome.LaunchFailed, request.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `recomposition does not launch again and result is delivered once`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val registry = FakeActivityResultRegistry()
            val revision = mutableIntStateOf(0)
            val request = async(start = CoroutineStart.UNDISPATCHED) { bridge.request(SpeechRequest("ru-RU")) }

            composeRule.setContent {
                ProvideFakeActivityResultRegistry(registry) {
                    val currentRevision by revision
                    if (currentRevision >= 0) VoiceHost(bridge)
                }
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { revision.intValue++ }
            composeRule.waitForIdle()

            assertEquals(1, registry.launches.size)
            registry.respondWith(Activity.RESULT_OK, Intent("example.RESULT"))
            composeRule.waitForIdle()
            assertEquals(Activity.RESULT_OK, (request.await() as RecognizerOutcome.Returned).resultCode)
            assertNull(bridge.pending.value)
        }

    @Test
    fun `host removal preserves pending request and does not launch again when restored`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val registry = FakeActivityResultRegistry()
            val visible = mutableStateOf(true)
            val request = async(start = CoroutineStart.UNDISPATCHED) { bridge.request(SpeechRequest("ru-RU")) }

            composeRule.setContent {
                ProvideFakeActivityResultRegistry(registry) {
                    if (visible.value) VoiceHost(bridge)
                }
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { visible.value = false }
            composeRule.waitForIdle()

            assertEquals(true, bridge.pending.value!!.launched)
            assertEquals(1, registry.launches.size)

            composeRule.runOnIdle { visible.value = true }
            composeRule.waitForIdle()
            assertEquals(1, registry.launches.size)

            bridge.complete(RecognizerOutcome.Returned(Activity.RESULT_OK, Intent("example.RESULT")))
            assertEquals(Activity.RESULT_OK, (request.await() as RecognizerOutcome.Returned).resultCode)
            assertNull(bridge.pending.value)
        }

    @Test
    fun `restored host receives one result without launching a second time`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val registry = FakeActivityResultRegistry()
            val restoration = StateRestorationTester(composeRule)
            val request = async(start = CoroutineStart.UNDISPATCHED) { bridge.request(SpeechRequest("ru-RU")) }
            restoration.setContent {
                ProvideFakeActivityResultRegistry(registry) { VoiceHost(bridge) }
            }
            composeRule.waitForIdle()
            assertEquals(1, registry.launches.size)

            restoration.emulateSavedInstanceStateRestore()
            composeRule.waitForIdle()

            assertEquals(1, registry.launches.size)
            registry.respondWith(Activity.RESULT_OK, Intent("example.RESULT"))
            composeRule.waitForIdle()
            assertEquals(Activity.RESULT_OK, (request.await() as RecognizerOutcome.Returned).resultCode)
            assertNull(bridge.pending.value)
        }

    @Test
    fun `late result after request cancellation is ignored`() =
        runTest {
            val bridge = SpeechRequestBridge()
            val registry = FakeActivityResultRegistry()
            val request = async(start = CoroutineStart.UNDISPATCHED) { bridge.request(SpeechRequest("ru-RU")) }

            composeRule.setContent {
                ProvideFakeActivityResultRegistry(registry) { VoiceHost(bridge) }
            }
            composeRule.waitForIdle()
            request.cancelAndJoin()
            composeRule.waitForIdle()
            assertNull(bridge.pending.value)

            registry.respondWith(Activity.RESULT_OK, Intent("example.RESULT"))
            composeRule.waitForIdle()
            assertNull(bridge.pending.value)
        }
}
