package com.example.phoneagent.storage

import com.example.phoneagent.domain.conversation.HistoryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomConversationRepositoryTest {
    @Test
    fun `load returns mixed history in stored order`() =
        runTest {
            val items =
                listOf(
                    HistoryItem.UserMessage(id = "user-1", text = "Hello"),
                    HistoryItem.ToolCallItem("item-1", "call-1", "search_files", buildJsonObject {}),
                    HistoryItem.ToolOutput("output-1", "call-1", "Found it"),
                    HistoryItem.AssistantMessage(id = "assistant-1", text = "Done"),
                    HistoryItem.Opaque("opaque-1", "provider", buildJsonObject { }),
                )
            val storedRows = items.mapIndexed { position, item -> HistoryItemEntityMapper.toEntity(item, position) }
            val fakeDao = FakeConversationDao(storedRows)
            val repository = RoomConversationRepository(fakeDao)

            assertEquals(items, repository.load())
        }

    @Test
    fun `replace overwrites previous rows instead of appending`() =
        runTest {
            val repository = RoomConversationRepository(FakeConversationDao(emptyList()))
            val previous = listOf(HistoryItem.UserMessage(id = "old", text = "Old"))
            val replacement = listOf(HistoryItem.AssistantMessage(id = "new", text = "New"))

            repository.replace(previous)
            repository.replace(replacement)

            assertEquals(replacement, repository.load())
        }

    @Test
    fun `replacing with empty history clears all rows`() =
        runTest {
            val repository =
                RoomConversationRepository(
                    FakeConversationDao(
                        listOf(HistoryItemEntityMapper.toEntity(HistoryItem.UserMessage("old", "Old"), position = 0)),
                    ),
                )

            repository.replace(emptyList())

            assertEquals(emptyList<HistoryItem>(), repository.load())
        }

    private class FakeConversationDao(
        initialRows: List<ConversationEntity>,
    ) : ConversationDao {
        private var rows = initialRows

        override suspend fun loadAll(): List<ConversationEntity> = rows.sortedBy(ConversationEntity::position)

        override suspend fun insertAll(items: List<ConversationEntity>) {
            rows = rows + items
        }

        override suspend fun deleteAll() {
            rows = emptyList()
        }
    }
}
