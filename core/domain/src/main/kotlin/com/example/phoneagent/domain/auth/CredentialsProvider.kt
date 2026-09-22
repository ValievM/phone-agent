package com.example.phoneagent.domain.auth

public data class Credentials(
    public val accessToken: String,
    public val accountId: String?,
)

public sealed interface CredentialsResult {
    public data class Ready(
        public val credentials: Credentials,
    ) : CredentialsResult

    public data object SignedOut : CredentialsResult
}

public interface CredentialsProvider {
    public suspend fun current(): CredentialsResult

    public suspend fun refreshAfterUnauthorized(): Credentials?
}
