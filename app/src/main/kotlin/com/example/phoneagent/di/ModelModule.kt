package com.example.phoneagent.di

import com.example.phoneagent.data.llm.BackendProfile
import com.example.phoneagent.data.llm.ResponsesGateway
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.LastNMessagesPolicy
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.storage.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// A streaming SSE response can sit quietly between chunks for longer than OkHttp's ~10s default
// read timeout, so the model client gets its own, more patient client rather than reusing the
// short-timeout one AuthNetworkModule provides for plain request/response auth calls.
private const val MODEL_READ_TIMEOUT_SECONDS = 60L

// Not user-configurable yet (see docs/stages/3-model-client.md item 3.7); ~20 turns of headroom.
private const val CONTEXT_WINDOW_MAX_ITEMS = 40

@Module
@InstallIn(SingletonComponent::class)
internal object ModelModule {
    @Provides
    @Singleton
    fun provideModelGateway(
        profile: BackendProfile,
        credentialsProvider: CredentialsProvider,
        settingsRepository: SettingsRepository,
        logger: AppLogger,
    ): ModelGateway {
        val client = OkHttpClient.Builder().readTimeout(MODEL_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
        return ResponsesGateway(profile, credentialsProvider, settingsRepository, logger, client)
    }

    @Provides
    fun provideContextWindowPolicy(): ContextWindowPolicy = LastNMessagesPolicy(CONTEXT_WINDOW_MAX_ITEMS)
}
