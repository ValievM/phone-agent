package com.example.phoneagent.data.llm

import com.example.phoneagent.data.llm.request.buildResponsesRequestBody
import com.example.phoneagent.data.llm.sse.SseParser
import com.example.phoneagent.data.llm.sse.toModelEvent
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

private const val TAG = "ResponsesGateway"
private const val AUTHORIZATION_HEADER = "Authorization"
private const val BEARER_PREFIX = "Bearer "
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * Implements [ModelGateway] against the Responses API (docs/backend-notes.md). 401s are retried
 * once by [RefreshAuthenticator] on the OkHttp client itself; everything else (403/429/network)
 * is mapped straight to [ModelEvent.Failed] rather than thrown, matching the port's contract.
 */
public class ResponsesGateway(
    private val profile: BackendProfile,
    private val credentialsProvider: CredentialsProvider,
    private val logger: AppLogger,
    private val client: OkHttpClient = defaultClient(credentialsProvider),
    // Test seam: lets a test capture the Call OkHttp created, to assert it gets cancelled when
    // the collecting coroutine is (see docs/stages/3-model-client.md item 3.5).
    private val callFactory: Call.Factory = client,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) : ModelGateway {
    override fun stream(request: ModelRequest): Flow<ModelEvent> =
        callbackFlow {
            val channel: SendChannel<ModelEvent> = this
            try {
                val credentials = credentialsProvider.current()
                if (credentials !is CredentialsResult.Ready) {
                    channel.emitOrIgnore(ModelEvent.Failed(AgentError.SessionExpired))
                    channel.close()
                    return@callbackFlow
                }

                val body = buildResponsesRequestBody(request, profile, newSessionId())
                val call = callFactory.newCall(httpRequest(body, credentials.credentials))
                call.enqueue(StreamingCallback(channel))

                awaitClose { call.cancel() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn(TAG, "failed to start a model request", throwable = exception)
                channel.emitOrIgnore(ModelEvent.Failed(AgentError.Unexpected(exception.message ?: "unknown")))
                channel.close()
            }
        }

    private inner class StreamingCallback(
        private val channel: SendChannel<ModelEvent>,
    ) : Callback {
        override fun onFailure(
            call: Call,
            e: IOException,
        ) {
            channel.emitOrIgnore(ModelEvent.Failed(e.toAgentError()))
            channel.close()
        }

        override fun onResponse(
            call: Call,
            response: Response,
        ) {
            try {
                response.use(::handle)
            } catch (exception: Exception) {
                logger.warn(TAG, "streaming response handling threw", throwable = exception)
                channel.emitOrIgnore(ModelEvent.Failed(AgentError.Unexpected(exception.message ?: "unknown")))
            } finally {
                channel.close()
            }
        }

        private fun handle(response: Response) {
            if (!response.isSuccessful) {
                channel.emitOrIgnore(ModelEvent.Failed(response.toAgentError()))
                return
            }
            val source = response.body.source()
            val parser = SseParser()
            // Lazy: only reads as many lines as needed to reach the first terminal event.
            generateSequence { source.readUtf8Line() }
                .flatMap { line -> parser.feed("$line\n") }
                .mapNotNull { it.toModelEvent() }
                .onEach { channel.emitOrIgnore(it) }
                .firstOrNull { it is ModelEvent.Completed || it is ModelEvent.Failed }
        }
    }

    private fun httpRequest(
        body: JsonObject,
        credentials: Credentials,
    ): Request =
        Request
            .Builder()
            .url("${profile.baseUrl}/responses")
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${credentials.accessToken}")
            .apply { credentials.accountId?.let { header("ChatGPT-Account-ID", it) } }
            .header("Accept", "text/event-stream")
            .post(Json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

    public companion object {
        public fun defaultClient(credentialsProvider: CredentialsProvider): OkHttpClient =
            OkHttpClient
                .Builder()
                .authenticator(RefreshAuthenticator(credentialsProvider))
                .build()
    }
}

private fun SendChannel<ModelEvent>.emitOrIgnore(event: ModelEvent) {
    try {
        trySendBlocking(event)
    } catch (_: CancellationException) {
        // The flow was already cancelled (e.g. the user hit "stop"); nothing left to deliver to.
    }
}

private fun Response.toAgentError(): AgentError =
    when (code) {
        HTTP_UNAUTHORIZED -> AgentError.SessionExpired
        HTTP_FORBIDDEN -> AgentError.Forbidden
        HTTP_TOO_MANY_REQUESTS -> AgentError.RateLimited(header("Retry-After")?.toLongOrNull())
        else -> AgentError.Server(code)
    }

private fun IOException.toAgentError(): AgentError =
    when (this) {
        is SocketTimeoutException -> AgentError.Timeout
        else -> AgentError.Offline
    }

/** OkHttp calls this on its own dispatcher thread, never the calling coroutine's — see adapters.md. */
internal class RefreshAuthenticator(
    private val credentialsProvider: CredentialsProvider,
) : Authenticator {
    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        if (response.priorResponseCount() >= MAX_ATTEMPTS) return null
        return runBlocking { credentialsProvider.refreshAfterUnauthorized() }?.let { refreshed ->
            response.request
                .newBuilder()
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${refreshed.accessToken}")
                .build()
        }
    }

    private fun Response.priorResponseCount(): Int {
        var count = 1
        var current = priorResponse
        while (current != null) {
            count += 1
            current = current.priorResponse
        }
        return count
    }

    private companion object {
        private const val MAX_ATTEMPTS = 2
    }
}
