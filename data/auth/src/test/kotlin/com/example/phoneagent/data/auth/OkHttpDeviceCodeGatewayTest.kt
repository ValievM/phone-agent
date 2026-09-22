package com.example.phoneagent.data.auth

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpDeviceCodeGatewayTest {
    @Test
    fun `requests user code with client identifier`() =
        runTest {
            serverWith(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(USER_CODE_JSON)
                    .build(),
            ) { server ->
                val result = gateway(server).requestCode()
                val request = server.takeRequest()

                assertEquals(
                    DeviceCodeRequestResult.Ready("auth-id", "CODE-1", "${server.url("").toString().removeSuffix("/")}/device", 5L),
                    result,
                )
                assertEquals("/deviceauth/usercode", request.url.encodedPath)
                assertTrue(requireNotNull(request.body).utf8().contains("\"client_id\":\"client\""))
            }
        }

    @Test
    fun `403 poll remains pending`() =
        runTest {
            serverWith(MockResponse.Builder().code(403).build()) { server ->
                assertEquals(DeviceCodePollResult.Pending, gateway(server).poll("auth-id", "CODE-1"))
            }
        }

    @Test
    fun `successful poll returns authorization code and verifier`() =
        runTest {
            serverWith(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(AUTHORIZED_JSON)
                    .build(),
            ) { server ->
                val result = gateway(server).poll("auth-id", "CODE-1")
                val request = server.takeRequest()

                assertEquals(DeviceCodePollResult.Authorized("authorization-code", "verifier"), result)
                assertEquals("/deviceauth/token", request.url.encodedPath)
            }
        }

    @Test
    fun `requestCode connection failure returns the request result type`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            assertEquals(DeviceCodeRequestResult.Failure(AuthFailure.Network(null)), gateway.requestCode())
        }

    @Test
    fun `poll connection failure returns the poll result type`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            assertEquals(DeviceCodePollResult.Failure(AuthFailure.Network(null)), gateway.poll("auth-id", "CODE-1"))
        }

    @Test
    fun `malformed response is a failure value`() =
        runTest {
            serverWith(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("{}")
                    .build(),
            ) { server ->
                assertEquals(DeviceCodeRequestResult.Failure(AuthFailure.MalformedToken), gateway(server).requestCode())
            }
        }

    private fun gateway(server: MockWebServer): OkHttpDeviceCodeGateway =
        OkHttpDeviceCodeGateway(
            AuthConfig(server.url("").toString().removeSuffix("/"), "authorize", "token", "client", "redirect", "scope", emptyMap()),
        )

    private inline fun serverWith(
        response: MockResponse,
        block: (MockWebServer) -> Unit,
    ) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response)
            block(server)
        }
    }

    private companion object {
        private const val USER_CODE_JSON = """{"device_auth_id":"auth-id","user_code":"CODE-1","interval":"5"}"""
        private const val AUTHORIZED_JSON = """{"authorization_code":"authorization-code","code_verifier":"verifier"}"""
    }
}
