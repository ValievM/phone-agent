package com.example.phoneagent.data.auth

public class ApiKeyAuth(
    private val apiKey: String,
    private val validationGateway: ApiKeyValidationGateway,
    private val tokenStore: TokenStore,
) : AuthProvider {
    override val method: AuthMethod = AuthMethod.API_KEY

    override suspend fun signIn(): AuthResult {
        if (apiKey.isBlank()) return AuthResult.Failure(AuthFailure.InvalidApiKey)

        return when (val result = validationGateway.validate(apiKey)) {
            ApiKeyValidationResult.Valid -> {
                val tokens =
                    AuthTokens(
                        accessToken = apiKey,
                        idToken = null,
                        refreshToken = null,
                        accessTokenExpiresAtEpochSeconds = null,
                        earliestRefreshAtEpochSeconds = null,
                    )
                tokenStore.write(tokens)
                AuthResult.Success(tokens)
            }

            ApiKeyValidationResult.Rejected -> {
                AuthResult.Failure(AuthFailure.InvalidApiKey)
            }

            is ApiKeyValidationResult.Failure -> {
                AuthResult.Failure(result.failure)
            }
        }
    }
}
