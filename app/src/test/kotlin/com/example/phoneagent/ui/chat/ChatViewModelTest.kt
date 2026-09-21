package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.model.ModelEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sending a message shows running state and appends fake response`() =
        runTest(dispatcher.scheduler) {
            val gateway =
                FakeModelGateway().apply {
                    events =
                        listOf(
                            ModelEvent.TextDelta("Готово"),
                            ModelEvent.Completed(),
                        )
                }
            val viewModel = ChatViewModel(gateway)
            val states = mutableListOf<ChatUiState>()
            backgroundScope.launch { viewModel.state.collect { states += it } }

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertTrue(states.any(ChatUiState::isRunning))
            assertEquals(
                listOf(ChatRole.USER, ChatRole.ASSISTANT),
                viewModel.state.value.messages
                    .map(ChatMessage::role),
            )
            assertEquals(
                "Готово",
                viewModel.state.value.messages
                    .last()
                    .text,
            )
            assertFalse(viewModel.state.value.isRunning)
        }

    @Test
    fun `model failure is exposed in state without crashing`() =
        runTest(dispatcher.scheduler) {
            val expectedError = AgentError.Offline
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.Failed(expectedError)) }
            val viewModel = ChatViewModel(gateway)

            viewModel.onEvent(ChatUiEvent.Submit("Проверка"))
            advanceUntilIdle()

            assertEquals(expectedError, viewModel.state.value.error)
            assertFalse(viewModel.state.value.isRunning)
        }
}
