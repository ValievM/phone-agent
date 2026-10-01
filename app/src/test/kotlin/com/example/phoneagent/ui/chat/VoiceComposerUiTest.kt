package com.example.phoneagent.ui.chat

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.ui.chat.voice.VoiceUiEvent
import com.example.phoneagent.ui.chat.voice.VoiceUiState
import com.example.phoneagent.ui.chat.voice.messageRes
import com.example.phoneagent.ui.designsystem.components.Composer
import com.example.phoneagent.ui.designsystem.components.ComposerMode
import com.example.phoneagent.ui.designsystem.components.ComposerVoice
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class VoiceComposerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `composer hides mic without voice and outside ready mode`() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    Composer("", {}, ComposerMode.Ready, {}, {})
                    Composer("", {}, ComposerMode.Running, {}, {}, voice = ComposerVoice(false, {}, {}))
                    Composer("", {}, ComposerMode.Unavailable, {}, {}, voice = ComposerVoice(false, {}, {}))
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("Голосовой ввод").assertCountEquals(0)
    }

    @Test
    fun `ready composer starts dictation and listening composer stops it`() {
        val events = mutableListOf<String>()
        val listening = mutableStateOf(false)
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                Composer(
                    "",
                    {},
                    ComposerMode.Ready,
                    {},
                    {},
                    voice =
                        ComposerVoice(
                            listening = listening.value,
                            onStart = { events += "start" },
                            onStop = { events += "stop" },
                        ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Голосовой ввод").assertIsDisplayed().performClick()
        composeRule.runOnIdle { listening.value = true }
        composeRule.onNodeWithContentDescription("Остановить запись").assertIsDisplayed().performClick()

        assertEquals(listOf("start", "stop"), events)
    }

    @Test
    fun `result appends to saved draft without submitting and consumes once per id`() {
        val events = mutableListOf<ChatUiEvent>()
        val voiceEvents = mutableListOf<VoiceUiEvent>()
        val voice = mutableStateOf<VoiceUiState?>(VoiceUiState.Result("привет", 1))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            AppTheme(useDynamicColor = false) {
                ChatScreen(
                    state = ChatUiState(),
                    onEvent = events::add,
                    onOpenSettings = {},
                    voice = voice.value,
                    onVoiceEvent = { event ->
                        voiceEvents += event
                        if (event is VoiceUiEvent.ResultConsumed) voice.value = null
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("привет").assertIsDisplayed()
        composeRule.onNodeWithText("Текст распознан").assertIsDisplayed()
        assertEquals(listOf(VoiceUiEvent.ResultConsumed(1)), voiceEvents)
        assertEquals(emptyList<ChatUiEvent>(), events)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("привет").assertIsDisplayed()
        assertEquals(listOf(VoiceUiEvent.ResultConsumed(1)), voiceEvents)

        composeRule.runOnIdle { voice.value = VoiceUiState.Result("мир", 2) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("привет мир").assertIsDisplayed()
        assertEquals(listOf(VoiceUiEvent.ResultConsumed(1), VoiceUiEvent.ResultConsumed(2)), voiceEvents)
        composeRule.onNodeWithText("привет мир").performTextReplacement("!")
        composeRule.onAllNodesWithText("Текст распознан").assertCountEquals(0)
        composeRule.onNodeWithText("!").assertExists()

        composeRule.runOnIdle { voice.value = VoiceUiState.Result("ещё", 3) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Текст распознан").assertIsDisplayed()
        composeRule.onNodeWithText("! ещё").assertExists()

        composeRule.onNodeWithContentDescription("Отправить сообщение").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(ChatUiEvent.Submit("! ещё")), events)
        composeRule.onAllNodesWithText("Текст распознан").assertCountEquals(0)
        composeRule.onNodeWithText("Сообщение").assertExists()

        composeRule.runOnIdle { voice.value = VoiceUiState.Result("снова", 4) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Текст распознан").assertIsDisplayed()
        composeRule.onNodeWithText("снова").assertExists()
        assertEquals(
            listOf(
                VoiceUiEvent.ResultConsumed(1),
                VoiceUiEvent.ResultConsumed(2),
                VoiceUiEvent.ResultConsumed(3),
                VoiceUiEvent.ResultConsumed(4),
            ),
            voiceEvents,
        )
        assertEquals(listOf(ChatUiEvent.Submit("! ещё")), events)
    }

    @Test
    fun `speech error displays banner and dismiss event while cancelled stays hidden`() {
        val voiceEvents = mutableListOf<VoiceUiEvent>()
        val voice = mutableStateOf<VoiceUiState?>(VoiceUiState.Error(SpeechFailure.NO_MATCH))
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ChatScreen(
                    state = ChatUiState(),
                    onEvent = {},
                    onOpenSettings = {},
                    voice = voice.value,
                    onVoiceEvent = voiceEvents::add,
                )
            }
        }
        composeRule.onNodeWithText("Не расслышал. Нажмите микрофон и повторите.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Закрыть сообщение об ошибке").performClick()
        assertEquals(listOf(VoiceUiEvent.DismissError), voiceEvents)

        composeRule.runOnIdle { voice.value = VoiceUiState.Error(SpeechFailure.CANCELLED) }
        composeRule.onAllNodesWithText("Не расслышал. Нажмите микрофон и повторите.").assertCountEquals(0)
    }

    @Test
    fun `each non cancellation failure maps to nonempty text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SpeechFailure.entries.filterNot { it == SpeechFailure.CANCELLED }.forEach { failure ->
            val resource = failure.messageRes()
            assertEquals("$failure must have text", false, resource == null || context.getString(resource).isBlank())
        }
        assertEquals(null, SpeechFailure.CANCELLED.messageRes())
    }
}
