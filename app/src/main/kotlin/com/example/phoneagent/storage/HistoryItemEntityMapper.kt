package com.example.phoneagent.storage

import com.example.phoneagent.domain.conversation.HistoryItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal object HistoryItemEntityMapper {
    private const val USER_MESSAGE = "user_message"
    private const val ASSISTANT_MESSAGE = "assistant_message"
    private const val TOOL_CALL = "tool_call"
    private const val TOOL_OUTPUT = "tool_output"
    private const val OPAQUE = "opaque"

    fun toEntity(
        item: HistoryItem,
        position: Int,
    ): ConversationEntity =
        when (item) {
            is HistoryItem.UserMessage -> {
                ConversationEntity(
                    position = position,
                    id = item.id,
                    type = USER_MESSAGE,
                    text = item.text,
                )
            }

            is HistoryItem.AssistantMessage -> {
                ConversationEntity(
                    position = position,
                    id = item.id,
                    type = ASSISTANT_MESSAGE,
                    text = item.text,
                )
            }

            is HistoryItem.ToolCallItem -> {
                ConversationEntity(
                    position = position,
                    id = item.id,
                    type = TOOL_CALL,
                    callId = item.callId,
                    name = item.name,
                    argumentsJson = item.arguments.toString(),
                )
            }

            is HistoryItem.ToolOutput -> {
                ConversationEntity(
                    position = position,
                    id = item.id,
                    type = TOOL_OUTPUT,
                    callId = item.callId,
                    output = item.output,
                )
            }

            is HistoryItem.Opaque -> {
                ConversationEntity(
                    position = position,
                    id = item.id,
                    type = OPAQUE,
                    provider = item.provider,
                    payloadJson = item.payload.toString(),
                )
            }
        }

    fun toDomain(entity: ConversationEntity): HistoryItem =
        when (entity.type) {
            USER_MESSAGE -> {
                HistoryItem.UserMessage(id = entity.id, text = requireNotNull(entity.text))
            }

            ASSISTANT_MESSAGE -> {
                HistoryItem.AssistantMessage(id = entity.id, text = requireNotNull(entity.text))
            }

            TOOL_CALL -> {
                HistoryItem.ToolCallItem(
                    id = entity.id,
                    callId = requireNotNull(entity.callId),
                    name = requireNotNull(entity.name),
                    arguments =
                        Json.parseToJsonElement(requireNotNull(entity.argumentsJson)) as? JsonObject
                            ?: error("Tool call arguments must be a JSON object"),
                )
            }

            TOOL_OUTPUT -> {
                HistoryItem.ToolOutput(
                    id = entity.id,
                    callId = requireNotNull(entity.callId),
                    output = requireNotNull(entity.output),
                )
            }

            OPAQUE -> {
                HistoryItem.Opaque(
                    id = entity.id,
                    provider = requireNotNull(entity.provider),
                    payload = Json.parseToJsonElement(requireNotNull(entity.payloadJson)),
                )
            }

            else -> {
                error("Unknown conversation history item type: ${entity.type}")
            }
        }
}
