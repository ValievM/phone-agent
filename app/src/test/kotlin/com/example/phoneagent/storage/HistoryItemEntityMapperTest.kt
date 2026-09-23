package com.example.phoneagent.storage

import com.example.phoneagent.domain.conversation.HistoryItem
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryItemEntityMapperTest {
    @Test
    fun `user message maps to room entity and back`() {
        val item = HistoryItem.UserMessage(id = "user-1", text = "Hello")
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `assistant message maps to room entity and back`() {
        val item = HistoryItem.AssistantMessage(id = "assistant-1", text = "Hi")
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `tool call maps identifiers name and nested arguments`() {
        val item =
            HistoryItem.ToolCallItem(
                id = "item-1",
                callId = "call-1",
                name = "search_files",
                arguments =
                    buildJsonObject {
                        put("query", "notes")
                        put("filters", buildJsonObject { put("extension", "txt") })
                    },
            )
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `tool output maps call identifier and output`() {
        val item = HistoryItem.ToolOutput(id = "output-1", callId = "call-1", output = "Found 3 files")
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `opaque item preserves provider and nested payload`() {
        val item =
            HistoryItem.Opaque(
                id = "opaque-1",
                provider = "openai",
                payload =
                    buildJsonObject {
                        put("type", "reasoning")
                        put("details", buildJsonObject { put("encrypted", true) })
                    },
            )
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }
}
