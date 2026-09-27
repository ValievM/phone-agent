package com.example.phoneagent.di

import android.content.Context
import com.example.phoneagent.data.llm.BackendProfile
import com.example.phoneagent.data.llm.OkHttpModelCatalog
import com.example.phoneagent.data.llm.ResponsesGateway
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.LastNMessagesPolicy
import com.example.phoneagent.domain.model.ModelCatalog
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.storage.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// A streaming SSE response can sit quietly between chunks longer than OkHttp's default read timeout.
// Keep its dedicated client more patient than the shared platform client.
private const val MODEL_READ_TIMEOUT_SECONDS = 60L

// Not user-configurable yet (see docs/stages/3-model-client.md item 3.7); ~20 turns of headroom.
private const val CONTEXT_WINDOW_MAX_ITEMS = 40

@Module
@InstallIn(SingletonComponent::class)
internal object ModelModule {
    // Keep each Hilt binding explicit instead of introducing an artificial dependency bundle.
    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideModelCatalog(
        profile: BackendProfile,
        credentialsProvider: CredentialsProvider,
        client: OkHttpClient,
        dispatchers: DispatcherProvider,
        logger: AppLogger,
        @ApplicationContext context: Context,
    ): ModelCatalog =
        OkHttpModelCatalog(
            profile = profile,
            credentialsProvider = credentialsProvider,
            client = client,
            clientVersion =
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
                    .orEmpty(),
            dispatchers = dispatchers,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideModelGateway(
        profile: BackendProfile,
        credentialsProvider: CredentialsProvider,
        settingsRepository: SettingsRepository,
        logger: AppLogger,
        dispatchers: DispatcherProvider,
    ): ModelGateway {
        val client = OkHttpClient.Builder().readTimeout(MODEL_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
        return ResponsesGateway(profile, credentialsProvider, settingsRepository, logger, client = client, dispatchers = dispatchers)
    }

    @Provides
    fun provideContextWindowPolicy(): ContextWindowPolicy = LastNMessagesPolicy(CONTEXT_WINDOW_MAX_ITEMS)
}
