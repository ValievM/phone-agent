package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.platform.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public sealed interface TokenRefreshOutcome {
    public data class Ready(
        public val tokens: AuthTokens,
    ) : TokenRefreshOutcome

    public data object SignedOut : TokenRefreshOutcome

    public data object NeedsSignIn : TokenRefreshOutcome

    public data class Failed(
        public val failure: AuthFailure,
    ) : TokenRefreshOutcome
}

public class TokenRefresher(
    private val tokenStore: TokenStore,
    private val gateway: TokenRefreshGateway,
    private val clock: Clock,
    private val refreshWindowSeconds: Long = 30L,
    private val mutex: Mutex = Mutex(),
) {
    public suspend fun current(forceRefresh: Boolean = false): TokenRefreshOutcome =
        mutex.withLock {
            val tokens = tokenStore.read() ?: return@withLock TokenRefreshOutcome.SignedOut
            if (!shouldRefresh(tokens, forceRefresh)) return@withLock TokenRefreshOutcome.Ready(tokens)
            val refreshToken = tokens.refreshToken ?: return@withLock TokenRefreshOutcome.NeedsSignIn

            when (val result = gateway.refresh(refreshToken)) {
                is RefreshResult.Success -> {
                    // The gateway doesn't know which AuthProvider originally signed in, so it
                    // cannot set the right method; preserve it from the tokens we just read.
                    val refreshed = result.tokens.copy(method = tokens.method)
                    tokenStore.write(refreshed)
                    TokenRefreshOutcome.Ready(refreshed)
                }

                is RefreshResult.Failure -> {
                    if (result.failure == AuthFailure.InvalidGrant) {
                        tokenStore.clear()
                        TokenRefreshOutcome.NeedsSignIn
                    } else {
                        TokenRefreshOutcome.Failed(result.failure)
                    }
                }
            }
        }

    private fun shouldRefresh(
        tokens: AuthTokens,
        forceRefresh: Boolean,
    ): Boolean =
        forceRefresh || tokens.accessTokenExpiresAtEpochSeconds?.let { expiresAt ->
            val now = clock.nowEpochMillis() / MILLISECONDS_PER_SECOND
            when {
                now >= expiresAt -> true
                expiresAt - now > refreshWindowSeconds -> false
                else -> tokens.earliestRefreshAtEpochSeconds?.let { now >= it } ?: true
            }
        } ?: false

    private companion object {
        private const val MILLISECONDS_PER_SECOND: Long = 1_000L
    }
}
