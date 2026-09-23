package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.ui.chat.ChatMessage
import com.example.phoneagent.ui.chat.ChatRole
import com.example.phoneagent.ui.chat.ChatScreen
import com.example.phoneagent.ui.chat.ChatUiState
import com.example.phoneagent.ui.chat.ToolCallMessage
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ChatScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun screen(
        name: String,
        state: ChatUiState,
        variants: List<Variant> = listOf(Variant.Light, Variant.Dark),
    ) = composeRule.snapshot("chat_$name", variants, fillScreen = true) {
        ChatScreen(state = state, onEvent = {}, onOpenSettings = {})
    }

    @Test
    fun empty() = screen("empty", ChatUiState())

    @Test
    fun conversationWithToolCard() =
        screen(
            "conversation",
            ChatUiState(
                messages =
                    listOf(
                        ChatMessage("1", ChatRole.USER, "Поставь будильник на 7:30"),
                        ChatMessage("2", ChatRole.ASSISTANT, "Ставлю будильник на 07:30."),
                        ChatMessage(
                            "3",
                            ChatRole.TOOL,
                            "",
                            ToolCallMessage(
                                ToolCallStatus.Done("Будильник поставлен на 07:30"),
                                alarmDescription,
                                alarmArguments,
                            ),
                        ),
                        ChatMessage("4", ChatRole.ASSISTANT, "Готово. Проверьте в приложении «Часы»."),
                    ),
            ),
            listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
        )

    @Test
    fun running() =
        screen(
            "running",
            ChatUiState(
                messages = listOf(ChatMessage("1", ChatRole.USER, "Найди договор аренды")),
                isRunning = true,
            ),
        )

    private val asked = listOf(ChatMessage("1", ChatRole.USER, "Найди договор аренды"))

    @Test
    fun offlineBanner() = screen("offline", ChatUiState(messages = asked, error = AgentError.Offline, canRetry = true))

    @Test
    fun sessionExpiredBanner() = screen("session_expired", ChatUiState(messages = asked, error = AgentError.SessionExpired))

    @Test
    fun timeoutInline() =
        screen(
            "timeout_inline",
            ChatUiState(messages = asked, error = AgentError.Timeout, canRetry = true),
            listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
        )

    @Test
    fun rateLimitedInline() =
        screen("rate_limited_inline", ChatUiState(messages = asked, error = AgentError.RateLimited(30), canRetry = true))

    @Test
    fun forbiddenInlineWithoutRetry() = screen("forbidden_inline", ChatUiState(messages = asked, error = AgentError.Forbidden))
}
