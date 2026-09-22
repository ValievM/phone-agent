package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

public class FakeModelGateway : ModelGateway {
    public val requests: MutableList<ModelRequest> = mutableListOf()
    public var events: List<ModelEvent> = emptyList()

    override fun stream(request: ModelRequest): Flow<ModelEvent> =
        flow {
            requests += request
            events.forEach { emit(it) }
        }
}

public class FakeConversationRepository : ConversationRepository {
    public var items: List<HistoryItem> = emptyList()

    override suspend fun load(): List<HistoryItem> = items

    override suspend fun replace(items: List<HistoryItem>) {
        this.items = items
    }
}

public class FakeSettingsRepository : SettingsRepository {
    public var settings: AppSettings = AppSettings()

    override suspend fun load(): AppSettings = settings

    override suspend fun save(settings: AppSettings) {
        this.settings = settings
    }
}

public class FakeCredentialsProvider(
    public var currentResult: CredentialsResult,
) : CredentialsProvider {
    public var refreshedCredentials: Credentials? = null

    override suspend fun current(): CredentialsResult = currentResult

    override suspend fun refreshAfterUnauthorized(): Credentials? = refreshedCredentials
}

public class FakeClock(
    public var epochMillis: Long = 0L,
) : Clock {
    override fun nowEpochMillis(): Long = epochMillis
}

public class FakeDispatcherProvider(
    override val main: CoroutineDispatcher,
    override val io: CoroutineDispatcher = main,
    override val default: CoroutineDispatcher = main,
) : DispatcherProvider

public class FakeUserInteraction : UserInteraction {
    public val approvalRequests: MutableList<ApprovalRequest> = mutableListOf()
    public val capabilityRequests: MutableList<Capability> = mutableListOf()
    public var approvalResult: Boolean = true
    public var capabilityResult: CapabilityResult = CapabilityResult.Available

    override suspend fun confirm(request: ApprovalRequest): Boolean {
        approvalRequests += request
        return approvalResult
    }

    override suspend fun ensure(capability: Capability): CapabilityResult {
        capabilityRequests += capability
        return capabilityResult
    }
}

public data class LoggedEvent(
    public val level: LogLevel,
    public val tag: String,
    public val message: String,
    public val fields: Map<String, String>,
    public val throwable: Throwable?,
)

public class FakeAppLogger : AppLogger {
    public val events: MutableList<LoggedEvent> = mutableListOf()

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    ) {
        events += LoggedEvent(level, tag, message, fields, throwable)
    }
}
