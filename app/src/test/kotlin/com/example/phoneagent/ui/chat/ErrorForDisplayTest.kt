package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.TurnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which error the chat shows when both a turn and a history sync have failed. Priority per the
 * owner-approved order: failed LOAD (the screen cannot be trusted), then the turn error (retry
 * stays visible), then a failed SAVE, else nothing.
 */
class ErrorForDisplayTest {
    @Test
    fun `ready and idle show nothing`() {
        assertNull(ChatUiState().errorForDisplay())
    }

    @Test
    fun `a retryable turn error is shown on its own`() {
        val state = ChatUiState(turn = TurnState.FailedRetryable(ItemId("q"), AgentError.Offline))

        assertEquals(AgentError.Offline, state.errorForDisplay())
    }

    @Test
    fun `a terminal turn error is shown on its own`() {
        val state = ChatUiState(turn = TurnState.FailedTerminal(AgentError.Forbidden))

        assertEquals(AgentError.Forbidden, state.errorForDisplay())
    }

    @Test
    fun `an interrupted turn is shown on its own`() {
        val state = ChatUiState(turn = TurnState.Interrupted(ItemId("q")))

        assertEquals(AgentError.Interrupted, state.errorForDisplay())
    }

    @Test
    fun `a save failure is shown on its own`() {
        val state = ChatUiState(historySync = HistorySyncState.Failed(StorageOperation.SAVE))

        assertEquals(
            HistorySyncState.Failed(StorageOperation.SAVE).error,
            state.errorForDisplay(),
        )
    }

    @Test
    fun `a turn error wins over a save failure so retry stays visible`() {
        val state =
            ChatUiState(
                turn = TurnState.FailedRetryable(ItemId("q"), AgentError.Offline),
                historySync = HistorySyncState.Failed(StorageOperation.SAVE),
            )

        assertEquals(AgentError.Offline, state.errorForDisplay())
    }

    @Test
    fun `an interrupted turn wins over a save failure`() {
        val state =
            ChatUiState(
                turn = TurnState.Interrupted(ItemId("q")),
                historySync = HistorySyncState.Failed(StorageOperation.SAVE),
            )

        assertEquals(AgentError.Interrupted, state.errorForDisplay())
    }

    @Test
    fun `a load failure wins over a turn error because the screen cannot be trusted`() {
        val state =
            ChatUiState(
                turn = TurnState.FailedRetryable(ItemId("q"), AgentError.Offline),
                historySync = HistorySyncState.Failed(StorageOperation.LOAD),
            )

        assertEquals(
            HistorySyncState.Failed(StorageOperation.LOAD).error,
            state.errorForDisplay(),
        )
    }

    @Test
    fun `loading and running show nothing`() {
        assertNull(ChatUiState(turn = TurnState.Running(ItemId("q")), historySync = HistorySyncState.Loading).errorForDisplay())
    }
}
