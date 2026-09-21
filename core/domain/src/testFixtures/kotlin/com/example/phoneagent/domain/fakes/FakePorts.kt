package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
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
    public var credentials: Credentials,
) : CredentialsProvider {
    public var refreshedCredentials: Credentials? = null

    override suspend fun current(): Credentials = credentials

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

public class FakeAppLogger : AppLogger {
    public val debugMessages: MutableList<String> = mutableListOf()
    public val errors: MutableList<Pair<String, Throwable?>> = mutableListOf()

    override fun debug(message: String) {
        debugMessages += message
    }

    override fun error(
        message: String,
        throwable: Throwable?,
    ) {
        errors += message to throwable
    }
}
