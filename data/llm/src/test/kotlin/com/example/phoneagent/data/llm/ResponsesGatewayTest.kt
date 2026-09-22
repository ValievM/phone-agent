package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ResponsesGatewayTest {
    @Test
    fun `a signed-out credentials provider fails fast without any HTTP call`() =
        runTest {
            withServer { server ->
                val gateway = gateway(server, credentials = FakeCredentialsProvider(CredentialsResult.SignedOut))

                val events = gateway.stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.SessionExpired)), events)
                assertEquals(0, server.requestCount)
            }
        }

    @Test
    fun `a successful stream sends the bearer token and account id and emits parsed events in order`() =
        runTest {
            withServer { server ->
                server.enqueue(sseResponse(sampleSse()))
                val credentials = Credentials(accessToken = "token-1", accountId = "acct-1")
                val gateway = gateway(server, credentials = FakeCredentialsProvider(CredentialsResult.Ready(credentials)))

                val events = gateway.stream(request()).toList()

                val sent = server.takeRequest()
                assertEquals("Bearer token-1", sent.headers["Authorization"])
                assertEquals("acct-1", sent.headers["ChatGPT-Account-ID"])
                assertEquals(
                    listOf(ModelEvent.TextDelta("hi"), ModelEvent.Completed(null)),
                    events,
                )
            }
        }

    @Test
    fun `a 401 triggers exactly one refresh and retry, then succeeds`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                server.enqueue(sseResponse(sampleSse()))
                val credentialsProvider =
                    FakeCredentialsProvider(
                        current = CredentialsResult.Ready(Credentials("stale", null)),
                        refreshed = Credentials("fresh", null),
                    )

                val events = gateway(server, credentials = credentialsProvider).stream(request()).toList()

                assertEquals(1, credentialsProvider.refreshCalls)
                assertEquals("Bearer stale", server.takeRequest().headers["Authorization"])
                assertEquals("Bearer fresh", server.takeRequest().headers["Authorization"])
                assertEquals(listOf(ModelEvent.TextDelta("hi"), ModelEvent.Completed(null)), events)
            }
        }

    @Test
    fun `a 401 that persists after the retry surfaces as SessionExpired`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                server.enqueue(MockResponse.Builder().code(401).build())
                val credentialsProvider =
                    FakeCredentialsProvider(
                        current = CredentialsResult.Ready(Credentials("stale", null)),
                        refreshed = Credentials("still-stale", null),
                    )

                val events = gateway(server, credentials = credentialsProvider).stream(request()).toList()

                assertEquals(1, credentialsProvider.refreshCalls)
                assertEquals(listOf(ModelEvent.Failed(AgentError.SessionExpired)), events)
            }
        }

    @Test
    fun `403 maps to Forbidden`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(403).build())

                val events = gateway(server).stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.Forbidden)), events)
            }
        }

    @Test
    fun `429 maps to RateLimited with the Retry-After header`() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(429)
                        .addHeader("Retry-After", "30")
                        .build(),
                )

                val events = gateway(server).stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.RateLimited(30))), events)
            }
        }

    @Test
    fun `a connection failure maps to Offline`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            val events = gateway.stream(request()).toList()

            assertEquals(listOf(ModelEvent.Failed(AgentError.Offline)), events)
        }

    @Test
    fun `cancelling the collecting coroutine cancels the underlying call`() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(sampleSse())
                        .bodyDelay(5, TimeUnit.SECONDS)
                        .build(),
                )
                val capturingFactory = CapturingCallFactory(cancellableClient())
                val gateway =
                    ResponsesGateway(
                        profile = profile(server),
                        credentialsProvider = FakeCredentialsProvider(),
                        logger = FakeAppLogger(),
                        client = capturingFactory.delegate,
                        callFactory = capturingFactory,
                    )

                // withTimeout on the test dispatcher measures virtual time, which never advances
                // while we're waiting on a real background thread; move to a real dispatcher.
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        val collecting = async { gateway.stream(request()).toList() }
                        capturingFactory.callCreated.await()
                        collecting.cancel()
                        collecting.join()
                    }
                }

                assertTrue(capturingFactory.call.isCanceled())
            }
        }

    private fun request(): ModelRequest = ModelRequest(history = listOf(HistoryItem.UserMessage("1", "hi")))

    private fun profile(server: MockWebServer): BackendProfile =
        BackendProfile(baseUrl = server.url("/backend-api/codex").toString().removeSuffix("/"), model = "test-model")

    // Long enough that only the explicit cancel() below can end the call, not a read timeout.
    private fun cancellableClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    private fun gateway(
        server: MockWebServer,
        credentials: CredentialsProvider = FakeCredentialsProvider(),
    ): ResponsesGateway =
        ResponsesGateway(
            profile = profile(server),
            credentialsProvider = credentials,
            logger = FakeAppLogger(),
            client =
                OkHttpClient
                    .Builder()
                    .readTimeout(1, TimeUnit.SECONDS)
                    .authenticator(RefreshAuthenticator(credentials))
                    .build(),
            newSessionId = { "session-1" },
        )

    private fun sseResponse(text: String): MockResponse =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/event-stream")
            .body(text)
            .build()

    private fun sampleSse(): String =
        "event: response.output_text.delta\ndata: {\"delta\":\"hi\"}\n\n" +
            "event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n"

    private inline fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private class FakeCredentialsProvider(
        private val current: CredentialsResult = CredentialsResult.Ready(Credentials("token", null)),
        private val refreshed: Credentials? = null,
    ) : CredentialsProvider {
        var refreshCalls: Int = 0

        override suspend fun current(): CredentialsResult = current

        override suspend fun refreshAfterUnauthorized(): Credentials? {
            refreshCalls += 1
            return refreshed
        }
    }

    private class CapturingCallFactory(
        val delegate: OkHttpClient,
    ) : Call.Factory {
        lateinit var call: Call
        val callCreated = CompletableDeferred<Unit>()

        override fun newCall(request: Request): Call {
            call = delegate.newCall(request)
            callCreated.complete(Unit)
            return call
        }
    }
}
