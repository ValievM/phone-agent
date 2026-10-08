package com.example.phoneagent.data.llm.sse

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.model.ModelEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.net.SocketTimeoutException

private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/** Temporary transport-only comparison against the production SSE parser and event mapper. */
public class KtorSseClient(
    private val client: HttpClient,
) {
    public fun stream(
        url: String,
        headers: Map<String, String>,
        requestBody: String,
    ): Flow<ModelEvent> =
        flow {
            val collector = this
            try {
                client
                    .preparePost(url) {
                        headers.forEach { (name, value) -> header(name, value) }
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }.execute { response -> collector.emitResponse(response) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Throwable) {
                collector.emit(ModelEvent.Failed(exception.toAgentError()))
            }
        }
}

private suspend fun FlowCollector<ModelEvent>.emitResponse(response: HttpResponse) {
    if (response.status.value !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
        emit(ModelEvent.Failed(response.status.value.toAgentError(response.headers["Retry-After"])))
        return
    }

    val parser = SseParser()
    val channel = response.bodyAsChannel()
    var finished = false
    while (!finished && !channel.isClosedForRead) {
        val line = channel.readUTF8Line()
        finished = line == null || emitParsedEvents(parser, line)
    }
}

private suspend fun FlowCollector<ModelEvent>.emitParsedEvents(
    parser: SseParser,
    line: String,
): Boolean {
    for (event in parser.feed("$line\n").mapNotNull { it.toModelEvent() }) {
        emit(event)
        if (event.isTerminal()) return true
    }
    return false
}

private fun ModelEvent.isTerminal(): Boolean = this is ModelEvent.Completed || this is ModelEvent.Failed

private fun Int.toAgentError(retryAfter: String? = null): AgentError =
    when (this) {
        HTTP_UNAUTHORIZED -> AgentError.SessionExpired
        HTTP_FORBIDDEN -> AgentError.Forbidden
        HTTP_TOO_MANY_REQUESTS -> AgentError.RateLimited(retryAfter?.toLongOrNull())
        else -> AgentError.Server(this)
    }

private fun Throwable.toAgentError(): AgentError =
    when (this) {
        is io.ktor.client.plugins.HttpRequestTimeoutException, is SocketTimeoutException -> AgentError.Timeout
        is IOException -> AgentError.Offline
        else -> AgentError.Unexpected(message ?: "unknown")
    }
