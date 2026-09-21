package com.example.phoneagent.domain.auth

public data class Credentials(
    public val accessToken: String,
    public val accountId: String?,
)

public interface CredentialsProvider {
    public suspend fun current(): Credentials

    public suspend fun refreshAfterUnauthorized(): Credentials?
}
