package com.example.phoneagent.di

import android.content.Context
import com.example.phoneagent.auth.ApiKeyAuthFactory
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.auth.MutableApiKeyAuthProvider
import com.example.phoneagent.auth.SessionAuthCoordinator
import com.example.phoneagent.data.auth.ApiKeyAuth
import com.example.phoneagent.data.auth.AuthConfig
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.data.auth.BrowserLauncher
import com.example.phoneagent.data.auth.CallbackReceiver
import com.example.phoneagent.data.auth.ChatGptBrowserAuth
import com.example.phoneagent.data.auth.ChatGptDeviceCodeAuth
import com.example.phoneagent.data.auth.CustomTabsBrowserLauncher
import com.example.phoneagent.data.auth.DeviceCodeGateway
import com.example.phoneagent.data.auth.EncryptedTokenStore
import com.example.phoneagent.data.auth.LoopbackCallbackReceiver
import com.example.phoneagent.data.auth.OkHttpDeviceCodeGateway
import com.example.phoneagent.data.auth.OkHttpTokenRefreshGateway
import com.example.phoneagent.data.auth.SessionManager
import com.example.phoneagent.data.auth.TokenRefreshGateway
import com.example.phoneagent.data.auth.TokenRefresher
import com.example.phoneagent.data.auth.TokenStore
import com.example.phoneagent.data.llm.BackendProfile
import com.example.phoneagent.data.llm.OkHttpApiKeyValidationGateway
import com.example.phoneagent.domain.auth.ApiKeyValidationGateway
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.platform.Clock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAuthCoordinator(implementation: SessionAuthCoordinator): AuthCoordinator

    @Binds
    abstract fun bindCredentialsProvider(implementation: SessionManager): CredentialsProvider
}

@Module
@InstallIn(SingletonComponent::class)
internal object AuthNetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    fun provideAuthConfig(): AuthConfig = AuthConfig.ChatGptSubscription

    @Provides
    fun provideBackendProfile(): BackendProfile = BackendProfile.ChatGptSubscription

    @Provides
    @Singleton
    fun provideTokenGateway(
        config: AuthConfig,
        client: OkHttpClient,
    ): TokenRefreshGateway = OkHttpTokenRefreshGateway(config, client)

    @Provides
    @Singleton
    fun provideApiKeyValidationGateway(
        profile: BackendProfile,
        client: OkHttpClient,
    ): ApiKeyValidationGateway = OkHttpApiKeyValidationGateway(profile, client)

    @Provides
    @Singleton
    fun provideDeviceCodeGateway(
        config: AuthConfig,
        client: OkHttpClient,
    ): DeviceCodeGateway = OkHttpDeviceCodeGateway(config, client)
}

@Module
@InstallIn(SingletonComponent::class)
internal object AuthRuntimeModule {
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    fun provideClock(): Clock = Clock(System::currentTimeMillis)

    @Provides
    @Singleton
    fun provideTokenStore(
        @ApplicationContext context: Context,
    ): TokenStore = EncryptedTokenStore(context)

    @Provides
    @Singleton
    fun provideCallbackReceiver(): CallbackReceiver = LoopbackCallbackReceiver()

    @Provides
    @Singleton
    fun provideBrowserLauncher(
        @ApplicationContext context: Context,
    ): BrowserLauncher = CustomTabsBrowserLauncher(context)

    @Provides
    @Singleton
    fun provideBrowserAuth(
        config: AuthConfig,
        receiver: CallbackReceiver,
        launcher: BrowserLauncher,
        tokenGateway: TokenRefreshGateway,
    ): ChatGptBrowserAuth = ChatGptBrowserAuth(config, receiver, launcher, tokenGateway)

    @Provides
    @Singleton
    fun provideDeviceCodeAuth(
        config: AuthConfig,
        deviceGateway: DeviceCodeGateway,
        tokenGateway: TokenRefreshGateway,
    ): ChatGptDeviceCodeAuth = ChatGptDeviceCodeAuth(config, deviceGateway, tokenGateway)

    @Provides
    @Singleton
    fun provideApiKeyAuthFactory(validationGateway: ApiKeyValidationGateway): ApiKeyAuthFactory =
        ApiKeyAuthFactory { key -> ApiKeyAuth(key, validationGateway) }

    @Provides
    @Singleton
    fun provideTokenRefresher(
        tokenStore: TokenStore,
        gateway: TokenRefreshGateway,
        clock: Clock,
    ): TokenRefresher = TokenRefresher(tokenStore, gateway, clock)

    @Provides
    @Singleton
    fun provideSessionManager(
        browserAuth: ChatGptBrowserAuth,
        deviceCodeAuth: ChatGptDeviceCodeAuth,
        apiKeyAuth: MutableApiKeyAuthProvider,
        tokenStore: TokenStore,
        tokenRefresher: TokenRefresher,
    ): SessionManager =
        SessionManager(
            providers =
                mapOf(
                    AuthMethod.CHATGPT_BROWSER to browserAuth,
                    AuthMethod.CHATGPT_DEVICE_CODE to deviceCodeAuth,
                    AuthMethod.API_KEY to apiKeyAuth,
                ),
            tokenStore = tokenStore,
            tokenRefresher = tokenRefresher,
        )
}
