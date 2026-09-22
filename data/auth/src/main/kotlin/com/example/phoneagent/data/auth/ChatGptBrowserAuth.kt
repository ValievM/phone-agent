package com.example.phoneagent.data.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder

private const val UTF_8: String = "UTF-8"

public fun interface BrowserLauncher {
    public fun open(url: String)
}

public class CustomTabsBrowserLauncher(
    private val context: Context,
) : BrowserLauncher {
    override fun open(url: String) {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }
}

public sealed interface CallbackReceiverStartResult {
    public data object Started : CallbackReceiverStartResult

    public data class Failure(
        public val failure: AuthFailure,
    ) : CallbackReceiverStartResult
}

public sealed interface CallbackResult {
    public data class Received(
        public val code: String?,
        public val state: String?,
    ) : CallbackResult

    public data object TimedOut : CallbackResult

    public data class Failure(
        public val failure: AuthFailure,
    ) : CallbackResult
}

public interface CallbackReceiver {
    public fun start(): CallbackReceiverStartResult

    public suspend fun awaitCallback(timeoutMillis: Long): CallbackResult

    public fun close()
}

public class LoopbackCallbackReceiver(
    private val port: Int = LOOPBACK_PORT,
) : CallbackReceiver {
    private var serverSocket: ServerSocket? = null

    override fun start(): CallbackReceiverStartResult =
        try {
            serverSocket =
                ServerSocket().also { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port))
                }
            CallbackReceiverStartResult.Started
        } catch (_: BindException) {
            close()
            CallbackReceiverStartResult.Failure(AuthFailure.LoopbackPortUnavailable)
        } catch (_: Exception) {
            close()
            CallbackReceiverStartResult.Failure(AuthFailure.Network(null))
        }

    override suspend fun awaitCallback(timeoutMillis: Long): CallbackResult =
        withContext(Dispatchers.IO) {
            val socket = serverSocket ?: return@withContext CallbackResult.Failure(AuthFailure.Network(null))
            try {
                socket.soTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                socket.accept().use { connection ->
                    val requestLine = connection.getInputStream().bufferedReader().readLine()
                    connection.getOutputStream().bufferedWriter().use(::writeResponse)
                    parseCallback(requestLine)
                }
            } catch (_: SocketTimeoutException) {
                CallbackResult.TimedOut
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                CallbackResult.Failure(AuthFailure.Network(null))
            }
        }

    override fun close() {
        serverSocket?.close()
        serverSocket = null
    }

    private fun parseCallback(requestLine: String?): CallbackResult {
        val requestTarget = requestLine?.split(' ')?.getOrNull(1) ?: return CallbackResult.Failure(AuthFailure.Network(null))
        val query = requestTarget.substringAfter('?', missingDelimiterValue = "")
        val parameters =
            query
                .split('&')
                .filter(String::isNotEmpty)
                .associate { part ->
                    val name = part.substringBefore('=')
                    val value = part.substringAfter('=', missingDelimiterValue = "")
                    URLDecoder.decode(name, UTF_8) to URLDecoder.decode(value, UTF_8)
                }
        return CallbackResult.Received(code = parameters["code"], state = parameters["state"])
    }

    private fun writeResponse(writer: BufferedWriter) {
        writer.write(CALLBACK_RESPONSE)
        writer.flush()
    }

    private companion object {
        private const val LOOPBACK_HOST: String = "127.0.0.1"
        private const val LOOPBACK_PORT: Int = 1455
        private const val CALLBACK_RESPONSE: String =
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                "Connection: close\r\n\r\nAuthorization completed. You can return to the app."
    }
}

public class ChatGptBrowserAuth(
    private val config: AuthConfig,
    private val callbackReceiver: CallbackReceiver,
    private val browserLauncher: BrowserLauncher,
    private val tokenGateway: TokenRefreshGateway,
    private val entropy: AuthEntropy = SecureAuthEntropy,
    private val timeoutMillis: Long = CALLBACK_TIMEOUT_MILLIS,
) : AuthProvider {
    override val method: AuthMethod = AuthMethod.CHATGPT_BROWSER

    override suspend fun signIn(): AuthResult {
        when (val start = callbackReceiver.start()) {
            is CallbackReceiverStartResult.Failure -> return AuthResult.Failure(start.failure)
            CallbackReceiverStartResult.Started -> Unit
        }

        val pkce = Pkce.create(entropy)
        return try {
            browserLauncher.open(authorizationUrl(pkce))
            when (val callback = callbackReceiver.awaitCallback(timeoutMillis)) {
                is CallbackResult.Failure -> {
                    AuthResult.Failure(callback.failure)
                }

                CallbackResult.TimedOut -> {
                    AuthResult.Failure(AuthFailure.TimedOut)
                }

                is CallbackResult.Received -> {
                    when {
                        callback.state != pkce.state -> {
                            AuthResult.Failure(AuthFailure.StateMismatch)
                        }

                        callback.code.isNullOrBlank() -> {
                            AuthResult.Failure(AuthFailure.MissingAuthorizationCode)
                        }

                        else -> {
                            tokenGateway.exchangeAuthorizationCode(
                                AuthorizationCodeExchange(
                                    code = callback.code,
                                    codeVerifier = pkce.verifier,
                                    redirectUri = config.redirectUri,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            AuthResult.Failure(AuthFailure.Network(null))
        } finally {
            callbackReceiver.close()
        }
    }

    private fun authorizationUrl(pkce: PkceParameters): String {
        val parameters =
            config.authorizationParameters +
                mapOf(
                    "client_id" to config.clientId,
                    "redirect_uri" to config.redirectUri,
                    "scope" to config.scope,
                    "code_challenge" to pkce.challenge,
                    "state" to pkce.state,
                )
        return config.authorizationEndpoint +
            parameters.entries.joinToString(prefix = "?", separator = "&") { (name, value) ->
                "${encode(name)}=${encode(value)}"
            }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")

    private companion object {
        private const val CALLBACK_TIMEOUT_MILLIS: Long = 3 * 60 * 1_000L
    }
}
