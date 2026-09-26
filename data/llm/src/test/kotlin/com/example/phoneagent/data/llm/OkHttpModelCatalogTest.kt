package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import com.example.phoneagent.domain.model.ModelCatalogResult
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.platform.RedactingAppLogger
import com.example.phoneagent.domain.platform.SanitizedThrowable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class OkHttpModelCatalogTest {
    @Test
    fun `returns unavailable when models endpoint rejects the request`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().code(503).build())
                val records = FakeAppLogger()

                assertEquals(
                    ModelCatalogResult.Unavailable,
                    catalog(server, RedactingAppLogger(records)).load(),
                )

                val event = records.events.single()
                assertEquals(LogLevel.WARN, event.level)
                assertEquals("load model catalog failed", event.message)
                assertEquals("503", event.fields["http"])
            }
        }

    @Test
    fun `malformed model response logs a safe warning`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body("not-json")
                        .build(),
                )
                val records = FakeAppLogger()

                assertEquals(ModelCatalogResult.Unavailable, catalog(server, RedactingAppLogger(records)).load())

                assertEquals(1, records.events.size)
                val event = records.events.single()
                assertEquals(LogLevel.WARN, event.level)
                assertEquals("ModelCatalog", event.tag)
                assertEquals("load model catalog failed", event.message)
                assertEquals("IllegalArgumentException", event.fields["exceptionType"])
                assertTrue(event.throwable is SanitizedThrowable)
                assertFalse(event.toString().contains("not-json"))
            }
        }

    @Test
    fun `loads model ids from data response`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body("""{"data":[{"id":"gpt-6-astra"}]}""")
                        .build(),
                )

                assertEquals(
                    ModelCatalogResult.Available(listOf("gpt-6-astra")),
                    catalog(server).load(),
                )
            }
        }

    @Test
    fun `loads model slugs from models response`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body(
                            """
                            {
                              "catalog_version": "test",
                              "models": [
                                {"slug":"gpt-5.6-luna","unknown_model_field":{"enabled":true}},
                                {"slug":"gpt-5.6-terra","description":"ignored"},
                                {"slug":"gpt-5.6-sol","capabilities":["chat"]}
                              ],
                              "unknown_catalog_field": true
                            }
                            """.trimIndent(),
                        ).build(),
                )

                val records = FakeAppLogger()
                val catalog = catalog(server, RedactingAppLogger(records))

                assertEquals(
                    ModelCatalogResult.Available(listOf("gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol")),
                    catalog.load(),
                )
                val request = server.takeRequest()
                assertEquals("/api/models", request.url.encodedPath)
                assertEquals("test-version", request.url.queryParameter("client_version"))
                assertEquals("Bearer test-token", request.headers["Authorization"])

                val event = records.events.single()
                assertEquals(LogLevel.INFO, event.level)
                assertEquals("ModelCatalog", event.tag)
                assertEquals("model catalog loaded", event.message)
                assertEquals("3", event.fields["count"])
                assertEquals("200", event.fields["http"])
                assertFalse(event.toString().contains("gpt-5.6-luna"))
                assertFalse(event.toString().contains("gpt-5.6-terra"))
                assertFalse(event.toString().contains("gpt-5.6-sol"))
            }
        }

    @Test
    fun `returns an empty available model list when catalog is empty`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body("""{"models":[],"unknown_catalog_field":true}""")
                        .build(),
                )

                assertEquals(ModelCatalogResult.Available(emptyList()), catalog(server).load())
            }
        }

    private fun readyCredentials(): CredentialsProvider =
        object : CredentialsProvider {
            override suspend fun current(): CredentialsResult = CredentialsResult.Ready(Credentials("test-token", accountId = null))

            override suspend fun refreshAfterUnauthorized(): CredentialsResult = CredentialsResult.SignedOut
        }

    @Test
    fun `the network call runs on the dispatcher provided by the port`() =
        runTest {
            val seenThreads = mutableListOf<String>()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        seenThreads += Thread.currentThread().name
                        chain.proceed(chain.request())
                    }.build()
            val io =
                Executors.newSingleThreadExecutor { r -> Thread(r, "catalog-test-io") }.asCoroutineDispatcher()
            try {
                MockWebServer().use { server ->
                    server.start()
                    server.enqueue(
                        MockResponse
                            .Builder()
                            .code(200)
                            .body("""{"models":[{"slug":"gpt-5.6-luna"}]}""")
                            .build(),
                    )

                    val result =
                        OkHttpModelCatalog(
                            profile = BackendProfile(server.url("/api").toString().removeSuffix("/")),
                            credentialsProvider = readyCredentials(),
                            clientVersion = "test-version",
                            client = client,
                            dispatchers = FakeDispatcherProvider(main = io, io = io, default = io),
                            logger = FakeAppLogger(),
                        ).load()

                    assertTrue(result is ModelCatalogResult.Available)
                }
            } finally {
                io.close()
            }
            assertTrue(
                "expected the call on the provided io dispatcher, saw $seenThreads",
                seenThreads.isNotEmpty() && seenThreads.all { it.startsWith("catalog-test-io") },
            )
        }

    private fun catalog(
        server: MockWebServer,
        logger: AppLogger = FakeAppLogger(),
    ): OkHttpModelCatalog =
        OkHttpModelCatalog(
            profile = BackendProfile(server.url("/api").toString().removeSuffix("/")),
            credentialsProvider = readyCredentials(),
            clientVersion = "test-version",
            client = OkHttpClient(),
            logger = logger,
        )
}
