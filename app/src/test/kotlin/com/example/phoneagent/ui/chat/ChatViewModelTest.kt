package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
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
            val viewModel = ChatViewModel(agentLoop(gateway))
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
            val viewModel = ChatViewModel(agentLoop(gateway))

            viewModel.onEvent(ChatUiEvent.Submit("Проверка"))
            advanceUntilIdle()

            assertEquals(expectedError, viewModel.state.value.error)
            assertFalse(viewModel.state.value.isRunning)
        }

    @Test
    fun `stopping mid-response cancels the stream, clears running state, and keeps partial text`() =
        runTest(dispatcher.scheduler) {
            val gateway = HangingModelGateway()
            val viewModel = ChatViewModel(agentLoop(gateway))

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isRunning)

            viewModel.onEvent(ChatUiEvent.Stop)
            advanceUntilIdle()

            assertTrue(gateway.wasCancelled)
            assertFalse(viewModel.state.value.isRunning)
            assertEquals(
                "partial",
                viewModel.state.value.messages
                    .last()
                    .text,
            )
        }

    private fun agentLoop(gateway: ModelGateway): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline =
                ToolPipeline(
                    registry = ToolRegistry(emptySet()),
                    userInteraction = FakeUserInteraction(),
                    clock = FakeClock(),
                    logger = FakeAppLogger(),
                ),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private class HangingModelGateway : ModelGateway {
        var wasCancelled: Boolean = false

        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                emit(ModelEvent.TextDelta("partial"))
                try {
                    awaitCancellation()
                } finally {
                    wasCancelled = true
                }
            }
    }
}
