package com.example.phoneagent.data.llm.sse

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.model.ModelEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class KtorSseClientTest {
    @Test
    fun `successful stream maps events in order`() =
        runTest {
            assertEquals(
                listOf(ModelEvent.TextDelta("hello"), ModelEvent.Completed(null)),
                clientWith {
                    respond(SUCCESS)
                }.stream(URL, emptyMap(), "{}").toList(),
            )
        }

    @Test
    fun `chunks split at line boundaries are parsed as one stream`() =
        runTest {
            val channel = ByteChannel(autoFlush = true)
            val client = clientWith { respond(channel) }
            val streaming = async { client.stream(URL, emptyMap(), "{}").toList() }
            channel.writeStringUtf8("event: response.output_text.delta\ndata: {\"delta\":")
            kotlinx.coroutines.yield()
            channel.writeStringUtf8("\"hi\"}\n\n")
            kotlinx.coroutines.yield()
            channel.writeStringUtf8("event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n")
            channel.close()
            assertEquals(listOf(ModelEvent.TextDelta("hi"), ModelEvent.Completed(null)), streaming.await())
        }

    @Test
    fun `comments and keep alive lines do not interrupt events`() =
        runTest {
            val body = ": keep-alive\n\nevent: response.output_text.delta\ndata: {\"delta\":\"ok\"}\n\n"
            assertEquals(listOf(ModelEvent.TextDelta("ok")), clientWith { respond(body) }.stream(URL, emptyMap(), "{}").toList())
        }

    @Test
    fun `empty events are ignored`() =
        runTest {
            val body = "event: response.output_text.delta\n\n" + "event: response.output_text.delta\ndata: {\"delta\":\"ok\"}\n\n"
            assertEquals(listOf(ModelEvent.TextDelta("ok")), clientWith { respond(body) }.stream(URL, emptyMap(), "{}").toList())
        }

    @Test
    fun `multiline data follows the production mapper behavior`() =
        runTest {
            val body = "event: ignored\ndata: line one\ndata: line two\n\n" + SUCCESS
            assertEquals(
                listOf(ModelEvent.TextDelta("hello"), ModelEvent.Completed(null)),
                clientWith {
                    respond(body)
                }.stream(URL, emptyMap(), "{}").toList(),
            )
        }

    @Test
    fun `http client and server errors become events`() =
        runTest {
            for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError)) {
                val events = clientWith { respond("private response body", status) }.stream(URL, emptyMap(), "{}").toList()
                assertEquals(listOf(ModelEvent.Failed(AgentError.Server(status.value))), events)
            }
        }

    @Test
    fun `transport failure becomes an event instead of escaping the flow`() =
        runTest {
            val events = clientWith { throw IOException("disconnected") }.stream(URL, emptyMap(), "{}").toList()
            assertEquals(listOf(ModelEvent.Failed(AgentError.Offline)), events)
        }

    @Test
    fun `midstream disconnect becomes a failure event after prior events`() =
        runTest {
            val firstEvent = CompletableDeferred<Unit>()
            val body = ByteChannel(autoFlush = true)
            val client = clientWith { respond(body) }
            val streaming =
                async {
                    client
                        .stream(URL, emptyMap(), "{}")
                        .onEach { if (it == ModelEvent.TextDelta("before")) firstEvent.complete(Unit) }
                        .toList()
                }
            body.writeStringUtf8("event: response.output_text.delta\ndata: {\"delta\":\"before\"}\n\n")
            firstEvent.await()
            body.cancel(IOException("disconnected"))
            assertEquals(listOf(ModelEvent.TextDelta("before"), ModelEvent.Failed(AgentError.Offline)), streaming.await())
        }

    @Test
    fun `Ktor and socket timeouts become failure events`() =
        runTest {
            val ktorTimeout = clientWith { request -> throw HttpRequestTimeoutException(request) }.stream(URL, emptyMap(), "{}").toList()
            val socketTimeout = clientWith { throw SocketTimeoutException("timed out") }.stream(URL, emptyMap(), "{}").toList()
            assertEquals(listOf(ModelEvent.Failed(AgentError.Timeout)), ktorTimeout)
            assertEquals(listOf(ModelEvent.Failed(AgentError.Timeout)), socketTimeout)
        }

    @Test
    fun `cancelling collection closes the response channel once`() =
        runTest {
            val channelClosed = AtomicInteger()
            val bodyChannel = ByteChannel(autoFlush = true)
            bodyChannel.writeStringUtf8("event: response.output_text.delta\ndata: {\"delta\":\"first\"}\n\n")
            bodyChannel.close()
            val trackingChannel = TrackingByteReadChannel(bodyChannel, onCancel = { channelClosed.incrementAndGet() })
            val client = clientWith { respond(trackingChannel) }
            assertEquals(ModelEvent.TextDelta("first"), client.stream(URL, emptyMap(), "{}").first())
            assertEquals(1, channelClosed.get())
        }

    @Test
    fun `a second request works after cancelling the first`() =
        runTest {
            val requests = AtomicInteger()
            val channelClosed = AtomicInteger()
            val readStarted = CompletableDeferred<Unit>()
            val firstStarted = CompletableDeferred<Unit>()
            val firstBody = ByteChannel(autoFlush = true)
            val client =
                clientWith {
                    if (requests.incrementAndGet() == 1) {
                        firstStarted.complete(Unit)
                        respond(
                            TrackingByteReadChannel(
                                firstBody,
                                onCancel = { channelClosed.incrementAndGet() },
                                onRead = { readStarted.complete(Unit) },
                            ),
                        )
                    } else {
                        respond("event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n")
                    }
                }
            val first = async { client.stream(URL, emptyMap(), "{}").first() }
            firstStarted.await()
            firstBody.writeStringUtf8("event: response.output_text.delta")
            readStarted.await()
            kotlinx.coroutines.yield()
            first.cancelAndJoin()
            assertEquals(1, channelClosed.get())
            assertTrue(firstBody.isClosedForRead)
            assertEquals(listOf(ModelEvent.Completed(null)), client.stream(URL, emptyMap(), "{}").toList())
            assertEquals(2, requests.get())
        }

    @Test
    fun `retry after stream failure works`() =
        runTest {
            val requests = AtomicInteger()
            val client =
                clientWith {
                    if (requests.incrementAndGet() == 1) throw IOException("disconnected")
                    respond("event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n")
                }
            assertEquals(listOf(ModelEvent.Failed(AgentError.Offline)), client.stream(URL, emptyMap(), "{}").toList())
            assertEquals(listOf(ModelEvent.Completed(null)), client.stream(URL, emptyMap(), "{}").toList())
        }

    private fun clientWith(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorSseClient =
        KtorSseClient(HttpClient(MockEngine(handler)))

    private companion object {
        const val URL = "https://example.test/responses"
        const val SUCCESS =
            "event: response.output_text.delta\ndata: {\"delta\":\"hello\"}\n\n" +
                "event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n"
    }

    private class TrackingByteReadChannel(
        private val delegate: ByteReadChannel,
        private val onCancel: () -> Unit,
        private val onRead: () -> Unit = {},
    ) : ByteReadChannel by delegate {
        private val cancelled = AtomicBoolean()

        override suspend fun awaitContent(min: Int): Boolean {
            onRead()
            return delegate.awaitContent(min)
        }

        override fun cancel(cause: Throwable?) {
            if (cancelled.compareAndSet(false, true)) onCancel()
            delegate.cancel(cause)
        }
    }
}
