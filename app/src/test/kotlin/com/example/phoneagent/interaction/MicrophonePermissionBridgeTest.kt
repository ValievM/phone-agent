package com.example.phoneagent.interaction

import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MicrophonePermissionBridgeTest {
    @Test
    fun `microphone request is queued and completes with host answer`() =
        runTest {
            val bridge = UserInteractionBridge()
            val answer = async { bridge.ensure(Capability.MICROPHONE) }
            advanceUntilIdle()

            val pending = bridge.pending.value as PendingInteraction.Permission
            assertEquals(Capability.MICROPHONE, pending.capability)
            pending.deferred.complete(CapabilityResult.Denied)

            assertEquals(CapabilityResult.Denied, answer.await())
            assertNull(bridge.pending.value)
        }
}
