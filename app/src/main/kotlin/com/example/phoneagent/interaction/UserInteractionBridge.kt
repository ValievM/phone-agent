package com.example.phoneagent.interaction

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One question the core is waiting on; the composable that resolves it drives [deferred]. */
public sealed interface PendingInteraction {
    public val deferredAny: CompletableDeferred<*>

    public data class Approval(
        public val request: ApprovalRequest,
        public val deferred: CompletableDeferred<Boolean>,
    ) : PendingInteraction {
        override val deferredAny: CompletableDeferred<*> get() = deferred
    }

    public data class Permission(
        public val capability: Capability,
        public val deferred: CompletableDeferred<CapabilityResult>,
    ) : PendingInteraction {
        override val deferredAny: CompletableDeferred<*> get() = deferred
    }
}

/**
 * The bridge between the (Android-free) core asking a question via [UserInteraction] and whatever
 * is on screen answering it — see architecture/adapters.md "UserInteractionBridge — самое хитрое
 * место". The core suspends on [deferred]; a root composable (`InteractionHost`) observes
 * [pending], shows the appropriate UI, and completes it. If the collector of `pending` goes away
 * before resolving (screen destroyed, host disposed), the pending call is declined rather than
 * left hanging forever — see [resolvePendingAsDeclined].
 */
@Singleton
public class UserInteractionBridge
    @Inject
    constructor() : UserInteraction {
        private val mutablePending = MutableStateFlow<PendingInteraction?>(null)
        public val pending: StateFlow<PendingInteraction?> = mutablePending.asStateFlow()

        override suspend fun confirm(request: ApprovalRequest): Boolean {
            val deferred = CompletableDeferred<Boolean>()
            mutablePending.value = PendingInteraction.Approval(request, deferred)
            return try {
                deferred.await()
            } finally {
                clearIfCurrent(deferred)
            }
        }

        override suspend fun ensure(capability: Capability): CapabilityResult {
            val deferred = CompletableDeferred<CapabilityResult>()
            mutablePending.value = PendingInteraction.Permission(capability, deferred)
            return try {
                deferred.await()
            } finally {
                clearIfCurrent(deferred)
            }
        }

        /** Called by the host when it leaves composition with a question still unanswered. */
        public fun resolvePendingAsDeclined() {
            when (val current = mutablePending.value) {
                is PendingInteraction.Approval -> current.deferred.complete(false)
                is PendingInteraction.Permission -> current.deferred.complete(CapabilityResult.Denied)
                null -> Unit
            }
        }

        private fun clearIfCurrent(deferred: CompletableDeferred<*>) {
            if (mutablePending.value?.deferredAny === deferred) {
                mutablePending.value = null
            }
        }
    }
