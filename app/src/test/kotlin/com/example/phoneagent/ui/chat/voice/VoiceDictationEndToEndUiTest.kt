package com.example.phoneagent.ui.chat.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.testing.FakeActivityResultRegistry
import com.example.phoneagent.testing.ProvideFakeActivityResultRegistry
import com.example.phoneagent.ui.chat.ChatScreen
import com.example.phoneagent.ui.chat.ChatUiEvent
import com.example.phoneagent.ui.chat.ChatUiState
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import com.example.phoneagent.ui.voice.VoiceHost
import com.example.phoneagent.voice.SpeechRequestBridge
import com.example.phoneagent.voice.SystemDialogSpeechInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class VoiceDictationEndToEndUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chatEvents = mutableListOf<ChatUiEvent>()

    private fun show(
        registry: FakeActivityResultRegistry = FakeActivityResultRegistry(),
        logger: FakeAppLogger = FakeAppLogger(),
    ): Fixture {
        val bridge = SpeechRequestBridge()
        val voiceViewModel = VoiceInputViewModel(SystemDialogSpeechInput(bridge, logger))
        composeRule.setContent {
            ProvideFakeActivityResultRegistry(registry) {
                AppTheme(useDynamicColor = false) {
                    VoiceHost(bridge)
                    val voice by voiceViewModel.state.collectAsState()
                    ChatScreen(
                        state = ChatUiState(),
                        onEvent = chatEvents::add,
                        onOpenSettings = {},
                        voice = voice,
                        onVoiceEvent = voiceViewModel::onEvent,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return Fixture(registry, bridge, voiceViewModel)
    }

    @Test
    fun `recognition intent uses Russian and final text fills draft without submitting`() {
        chatEvents.clear()
        val fixture = show()
        startDictation()

        val launch = fixture.registry.launches.single()
        val intent = launch.input as Intent
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("ru-RU", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        fixture.registry.respondWith(Activity.RESULT_OK, resultIntent("Поставь будильник на 7"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Поставь будильник на 7").assertExists()
        assertTrue(chatEvents.isEmpty())
    }

    @Test
    fun `a second dictation appends to the existing draft with a space`() {
        chatEvents.clear()
        val fixture = show()
        dictation(fixture, "Поставь будильник")
        startDictation()
        fixture.registry.respondWith(Activity.RESULT_OK, resultIntent("на 7"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Поставь будильник на 7").assertExists()
        assertTrue(chatEvents.isEmpty())
    }

    @Test
    fun `cancelled recognition leaves draft unchanged without a banner and restores microphone`() {
        chatEvents.clear()
        val fixture = show()
        composeRule.onNodeWithText("Сообщение").performTextInput("Черновик")
        startDictation()
        fixture.registry.respondWith(Activity.RESULT_CANCELED, null)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Черновик").assertExists()
        composeRule.onNodeWithText("Не получилось распознать. Попробуйте ещё раз.").assertDoesNotExist()
        microphone().assertIsEnabled()
        assertTrue(chatEvents.isEmpty())
    }

    @Test
    fun `no match shows dismissible banner and restores microphone`() {
        chatEvents.clear()
        val fixture = show()
        startDictation()
        fixture.registry.respondWith(RecognizerIntent.RESULT_NO_MATCH, null)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Не расслышал. Нажмите микрофон и повторите.").assertExists()
        microphone().assertIsEnabled()
        composeRule.onNodeWithContentDescription("Закрыть сообщение об ошибке").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Не расслышал. Нажмите микрофон и повторите.").assertDoesNotExist()
        microphone().assertIsEnabled()
        assertTrue(chatEvents.isEmpty())
    }

    @Test
    fun `missing recognition service shows unavailable banner`() {
        chatEvents.clear()
        val fixture = show(FakeActivityResultRegistry().apply { launchFailure = ActivityNotFoundException("unavailable") })
        startDictation()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("На телефоне нет службы распознавания речи. Установите или включите её.").assertExists()
        microphone().assertIsEnabled()
        assertEquals(1, fixture.registry.launches.size)
        assertTrue(chatEvents.isEmpty())
    }

    @Test
    fun `speech logs contain outcomes but never recognized text`() {
        chatEvents.clear()
        val logger = FakeAppLogger()
        val fixture = show(logger = logger)
        startDictation()
        fixture.registry.respondWith(Activity.RESULT_OK, resultIntent("Поставь будильник на 7"))
        composeRule.waitForIdle()

        assertTrue(logger.events.isNotEmpty())
        assertTrue(logger.events.any { it.fields.containsKey("outcome") })
        assertTrue(
            logger.events.none { event ->
                event.message.contains("будильник", ignoreCase = true) ||
                    event.fields.values.any { it.contains("будильник", ignoreCase = true) } ||
                    event.throwable?.stackTraceToString()?.contains("будильник", ignoreCase = true) == true
            },
        )
        assertTrue(chatEvents.isEmpty())
    }

    @Test
    fun `starting again while recognition is open does not launch a second dialog`() {
        chatEvents.clear()
        val fixture = show()
        startDictation()
        assertEquals(1, fixture.registry.launches.size)
        assertTrue(
            fixture.bridge.pending.value
                ?.launched == true,
        )
        fixture.voiceViewModel.onEvent(VoiceUiEvent.Start)
        composeRule.waitForIdle()

        assertEquals(1, fixture.registry.launches.size)
        fixture.registry.respondWith(Activity.RESULT_OK, resultIntent("Поставь будильник"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Поставь будильник").assertExists()
        assertTrue(chatEvents.isEmpty())
    }

    private fun dictation(
        fixture: Fixture,
        text: String,
    ) {
        startDictation()
        fixture.registry.respondWith(Activity.RESULT_OK, resultIntent(text))
        composeRule.waitForIdle()
    }

    private fun startDictation() {
        microphone().performClick()
        composeRule.waitForIdle()
    }

    private fun microphone() =
        composeRule.onNodeWithContentDescription(
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.voice_input),
        )

    private fun resultIntent(text: String) = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text))

    private data class Fixture(
        val registry: FakeActivityResultRegistry,
        val bridge: SpeechRequestBridge,
        val voiceViewModel: VoiceInputViewModel,
    )
}
