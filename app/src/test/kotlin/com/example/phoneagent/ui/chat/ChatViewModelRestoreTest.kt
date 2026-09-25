package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Reopening the app (a new ViewModel over the saved history) must show the tool cards again. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelRestoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val args = buildJsonObject { put("hour", 5) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val alarmTool =
        object : Tool {
            override val spec = ToolSpec("set_alarm", "sets an alarm", buildJsonObject {}, Risk.CONFIRM)

            override fun describe(args: JsonObject) = CallDescription("Поставить будильник", "На 05:00")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = ToolResult.Success("unused")
        }

    private val saved =
        listOf(
            HistoryItem.UserMessage("u1", "поставь будильник на 5 утра"),
            HistoryItem.ToolCallItem("t1", "c1", "set_alarm", args),
            HistoryItem.ToolOutput("o1", "c1", "Запрос на будильник отправлен"),
            HistoryItem.AssistantMessage("a1", "Поставил будильник на 5:00."),
        )

    private fun viewModelOver(items: List<HistoryItem>): ChatViewModel {
        val repository =
            object : ConversationRepository {
                private val flow = MutableStateFlow(items)

                override suspend fun load() = flow.value

                override fun observe(): Flow<List<HistoryItem>> = flow

                override suspend fun replace(items: List<HistoryItem>) {
                    flow.value = items
                }
            }
        val loop =
            AgentLoop(
                modelGateway = FakeModelGateway(),
                toolPipeline = ToolPipeline(ToolRegistry(setOf(alarmTool)), FakeUserInteraction(), FakeClock(), FakeAppLogger()),
                contextWindowPolicy = ContextWindowPolicy { it },
                idGenerator = FakeIdGenerator(),
            )
        return ChatViewModel(loop, repository)
    }

    @Test
    fun `after reopening the app the tool card is still in the conversation`() =
        runTest(dispatcher.scheduler) {
            val viewModel = viewModelOver(saved)

            advanceUntilIdle()

            assertEquals(
                listOf(ChatRole.USER, ChatRole.TOOL, ChatRole.ASSISTANT),
                viewModel.state.value.messages
                    .map { it.role },
            )
        }

    @Test
    fun `the restored card is described by the registered tool and shows the saved result`() =
        runTest(dispatcher.scheduler) {
            val viewModel = viewModelOver(saved)

            advanceUntilIdle()

            val card =
                viewModel.state.value.messages[1]
                    .toolCall
            assertEquals(CallDescription("Поставить будильник", "На 05:00"), card?.description)
            assertEquals(ToolCallStatus.Done("Запрос на будильник отправлен"), card?.status)
        }

    @Test
    fun `a card of a tool that no longer exists is still shown under its name`() =
        runTest(dispatcher.scheduler) {
            val viewModel = viewModelOver(saved.map { if (it is HistoryItem.ToolCallItem) it.copy(name = "old_tool") else it })

            advanceUntilIdle()

            assertEquals(
                "old_tool",
                viewModel.state.value.messages[1]
                    .toolCall
                    ?.description
                    ?.title,
            )
        }
}
