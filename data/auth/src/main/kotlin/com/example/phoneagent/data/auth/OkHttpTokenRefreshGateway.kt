package com.example.phoneagent.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

public class OkHttpTokenRefreshGateway(
    private val config: AuthConfig,
    private val client: OkHttpClient = OkHttpClient(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) : TokenRefreshGateway {
    override suspend fun exchangeAuthorizationCode(exchange: AuthorizationCodeExchange): AuthResult =
        request(
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to config.clientId,
                "code" to exchange.code,
                "redirect_uri" to exchange.redirectUri,
                "code_verifier" to exchange.codeVerifier,
            ),
        ).toAuthResult()

    override suspend fun refresh(refreshToken: String): RefreshResult =
        request(
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to config.clientId,
                "refresh_token" to refreshToken,
            ),
        ).toRefreshResult()

    private suspend fun request(fields: Map<String, String>): GatewayResult =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder().apply { fields.forEach(::add) }.build()
                val request =
                    Request
                        .Builder()
                        .url(config.tokenEndpoint)
                        .post(body)
                        .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    when {
                        response.isSuccessful -> parseTokens(responseBody)
                        responseBody.errorCode() == INVALID_GRANT -> GatewayResult.InvalidGrant
                        else -> GatewayResult.Network
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: IOException) {
                GatewayResult.Network
            } catch (_: Exception) {
                GatewayResult.Malformed
            }
        }

    private fun parseTokens(body: String): GatewayResult =
        try {
            val payload = Json.parseToJsonElement(body).jsonObject
            val accessToken = payload.stringValue(ACCESS_TOKEN) ?: return GatewayResult.Malformed
            val expiresIn = payload.longValue(EXPIRES_IN)
            GatewayResult.Success(
                AuthTokens(
                    accessToken = accessToken,
                    idToken = payload.stringValue(ID_TOKEN),
                    refreshToken = payload.stringValue(REFRESH_TOKEN),
                    accessTokenExpiresAtEpochSeconds = expiresIn?.let { nowEpochSeconds() + it },
                    earliestRefreshAtEpochSeconds = payload.longValue(EARLIEST_REFRESH_AT),
                    // This gateway is shared by both OAuth flows (browser and device code) and by
                    // refresh, so it cannot know the real method. The owning AuthProvider stamps
                    // the correct one on exchange, and TokenRefresher preserves it on refresh.
                    method = AuthMethod.CHATGPT_BROWSER,
                ),
            )
        } catch (_: Exception) {
            GatewayResult.Malformed
        }

    private fun GatewayResult.toAuthResult(): AuthResult =
        when (this) {
            is GatewayResult.Success -> AuthResult.Success(tokens)
            GatewayResult.InvalidGrant -> AuthResult.Failure(AuthFailure.InvalidGrant)
            GatewayResult.Malformed -> AuthResult.Failure(AuthFailure.MalformedToken)
            GatewayResult.Network -> AuthResult.Failure(AuthFailure.Network(null))
        }

    private fun GatewayResult.toRefreshResult(): RefreshResult =
        when (this) {
            is GatewayResult.Success -> RefreshResult.Success(tokens)
            GatewayResult.InvalidGrant -> RefreshResult.Failure(AuthFailure.InvalidGrant)
            GatewayResult.Malformed -> RefreshResult.Failure(AuthFailure.MalformedToken)
            GatewayResult.Network -> RefreshResult.Failure(AuthFailure.Network(null))
        }

    private fun String.errorCode(): String? = runCatching { Json.parseToJsonElement(this).jsonObject.stringValue(ERROR) }.getOrNull()

    private sealed interface GatewayResult {
        data class Success(
            val tokens: AuthTokens,
        ) : GatewayResult

        data object InvalidGrant : GatewayResult

        data object Malformed : GatewayResult

        data object Network : GatewayResult
    }

    private companion object {
        private const val ACCESS_TOKEN: String = "access_token"
        private const val ID_TOKEN: String = "id_token"
        private const val REFRESH_TOKEN: String = "refresh_token"
        private const val EXPIRES_IN: String = "expires_in"
        private const val EARLIEST_REFRESH_AT: String = "earliest_refresh_at"
        private const val ERROR: String = "error"
        private const val INVALID_GRANT: String = "invalid_grant"
        private const val MILLIS_PER_SECOND: Long = 1_000L
    }
}

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull

private fun JsonObject.longValue(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.longOrNull
