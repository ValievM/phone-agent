package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.platform.RedactingAppLogger
import com.example.phoneagent.domain.platform.SanitizedThrowable
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelLoggingTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `failed history load logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val repository = LoggingConversationRepository(observeFailure = IOException(SECRET_PATH))
            createViewModel(repository, RedactingAppLogger(records))

            advanceUntilIdle()

            assertSafeWarning(records, "conversation history load failed")
        }

    @Test
    fun `failed history save logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val repository = LoggingConversationRepository(failedReplacements = 1)
            val viewModel = createViewModel(repository, RedactingAppLogger(records))
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("question"))
            advanceUntilIdle()

            assertSafeWarning(records, "conversation history save failed")
        }

    @Test
    fun `unexpected turn failure logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val gateway =
                object : com.example.phoneagent.domain.model.ModelGateway {
                    override fun stream(
                        request: com.example.phoneagent.domain.model.ModelRequest,
                    ): Flow<com.example.phoneagent.domain.model.ModelEvent> = flow { throw IOException(SECRET_PATH) }
                }
            val viewModel = createViewModel(LoggingConversationRepository(), RedactingAppLogger(records), gateway)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("question"))
            advanceUntilIdle()

            assertSafeWarning(records, "agent turn failed")
        }

    @Test
    fun `cancelled history load does not log a warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val repository = LoggingConversationRepository(observeFailure = CancellationException(SECRET_PATH))
            createViewModel(repository, RedactingAppLogger(records))

            advanceUntilIdle()

            assertTrue(records.events.isEmpty())
        }

    private fun createViewModel(
        repository: LoggingConversationRepository,
        logger: AppLogger,
        gateway: com.example.phoneagent.domain.model.ModelGateway = FakeModelGateway(),
    ): ChatViewModel = ChatViewModel(agentLoop(gateway), repository, logger = logger)

    private fun agentLoop(gateway: com.example.phoneagent.domain.model.ModelGateway): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline = ToolPipeline(ToolRegistry(emptySet()), FakeUserInteraction(), FakeClock(), FakeAppLogger()),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private fun assertSafeWarning(
        records: FakeAppLogger,
        operation: String,
    ) {
        assertEquals(1, records.events.size)
        val event = records.events.single()
        assertEquals(LogLevel.WARN, event.level)
        assertEquals("ChatViewModel", event.tag)
        assertEquals(operation, event.message)
        assertFalse(event.toString().contains(SECRET_PATH))
        assertEquals("IOException", event.fields["exceptionType"])
        assertTrue(event.throwable is SanitizedThrowable)
    }

    private class LoggingConversationRepository(
        private val observeFailure: Throwable? = null,
        failedReplacements: Int = 0,
    ) : ConversationRepository {
        private val items = MutableStateFlow<List<HistoryItem>>(emptyList())
        private var failedReplacementsRemaining = failedReplacements

        override suspend fun load(): List<HistoryItem> = items.value

        override fun observe(): Flow<List<HistoryItem>> =
            flow {
                observeFailure?.let { throw it }
                items.collect { emit(it) }
            }

        override suspend fun replace(items: List<HistoryItem>) {
            if (failedReplacementsRemaining > 0) {
                failedReplacementsRemaining--
                throw IOException(SECRET_PATH)
            }
            this.items.value = items
        }
    }

    private companion object {
        const val SECRET_PATH = "secret-path"
    }
}
