package com.example.phoneagent.ui.chat

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.ui.designsystem.components.ASSISTANT_MESSAGE_ACTIONS_TAG
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AssistantAnswerActionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val answer = "**Исходный markdown**\n\n`код`"

    private fun show(
        messages: List<ChatMessage> = listOf(ChatMessage("assistant", ChatRole.ASSISTANT, answer)),
        turn: TurnState = TurnState.Idle,
    ) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ChatScreen(ChatUiState(messages = messages, turn = turn), {}, {})
            }
        }
    }

    @Test
    fun `empty assistant reply has no actions row`() {
        show(listOf(ChatMessage("assistant", ChatRole.ASSISTANT, "")))

        composeRule.onNodeWithTag(ASSISTANT_MESSAGE_ACTIONS_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Скопировать ответ").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Поделиться ответом").assertDoesNotExist()
    }

    @Test
    fun `completed assistant reply has copy and share actions`() {
        show()

        composeRule.onNodeWithContentDescription("Скопировать ответ").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Поделиться ответом").assertIsDisplayed()
    }

    @Test
    fun `user message has no answer actions`() {
        show(listOf(ChatMessage("user", ChatRole.USER, "Вопрос")))

        composeRule.onNodeWithContentDescription("Скопировать ответ").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Поделиться ответом").assertDoesNotExist()
    }

    @Test
    fun `running turn hides actions for its latest assistant reply`() {
        show(turn = TurnState.Running(ItemId("question")))

        composeRule.onNodeWithContentDescription("Скопировать ответ").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Поделиться ответом").assertDoesNotExist()
    }

    @Test
    fun `failed turn hides answer actions`() {
        show(turn = TurnState.FailedRetryable(ItemId("question"), AgentError.Offline))

        composeRule.onNodeWithContentDescription("Скопировать ответ").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Поделиться ответом").assertDoesNotExist()
    }

    @Test
    fun `copy writes the original markdown and confirmation expires on test clock`() {
        composeRule.mainClock.autoAdvance = false
        show()

        composeRule.onNodeWithContentDescription("Скопировать ответ").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        assertEquals(
            answer,
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                .toString(),
        )
        composeRule.onNodeWithContentDescription("Скопировано").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_600)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Скопировать ответ").assertIsDisplayed()
    }

    @Test
    fun `share launches chooser with original answer`() {
        show()

        composeRule.onNodeWithContentDescription("Поделиться ответом").performClick()
        val chooser = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val sendIntent = chooser?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(answer, sendIntent?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("text/plain", sendIntent?.type)
    }

    @Test
    fun `action buttons have accessible descriptions and at least 48dp targets`() {
        show()

        val copy = composeRule.onNodeWithContentDescription("Скопировать ответ")
        val share = composeRule.onNodeWithContentDescription("Поделиться ответом")
        copy.assertIsDisplayed()
        share.assertIsDisplayed()
        assertTrue(copy.fetchSemanticsNode().boundsInRoot.height / composeRule.density.density >= 48f)
        assertTrue(share.fetchSemanticsNode().boundsInRoot.height / composeRule.density.density >= 48f)
    }

    @Test
    fun `actions do not change message height when they become available`() {
        var state by mutableStateOf(
            ChatUiState(
                messages = listOf(ChatMessage("assistant", ChatRole.ASSISTANT, answer)),
                turn = TurnState.Running(ItemId("question")),
            ),
        )
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ChatScreen(state, {}, {})
            }
        }

        val row = composeRule.onNodeWithTag(ASSISTANT_MESSAGE_ACTIONS_TAG)
        val hiddenHeight = row.fetchSemanticsNode().boundsInRoot.height
        composeRule.runOnIdle { state = state.copy(turn = TurnState.Idle) }
        composeRule.waitForIdle()
        val visibleHeight = row.fetchSemanticsNode().boundsInRoot.height

        assertEquals(48f * composeRule.density.density, hiddenHeight, 1f)
        assertEquals(hiddenHeight, visibleHeight, 1f)
    }
}
