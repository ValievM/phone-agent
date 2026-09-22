package com.example.phoneagent.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatGptBrowserAuthTest {
    @Test
    fun `rejects a callback with a different state without exchanging its code`() =
        runTest {
            val gateway = FakeTokenGateway()
            val receiver = FakeCallbackReceiver(CallbackResult.Received(code = "code", state = "other-state"))
            val auth = browserAuth(receiver, gateway)

            assertEquals(AuthResult.Failure(AuthFailure.StateMismatch), auth.signIn())
            assertEquals(0, gateway.exchangeCalls)
            assertEquals(1, receiver.closeCalls)
        }

    @Test
    fun `rejects a callback without code`() =
        runTest {
            val gateway = FakeTokenGateway()
            val receiver = FakeCallbackReceiver(CallbackResult.Received(code = null, state = EXPECTED_STATE))
            val auth = browserAuth(receiver, gateway)

            assertEquals(AuthResult.Failure(AuthFailure.MissingAuthorizationCode), auth.signIn())
            assertEquals(0, gateway.exchangeCalls)
            assertEquals(1, receiver.closeCalls)
        }

    @Test
    fun `returns timeout and closes receiver`() =
        runTest {
            val receiver = FakeCallbackReceiver(CallbackResult.TimedOut)
            val auth = browserAuth(receiver, FakeTokenGateway())

            assertEquals(AuthResult.Failure(AuthFailure.TimedOut), auth.signIn())
            assertEquals(1, receiver.closeCalls)
        }

    @Test
    fun `network exchange failure does not clear an existing session`() =
        runTest {
            val existingTokens = tokens("existing-access")
            val store = InMemoryTokenStore(existingTokens)
            val gateway = FakeTokenGateway(AuthResult.Failure(AuthFailure.Network("offline")))
            val auth = browserAuth(FakeCallbackReceiver(CallbackResult.Received("code", EXPECTED_STATE)), gateway)

            assertEquals(AuthResult.Failure(AuthFailure.Network("offline")), auth.signIn())
            assertEquals(existingTokens, store.read())
            assertFalse(gateway.exchanges.any { it.code == existingTokens.accessToken })
        }

    private fun browserAuth(
        receiver: CallbackReceiver,
        gateway: TokenRefreshGateway,
    ): ChatGptBrowserAuth =
        ChatGptBrowserAuth(
            config = AuthConfig.ChatGptSubscription,
            callbackReceiver = receiver,
            browserLauncher = FakeBrowserLauncher(),
            tokenGateway = gateway,
            entropy = FixedEntropy(),
        )

    private class FixedEntropy : AuthEntropy {
        override fun nextBytes(size: Int): ByteArray = ByteArray(size) { 7 }
    }

    private class FakeBrowserLauncher : BrowserLauncher {
        override fun open(url: String) = Unit
    }

    private class FakeCallbackReceiver(
        private val result: CallbackResult,
    ) : CallbackReceiver {
        var closeCalls: Int = 0

        override fun start(): CallbackReceiverStartResult = CallbackReceiverStartResult.Started

        override suspend fun awaitCallback(timeoutMillis: Long): CallbackResult = result

        override fun close() {
            closeCalls += 1
        }
    }

    private class FakeTokenGateway(
        private val exchangeResult: AuthResult = AuthResult.Success(tokens("new-access")),
    ) : TokenRefreshGateway {
        var exchangeCalls: Int = 0
        val exchanges: MutableList<AuthorizationCodeExchange> = mutableListOf()

        override suspend fun exchangeAuthorizationCode(exchange: AuthorizationCodeExchange): AuthResult {
            exchangeCalls += 1
            exchanges += exchange
            return exchangeResult
        }

        override suspend fun refresh(refreshToken: String): RefreshResult = RefreshResult.Failure(AuthFailure.Network("not used"))
    }

    private class InMemoryTokenStore(
        private var tokens: AuthTokens?,
    ) : TokenStore {
        override suspend fun read(): AuthTokens? = tokens

        override suspend fun write(tokens: AuthTokens) {
            this.tokens = tokens
        }

        override suspend fun clear() {
            tokens = null
        }
    }

    private companion object {
        private const val EXPECTED_STATE: String = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc"

        private fun tokens(accessToken: String): AuthTokens = AuthTokens(accessToken, null, "refresh", 300L, null)
    }
}
