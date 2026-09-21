package com.example.phoneagent.domain.interaction

import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk

public enum class Capability {
    NOTIFICATIONS,
}

public sealed interface CapabilityResult {
    public data object Available : CapabilityResult

    public data object Denied : CapabilityResult
}

public data class ApprovalRequest(
    public val description: CallDescription,
    public val risk: Risk,
)

public interface UserInteraction {
    public suspend fun confirm(request: ApprovalRequest): Boolean

    public suspend fun ensure(capability: Capability): CapabilityResult
}
