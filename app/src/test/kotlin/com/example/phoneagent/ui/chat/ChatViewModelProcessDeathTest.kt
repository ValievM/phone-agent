package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelProcessDeathTest {
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
    fun `the question is saved as soon as it is sent so a killed process cannot lose it`() =
        runTest(dispatcher.scheduler) {
            val repository = Repository()
            val viewModel = ChatViewModel(agentLoop(HangingGateway()), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("Поставь будильник"))
            runCurrent()

            try {
                assertTrue(viewModel.state.value.isRunning)
                assertEquals(
                    listOf("Поставь будильник"),
                    repository.load().filterIsInstance<HistoryItem.UserMessage>().map(HistoryItem.UserMessage::text),
                )
            } finally {
                viewModel.onEvent(ChatUiEvent.Stop)
                runCurrent()
            }
        }

    @Test
    fun `saving the question does not cancel the turn and the finished turn replaces it`() =
        runTest(dispatcher.scheduler) {
            val repository = Repository()
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.TextDelta("Готово"), ModelEvent.Completed()) }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertEquals(1, gateway.requests.size)
            assertEquals(listOf("Привет", "Готово"), repository.load().map(::textOf))
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `after a restart an unanswered question is shown with an offer to retry`() =
        runTest(dispatcher.scheduler) {
            val repository =
                Repository(
                    listOf(
                        HistoryItem.UserMessage("u1", "Первый вопрос"),
                        HistoryItem.AssistantMessage("a1", "Первый ответ"),
                        HistoryItem.UserMessage("u2", "Второй вопрос"),
                    ),
                )

            val viewModel = ChatViewModel(agentLoop(FakeModelGateway()), repository)
            advanceUntilIdle()

            assertEquals(
                listOf("Первый вопрос", "Первый ответ", "Второй вопрос"),
                viewModel.state.value.messages
                    .map(ChatMessage::text),
            )
            assertEquals(AgentError.Interrupted, viewModel.state.value.error)
            assertTrue(viewModel.state.value.canRetry)
        }

    @Test
    fun `a fully answered history restores without any error`() =
        runTest(dispatcher.scheduler) {
            val repository =
                Repository(listOf(HistoryItem.UserMessage("u1", "Вопрос"), HistoryItem.AssistantMessage("a1", "Ответ")))

            val viewModel = ChatViewModel(agentLoop(FakeModelGateway()), repository)
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.canRetry)
        }

    @Test
    fun `retrying after a restart answers the unanswered question once and keeps the earlier turns`() =
        runTest(dispatcher.scheduler) {
            val repository =
                Repository(
                    listOf(
                        HistoryItem.UserMessage("u1", "Первый вопрос"),
                        HistoryItem.AssistantMessage("a1", "Первый ответ"),
                        HistoryItem.UserMessage("u2", "Второй вопрос"),
                    ),
                )
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.TextDelta("Второй ответ"), ModelEvent.Completed()) }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Retry)
            advanceUntilIdle()

            assertEquals(
                listOf("Первый вопрос", "Второй вопрос"),
                gateway.requests
                    .single()
                    .history
                    .filterIsInstance<HistoryItem.UserMessage>()
                    .map(HistoryItem.UserMessage::text),
            )
            assertEquals(
                listOf("Первый вопрос", "Первый ответ", "Второй вопрос", "Второй ответ"),
                repository.load().map(::textOf),
            )
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `clearing the history from settings also dismisses the interrupted notice`() =
        runTest(dispatcher.scheduler) {
            val repository = Repository(listOf(HistoryItem.UserMessage("u1", "Вопрос")))
            val viewModel = ChatViewModel(agentLoop(FakeModelGateway()), repository)
            advanceUntilIdle()
            assertEquals(AgentError.Interrupted, viewModel.state.value.error)

            repository.replace(emptyList())
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.canRetry)
        }

    private fun textOf(item: HistoryItem): String =
        when (item) {
            is HistoryItem.UserMessage -> item.text
            is HistoryItem.AssistantMessage -> item.text
            else -> error("unexpected $item")
        }

    private fun agentLoop(gateway: ModelGateway): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline = ToolPipeline(ToolRegistry(emptySet()), FakeUserInteraction(), FakeClock(), FakeAppLogger()),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private class Repository(
        initial: List<HistoryItem> = emptyList(),
    ) : ConversationRepository {
        private var items = initial
        private val mutableItems = MutableStateFlow(initial)

        override suspend fun load(): List<HistoryItem> = items

        override fun observe(): Flow<List<HistoryItem>> = mutableItems

        override suspend fun replace(items: List<HistoryItem>) {
            this.items = items
            mutableItems.value = items
        }
    }

    private class HangingGateway : ModelGateway {
        override fun stream(request: ModelRequest): Flow<ModelEvent> = flow { awaitCancellation() }
    }
}
