package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.serialization.json.JsonObject

/** Updates the result card from the arguments and description that were actually approved and run. */
internal fun ChatUiState.withToolCallResult(
    id: String,
    status: ToolCallStatus,
    description: CallDescription,
    arguments: JsonObject,
    fileHits: List<FileHit>,
): ChatUiState =
    copy(
        messages =
            messages.map { message ->
                val toolCall = message.toolCall
                if (message.id == id && toolCall != null) {
                    message.copy(
                        toolCall = toolCall.copy(status = status, description = description, arguments = arguments, fileHits = fileHits),
                    )
                } else {
                    message
                }
            },
    )
