package com.example.phoneagent.interaction

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.ConfirmResult
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Queue semantics on top of the public contract: one question at a time, in arrival order. */
class UserInteractionBridgeQueueTest {
    private val bridge = UserInteractionBridge()

    @Test
    fun `two parallel confirms are served one by one and each gets its own answer`() =
        runTest {
            val first = async { bridge.confirm(request("06:00")) }
            advanceUntilIdle()
            val second = async { bridge.confirm(request("06:15")) }
            advanceUntilIdle()

            val head = bridge.pending.value as PendingInteraction.Approval
            assertEquals(request("06:00"), head.request)
            head.deferred.complete(ConfirmResult.Approved(head.request.arguments))
            assertTrue(first.await() is ConfirmResult.Approved)

            val next = bridge.pending.value as PendingInteraction.Approval
            assertEquals(request("06:15"), next.request)
            next.deferred.complete(ConfirmResult.Rejected)
            assertEquals(ConfirmResult.Rejected, second.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `cancelling a queued call does not touch the current one`() =
        runTest {
            val first = async { bridge.confirm(request("06:00")) }
            advanceUntilIdle()
            val second = async { bridge.confirm(request("06:15")) }
            advanceUntilIdle()

            second.cancel()
            advanceUntilIdle()

            assertTrue(bridge.pending.value is PendingInteraction.Approval)
            val head = bridge.pending.value as PendingInteraction.Approval
            assertEquals(request("06:00"), head.request)
            head.deferred.complete(ConfirmResult.Approved(head.request.arguments))
            assertTrue(first.await() is ConfirmResult.Approved)
            assertNull(bridge.pending.value)
        }

    @Test
    fun `the host leaving declines the current question and every waiting one`() =
        runTest {
            val first = async { bridge.confirm(request("06:00")) }
            advanceUntilIdle()
            val second = async { bridge.confirm(request("06:15")) }
            advanceUntilIdle()

            bridge.resolvePendingAsDeclined()

            assertEquals(ConfirmResult.Rejected, first.await())
            assertEquals(ConfirmResult.Rejected, second.await())
            assertNull(bridge.pending.value)
        }

    private fun request(time: String) =
        ApprovalRequest(CallDescription("Поставить будильник", "На $time"), Risk.CONFIRM, buildJsonObject {})
}
