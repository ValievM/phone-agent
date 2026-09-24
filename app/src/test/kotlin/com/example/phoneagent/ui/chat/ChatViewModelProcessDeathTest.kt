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
import org.junit.Assert.assertNotNull
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

    @Test
    fun `two stopped questions keep distinct ids so the restored feed has unique keys`() =
        runTest(dispatcher.scheduler) {
            val repository = Repository()
            val viewModel = ChatViewModel(agentLoop(HangingGateway()), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("Первый"))
            runCurrent()
            viewModel.onEvent(ChatUiEvent.Stop)
            advanceUntilIdle()
            viewModel.onEvent(ChatUiEvent.Submit("Второй"))
            runCurrent()
            viewModel.onEvent(ChatUiEvent.Stop)
            advanceUntilIdle()

            val restarted = ChatViewModel(agentLoop(FakeModelGateway()), repository)
            advanceUntilIdle()

            val messages = restarted.state.value.messages
            assertEquals(listOf("Первый", "Второй"), messages.map(ChatMessage::text))
            // The feed uses the message id as its LazyColumn key: duplicates crash the screen.
            assertEquals(messages.size, messages.map(ChatMessage::id).toSet().size)
        }

    @Test
    fun `a stop right after submit still keeps the question for the next turn and a restart`() =
        runTest(dispatcher.scheduler) {
            val repository = Repository()
            val gateway = HangingRecordingGateway()
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("Первый"))
            viewModel.onEvent(ChatUiEvent.Stop)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isRunning)
            assertEquals(0, gateway.requests.size)

            viewModel.onEvent(ChatUiEvent.Submit("Второй"))
            advanceUntilIdle()

            assertEquals(
                listOf("Первый", "Второй"),
                repository.load().filterIsInstance<HistoryItem.UserMessage>().map(HistoryItem.UserMessage::text),
            )
            val restarted = ChatViewModel(agentLoop(FakeModelGateway()), repository)
            advanceUntilIdle()
            assertEquals(
                listOf("Первый", "Второй"),
                restarted.state.value.messages
                    .filter { it.role == ChatRole.USER }
                    .map(ChatMessage::text),
            )
        }

    @Test
    fun `a finished turn replaces the provisional question exactly once`() =
        runTest(dispatcher.scheduler) {
            // A channel-based repository does not conflate saves, so every intermediate write is
            // delivered back - like Room does in production.
            val repository = EagerRepository()
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.TextDelta("Ответ"), ModelEvent.Completed()) }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertEquals(listOf("Привет", "Ответ"), repository.load().map(::textOf))
        }

    @Test
    fun `an unexpected exception in the turn surfaces an error with retry instead of crashing`() =
        runTest(dispatcher.scheduler) {
            val repository = Repository()
            val gateway = ThrowThenCompleteGateway()
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isRunning)
            assertNotNull(state.error)
            assertTrue(state.canRetry)

            viewModel.onEvent(ChatUiEvent.Retry)
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertEquals(listOf("Привет", "Ответ"), repository.load().map(::textOf))
        }

    @Test
    fun `a failed question save is cleared once the finished turn saves`() =
        runTest(dispatcher.scheduler) {
            val repository = FailFirstSaveRepository()
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.TextDelta("Ответ"), ModelEvent.Completed()) }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertEquals(listOf("Привет", "Ответ"), repository.load().map(::textOf))
        }

    @Test
    fun `a failed final save still shows the save error`() =
        runTest(dispatcher.scheduler) {
            val repository = FailLastSaveRepository()
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.TextDelta("Ответ"), ModelEvent.Completed()) }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertNotNull(viewModel.state.value.error)
            assertEquals(listOf("Привет"), repository.load().map(::textOf))
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

    private class HangingRecordingGateway : ModelGateway {
        val requests = mutableListOf<ModelRequest>()

        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                requests += request
                awaitCancellation()
            }
    }

    /** Delivers every save back to collectors, unlike a conflating MutableStateFlow. */
    private class EagerRepository : ConversationRepository {
        private val changes = kotlinx.coroutines.channels.Channel<List<HistoryItem>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        private var items: List<HistoryItem> = emptyList()

        override suspend fun load(): List<HistoryItem> = items

        override fun observe(): Flow<List<HistoryItem>> =
            flow {
                emit(items)
                for (change in changes) emit(change)
            }

        override suspend fun replace(items: List<HistoryItem>) {
            this.items = items
            changes.send(items)
            // Room re-queries on its own dispatcher, so the echo interleaves with the turn;
            // yield() reproduces that instead of letting one coroutine run to completion.
            kotlinx.coroutines.yield()
        }
    }

    private class FailFirstSaveRepository : ConversationRepository {
        private var items: List<HistoryItem> = emptyList()
        private val mutableItems = MutableStateFlow(items)
        private var failed = false

        override suspend fun load(): List<HistoryItem> = items

        override fun observe(): Flow<List<HistoryItem>> = mutableItems

        override suspend fun replace(items: List<HistoryItem>) {
            if (!failed) {
                failed = true
                throw java.io.IOException("disk is full")
            }
            this.items = items
            mutableItems.value = items
        }
    }

    private class FailLastSaveRepository : ConversationRepository {
        private var items: List<HistoryItem> = emptyList()
        private val mutableItems = MutableStateFlow(items)
        private var writes = 0

        override suspend fun load(): List<HistoryItem> = items

        override fun observe(): Flow<List<HistoryItem>> = mutableItems

        override suspend fun replace(items: List<HistoryItem>) {
            writes++
            if (writes > 1) throw java.io.IOException("disk is full again")
            this.items = items
            mutableItems.value = items
        }
    }

    /** The first turn explodes mid-stream; the retry answers normally. */
    private class ThrowThenCompleteGateway : ModelGateway {
        var calls = 0

        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                if (calls++ == 0) {
                    emit(ModelEvent.TextDelta("частичный ответ"))
                    error("gateway exploded")
                }
                emit(ModelEvent.TextDelta("Ответ"))
                emit(ModelEvent.Completed())
            }
    }
}
