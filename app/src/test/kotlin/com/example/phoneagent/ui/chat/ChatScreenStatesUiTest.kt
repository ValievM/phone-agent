package com.example.phoneagent.ui.chat

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.domain.agent.AgentError
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
class ChatScreenStatesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ChatUiEvent>()

    private fun show(state: ChatUiState) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ChatScreen(state = state, onEvent = { events += it }, onOpenSettings = {})
            }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun userMessage() = listOf(ChatMessage("1", ChatRole.USER, "Привет"))

    @Test
    fun `offline is a status banner with retry and dismiss`() {
        show(ChatUiState(messages = userMessage(), error = AgentError.Offline, canRetry = true))

        composeRule.onNodeWithText(string(R.string.error_offline)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_retry)).performClick()
        composeRule.onNodeWithText(string(R.string.dismiss_error)).performClick()

        assertEquals(listOf(ChatUiEvent.Retry, ChatUiEvent.DismissError), events)
    }

    @Test
    fun `an expired session banner has no retry`() {
        show(ChatUiState(messages = userMessage(), error = AgentError.SessionExpired, canRetry = false))

        composeRule.onNodeWithText(string(R.string.error_session_expired)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_retry)).assertDoesNotExist()
    }

    @Test
    fun `a timeout is shown inside the feed with a retry action`() {
        show(ChatUiState(messages = userMessage(), error = AgentError.Timeout, canRetry = true))

        composeRule.onNodeWithText(string(R.string.error_timeout)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_retry)).performClick()

        assertEquals(listOf(ChatUiEvent.Retry), events)
    }

    @Test
    fun `a rate limit tells the seconds to wait`() {
        show(ChatUiState(messages = userMessage(), error = AgentError.RateLimited(30), canRetry = true))

        composeRule.onNodeWithText(string(R.string.error_rate_limited_after, 30L)).assertIsDisplayed()
    }

    @Test
    fun `no retry button when the failure cannot be retried`() {
        show(ChatUiState(messages = userMessage(), error = AgentError.Forbidden, canRetry = false))

        composeRule.onNodeWithText(string(R.string.error_forbidden)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_retry)).assertDoesNotExist()
    }

    @Test
    fun `the empty chat offers three suggestions that fill the composer without sending`() {
        show(ChatUiState())

        composeRule.onNodeWithText(string(R.string.chat_suggestion_alarm)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_suggestion_find_file).trim()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_suggestion_notify).trim()).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.chat_suggestion_alarm)).performClick()

        // The chip and the composer's text field now both show the suggestion; nothing was sent.
        assertEquals(2, composeRule.onAllNodesWithText(string(R.string.chat_suggestion_alarm)).fetchSemanticsNodes().size)
        assertEquals(emptyList<ChatUiEvent>(), events)
    }

    @Test
    fun `typing and sending submits the text and clears the field`() {
        show(ChatUiState())

        composeRule.onNodeWithText(string(R.string.message_hint)).performTextInput("Привет")
        composeRule.onNodeWithContentDescription(string(R.string.send_message)).performClick()

        assertEquals(listOf<ChatUiEvent>(ChatUiEvent.Submit("Привет")), events)
    }
}
