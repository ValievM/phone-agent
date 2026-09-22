package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentEvent
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.ui.designsystem.components.FileHit
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FileHitMapperTest {
    @Test
    fun `search files tool result maps structured content uris`() {
        val event =
            AgentEvent.ToolCallFinished(
                call = ToolCall("call-1", "search_files", buildJsonObject {}),
                outcome =
                    ToolOutcome.Success(
                        """{"items":[{"name":"report.pdf","uri":"content://media/external/downloads/42",
                            "path":"Download/report.pdf","size":1024,"dateModified":123,"mimeType":"application/pdf"}],
                            "truncated":false}""",
                    ),
            )

        assertEquals(
            listOf(FileHit("report.pdf", "content://media/external/downloads/42", 1024L, "application/pdf")),
            event.toFileHits(),
        )
    }

    @Test
    fun `assistant text is never parsed for file hits`() {
        val event =
            AgentEvent.TextDelta(
                """{"items":[{"name":"fake.txt","uri":"content://media/external/files/7","size":1,"mimeType":"text/plain"}]}""",
            )

        assertEquals(emptyList<FileHit>(), event.toFileHits())
    }

    @Test
    fun `malformed search files result has no hits`() {
        val event =
            AgentEvent.ToolCallFinished(
                call = ToolCall("call-1", "search_files", buildJsonObject {}),
                outcome = ToolOutcome.Success("not JSON"),
            )

        assertEquals(emptyList<FileHit>(), event.toFileHits())
    }

    @Test
    fun `other tool result is not treated as search files output`() {
        val event =
            AgentEvent.ToolCallFinished(
                call = ToolCall("call-1", "find_files", buildJsonObject {}),
                outcome =
                    ToolOutcome.Success(
                        """{"items":[{"name":"local.txt","uri":"content://media/external/files/7","size":1,"mimeType":"text/plain"}]}""",
                    ),
            )

        assertEquals(emptyList<FileHit>(), event.toFileHits())
    }

    @Test
    fun `non content uri is discarded`() {
        val event =
            AgentEvent.ToolCallFinished(
                call = ToolCall("call-1", "search_files", buildJsonObject {}),
                outcome =
                    ToolOutcome.Success(
                        """{"items":[{"name":"private.txt","uri":"file:///private/private.txt","size":1,"mimeType":"text/plain"}]}""",
                    ),
            )

        assertEquals(emptyList<FileHit>(), event.toFileHits())
    }
}
