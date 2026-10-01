package com.example.phoneagent.interaction

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.ConfirmResult
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInteractionBridgeTest {
    private val bridge = UserInteractionBridge()

    @Test
    fun `single confirmation returns the exact approved arguments`() =
        runTest {
            val editedArguments = buildJsonObject { put("minute", 45) }
            val answer = async { bridge.confirm(request("06:30")) }
            advanceUntilIdle()

            val pending = bridge.pending.value as PendingInteraction.Approval
            pending.deferred.complete(ConfirmResult.Approved(editedArguments))

            assertEquals(ConfirmResult.Approved(editedArguments), answer.await())
        }

    @Test
    fun `a batch question stays pending until answered and returns the answer`() =
        runTest {
            val requests = listOf(request("06:00"), request("06:15"))

            val answer = async { bridge.confirmBatch(requests) }
            advanceUntilIdle()

            val pending = bridge.pending.value as PendingInteraction.BatchApproval
            assertEquals(requests, pending.requests)
            pending.deferred.complete(true)
            assertTrue(answer.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `leaving the screen declines a pending batch`() =
        runTest {
            val answer = async { bridge.confirmBatch(listOf(request("06:00"), request("06:15"))) }
            advanceUntilIdle()

            bridge.resolvePendingAsDeclined()

            assertFalse(answer.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `closing a permission request resolves as denied and clears dialog visibility`() =
        runTest {
            val answer = async { bridge.ensure(Capability.NOTIFICATIONS) }
            advanceUntilIdle()
            assertTrue(bridge.pending.value is PendingInteraction.Permission)
            bridge.setPermissionDialogVisible(true)

            bridge.resolvePendingAsDeclined()

            assertEquals(CapabilityResult.Denied, withTimeout(1_000) { answer.await() })
            assertNull(bridge.pending.value)
            assertFalse(bridge.permissionDialogVisible.value)
        }

    @Test
    fun `permission can be requested again after denial`() =
        runTest {
            val first = async { bridge.ensure(Capability.NOTIFICATIONS) }
            advanceUntilIdle()
            val firstRequest = bridge.pending.value as PendingInteraction.Permission
            firstRequest.deferred.complete(CapabilityResult.Denied)
            assertEquals(CapabilityResult.Denied, first.await())

            val second = async { bridge.ensure(Capability.NOTIFICATIONS) }
            advanceUntilIdle()
            val secondRequest = bridge.pending.value as PendingInteraction.Permission
            assertNotSame(firstRequest, secondRequest)
            secondRequest.deferred.complete(CapabilityResult.Available)

            assertEquals(CapabilityResult.Available, second.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `cancelling a pending permission clears dialog visibility`() =
        runTest {
            val job = launch { bridge.ensure(Capability.FILES) }
            advanceUntilIdle()
            assertTrue(bridge.pending.value is PendingInteraction.Permission)
            bridge.setPermissionDialogVisible(true)

            job.cancel()
            advanceUntilIdle()

            assertNull(bridge.pending.value)
            assertFalse(bridge.permissionDialogVisible.value)
        }

    @Test
    fun `cancelling the caller clears a pending batch`() =
        runTest {
            val job = launch { bridge.confirmBatch(listOf(request("06:00"), request("06:15"))) }
            advanceUntilIdle()
            assertTrue(bridge.pending.value is PendingInteraction.BatchApproval)

            job.cancel()
            advanceUntilIdle()

            assertNull(bridge.pending.value)
        }

    private fun request(time: String) =
        ApprovalRequest(CallDescription("Поставить будильник", "На $time"), Risk.CONFIRM, buildJsonObject {})
}
