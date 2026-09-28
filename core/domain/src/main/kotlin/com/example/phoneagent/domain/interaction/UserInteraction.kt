package com.example.phoneagent.domain.interaction

import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolArgumentEditor
import kotlinx.serialization.json.JsonObject

public enum class Capability {
    NOTIFICATIONS,
    FILES,
}

public sealed interface CapabilityResult {
    public data object Available : CapabilityResult

    public data object Denied : CapabilityResult
}

/** [arguments]: the raw call arguments, so the confirmation UI can show them (e.g. monospace) alongside [description]. */
public data class ApprovalRequest(
    public val description: CallDescription,
    public val risk: Risk,
    public val arguments: JsonObject,
    public val editor: ToolArgumentEditor? = null,
)

public sealed interface ConfirmResult {
    public data class Approved(
        public val arguments: JsonObject,
    ) : ConfirmResult

    public data object Rejected : ConfirmResult
}

public interface UserInteraction {
    public suspend fun confirm(request: ApprovalRequest): ConfirmResult

    /** One question for several calls of the same turn: `true` approves all of them, `false` declines all. */
    public suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean

    public suspend fun ensure(capability: Capability): CapabilityResult
}
