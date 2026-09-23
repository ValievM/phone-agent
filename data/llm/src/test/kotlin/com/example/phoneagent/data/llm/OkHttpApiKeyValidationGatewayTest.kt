package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.auth.ApiKeyValidationResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpApiKeyValidationGatewayTest {
    @Test
    fun `200 validates key using models endpoint and bearer header`() =
        runTest {
            serverWith(200) { server ->
                val result = gateway(server).validate("test-key")
                val request = server.takeRequest()

                assertEquals(ApiKeyValidationResult.Valid, result)
                assertEquals("GET", request.method)
                assertEquals("/api/models", request.url.encodedPath)
                assertEquals("Bearer test-key", request.headers["Authorization"])
            }
        }

    @Test
    fun `401 rejects key`() =
        runTest {
            serverWith(401) { server ->
                assertEquals(ApiKeyValidationResult.Rejected, gateway(server).validate("key"))
            }
        }

    @Test
    fun `403 rejects key`() =
        runTest {
            serverWith(403) { server ->
                assertEquals(ApiKeyValidationResult.Rejected, gateway(server).validate("key"))
            }
        }

    @Test
    fun `unexpected status is a network failure`() =
        runTest {
            serverWith(500) { server ->
                assertEquals(ApiKeyValidationResult.NetworkFailure, gateway(server).validate("key"))
            }
        }

    @Test
    fun `connection failure is a network failure`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            assertEquals(ApiKeyValidationResult.NetworkFailure, gateway.validate("key"))
        }

    private fun gateway(server: MockWebServer): OkHttpApiKeyValidationGateway =
        OkHttpApiKeyValidationGateway(BackendProfile(server.url("/api").toString().removeSuffix("/")))

    private inline fun serverWith(
        code: Int,
        block: (MockWebServer) -> Unit,
    ) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(code).build())
            block(server)
        }
    }
}
