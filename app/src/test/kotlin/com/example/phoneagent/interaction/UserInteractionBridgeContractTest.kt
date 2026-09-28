package com.example.phoneagent.interaction

import com.example.phoneagent.domain.contracts.UserInteractionContract
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.ConfirmResult
import com.example.phoneagent.domain.interaction.UserInteraction
import kotlinx.coroutines.launch

/** The real bridge obeys the same contract as the shared fake (see FakeUserInteractionContractTest). */
class UserInteractionBridgeContractTest : UserInteractionContract() {
    private val harness = BridgeHarness()

    override fun subject(): UserInteraction = harness.bridge

    override fun kotlinx.coroutines.test.TestScope.setUp(interaction: UserInteraction) {
        // The contract offers answers before the calls exist, so the harness watches the bridge's
        // pending flow and resolves each waiting question from its armed answers.
        val watcher = harness
        backgroundScope.launch {
            watcher.bridge.pending.collect { pending ->
                when (pending) {
                    null -> {
                        Unit
                    }

                    is PendingInteraction.Approval -> {
                        if (!pending.deferred.isCompleted) {
                            watcher.armed.removeFirstOrNull()?.let { approved -> pending.deferred.complete(pending.answer(approved)) }
                        }
                    }

                    is PendingInteraction.BatchApproval -> {
                        if (!pending.deferred.isCompleted) watcher.armedBatch.removeFirstOrNull()?.let(pending.deferred::complete)
                    }

                    is PendingInteraction.Permission -> {
                        if (!pending.deferred.isCompleted) watcher.armedCapabilities.removeFirstOrNull()?.let(pending.deferred::complete)
                    }
                }
            }
        }
    }

    override fun offer(approved: Boolean) {
        val current = harness.bridge.pending.value
        if (current is PendingInteraction.Approval && !current.deferred.isCompleted) {
            current.deferred.complete(current.answer(approved))
        } else {
            harness.armed += approved
        }
    }

    override fun offerBatch(approved: Boolean) {
        val current = harness.bridge.pending.value
        if (current is PendingInteraction.BatchApproval && !current.deferred.isCompleted) {
            current.deferred.complete(approved)
        } else {
            harness.armedBatch += approved
        }
    }

    override fun offerCapability(result: CapabilityResult) {
        val current = harness.bridge.pending.value
        if (current is PendingInteraction.Permission && !current.deferred.isCompleted) {
            current.deferred.complete(result)
        } else {
            harness.armedCapabilities += result
        }
    }

    private class BridgeHarness {
        val bridge = UserInteractionBridge()
        val armed = ArrayDeque<Boolean>()
        val armedBatch = ArrayDeque<Boolean>()
        val armedCapabilities = ArrayDeque<CapabilityResult>()
    }

    private fun PendingInteraction.Approval.answer(approved: Boolean): ConfirmResult =
        if (approved) ConfirmResult.Approved(request.arguments) else ConfirmResult.Rejected
}
