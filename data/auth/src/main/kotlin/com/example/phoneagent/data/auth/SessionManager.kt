package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class SessionManager(
    private val providers: Map<AuthMethod, AuthProvider>,
    private val tokenStore: TokenStore,
    private val tokenRefresher: TokenRefresher,
) : CredentialsProvider {
    private val mutableState: MutableStateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    private val mutableReauthenticationFailure: MutableStateFlow<AuthFailure?> = MutableStateFlow(null)

    public val state: StateFlow<AuthState> = mutableState.asStateFlow()
    public val reauthenticationFailure: StateFlow<AuthFailure?> = mutableReauthenticationFailure.asStateFlow()

    public suspend fun restore() {
        when (val result = tokenRefresher.current()) {
            is TokenRefreshOutcome.Ready -> {
                mutableState.value = AuthState.SignedIn(result.tokens.method, accountId(result.tokens))
            }

            TokenRefreshOutcome.SignedOut -> {
                mutableState.value = AuthState.SignedOut
            }

            TokenRefreshOutcome.NeedsSignIn -> {
                mutableReauthenticationFailure.value = AuthFailure.InvalidGrant
                mutableState.value = AuthState.SignedOut
            }

            is TokenRefreshOutcome.Failed -> {
                mutableState.value = AuthState.Error(null, result.failure)
            }
        }
    }

    public suspend fun signIn(method: AuthMethod): AuthResult {
        mutableReauthenticationFailure.value = null
        val provider = providers[method]
        if (provider == null) {
            val result = AuthResult.Failure(AuthFailure.ProviderUnavailable)
            mutableState.value = AuthState.Error(method, result.failure)
            return result
        }

        mutableState.value = AuthState.SigningIn(method)
        return when (val result = provider.signIn()) {
            is AuthResult.Success -> {
                tokenStore.write(result.tokens)
                mutableState.value = AuthState.SignedIn(method, accountId(result.tokens))
                result
            }

            is AuthResult.Failure -> {
                mutableState.value = AuthState.Error(method, result.failure)
                result
            }
        }
    }

    public suspend fun signOut() {
        tokenStore.clear()
        mutableReauthenticationFailure.value = null
        mutableState.value = AuthState.SignedOut
    }

    override suspend fun current(): CredentialsResult = credentials(forceRefresh = false)

    override suspend fun refreshAfterUnauthorized(): Credentials? =
        when (val result = credentials(forceRefresh = true)) {
            is CredentialsResult.Ready -> result.credentials
            CredentialsResult.SignedOut -> null
        }

    private suspend fun credentials(forceRefresh: Boolean): CredentialsResult =
        when (val result = tokenRefresher.current(forceRefresh)) {
            is TokenRefreshOutcome.Ready -> {
                CredentialsResult.Ready(Credentials(result.tokens.accessToken, accountId(result.tokens)))
            }

            TokenRefreshOutcome.SignedOut,
            TokenRefreshOutcome.NeedsSignIn,
            -> {
                mutableReauthenticationFailure.value = AuthFailure.InvalidGrant
                mutableState.value = AuthState.SignedOut
                CredentialsResult.SignedOut
            }

            is TokenRefreshOutcome.Failed -> {
                mutableState.value = AuthState.Error(signedInMethod(), result.failure)
                CredentialsResult.SignedOut
            }
        }

    private fun accountId(tokens: AuthTokens): String? =
        (JwtClaimsExtractor.extract(tokens.accessToken, tokens.idToken) as? JwtClaimsResult.Success)
            ?.claims
            ?.chatGptAccountId

    private fun signedInMethod(): AuthMethod? = (mutableState.value as? AuthState.SignedIn)?.method
}
