package com.example.phoneagent.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyAuthTest {
    @Test
    fun `blank API key is rejected without a network request`() =
        runTest {
            val gateway = FakeValidationGateway(ApiKeyValidationResult.Valid)
            val store = InMemoryTokenStore()
            val auth = ApiKeyAuth("   ", gateway, store)

            assertEquals(AuthResult.Failure(AuthFailure.InvalidApiKey), auth.signIn())
            assertEquals(0, gateway.calls)
            assertEquals(null, store.stored)
        }

    @Test
    fun `valid API key is stored after validation`() =
        runTest {
            val store = InMemoryTokenStore()
            val auth = ApiKeyAuth("api-key", FakeValidationGateway(ApiKeyValidationResult.Valid), store)

            assertEquals(AuthResult.Success(tokens("api-key")), auth.signIn())
            assertEquals(tokens("api-key"), store.stored)
        }

    @Test
    fun `rejected API key is not stored`() =
        runTest {
            val store = InMemoryTokenStore()
            val auth = ApiKeyAuth("api-key", FakeValidationGateway(ApiKeyValidationResult.Rejected), store)

            assertEquals(AuthResult.Failure(AuthFailure.InvalidApiKey), auth.signIn())
            assertEquals(null, store.stored)
        }

    @Test
    fun `network validation failure does not replace or clear an existing session`() =
        runTest {
            val existing = tokens("existing-key")
            val store = InMemoryTokenStore(existing)
            val auth =
                ApiKeyAuth(
                    "api-key",
                    FakeValidationGateway(ApiKeyValidationResult.Failure(AuthFailure.Network("offline"))),
                    store,
                )

            assertEquals(AuthResult.Failure(AuthFailure.Network("offline")), auth.signIn())
            assertEquals(existing, store.stored)
        }

    private fun tokens(key: String): AuthTokens =
        AuthTokens(
            accessToken = key,
            idToken = null,
            refreshToken = null,
            accessTokenExpiresAtEpochSeconds = null,
            earliestRefreshAtEpochSeconds = null,
        )

    private class InMemoryTokenStore(
        var stored: AuthTokens? = null,
    ) : TokenStore {
        override suspend fun read(): AuthTokens? = stored

        override suspend fun write(tokens: AuthTokens) {
            stored = tokens
        }

        override suspend fun clear() {
            stored = null
        }
    }

    private class FakeValidationGateway(
        private val result: ApiKeyValidationResult,
    ) : ApiKeyValidationGateway {
        var calls: Int = 0

        override suspend fun validate(key: String): ApiKeyValidationResult {
            calls += 1
            return result
        }
    }
}
