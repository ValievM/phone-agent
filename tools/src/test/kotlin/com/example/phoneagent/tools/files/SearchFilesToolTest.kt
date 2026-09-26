package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.platform.RedactingAppLogger
import com.example.phoneagent.domain.platform.SanitizedThrowable
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolFile
import com.example.phoneagent.domain.tools.ToolResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class SearchFilesToolTest {
    @Test
    fun `requires files capability and skips querying when it is denied`() =
        runTest {
            val interaction = FakeUserInteraction().apply { capabilityResult = CapabilityResult.Denied }
            val search = RecordingMediaStoreSearch()

            val result = tool(interaction, search).execute(args("report"), context())

            assertTrue(result is ToolResult.Failed)
            assertEquals(listOf(Capability.FILES), interaction.capabilityRequests)
            assertFalse(search.called)
        }

    @Test
    fun `provider failure logs safely and never echoes exception text to the model`() =
        runTest {
            val secretUri = "content://secret/path"
            val search =
                object : MediaStoreSearch {
                    override fun search(
                        criteria: FileSearchCriteria,
                        shouldContinue: () -> Boolean,
                    ): MediaStoreQueryResult = error("failed to read $secretUri")
                }
            val records = FakeAppLogger()
            val toolContext = ToolContext(FakeClock(), RedactingAppLogger(records))

            val result = tool(FakeUserInteraction(), search).execute(args("report"), toolContext)

            assertTrue(result is ToolResult.Failed)
            assertFalse((result as ToolResult.Failed).reason.contains(secretUri))
            assertEquals(1, records.events.size)
            val event = records.events.single()
            assertEquals(LogLevel.WARN, event.level)
            assertEquals("SearchFilesTool", event.tag)
            assertEquals("search files failed", event.message)
            assertEquals("IllegalStateException", event.fields["exceptionType"])
            assertTrue(event.throwable is SanitizedThrowable)
            assertFalse(event.toString().contains(secretUri))
        }

    @Test
    fun `queries after capability is available and returns the structured result`() =
        runTest {
            val interaction = FakeUserInteraction()
            val hit = SearchFileMetadata("report.pdf", "content://report", "Download/report.pdf", 42, 123, "application/pdf")
            val search = RecordingMediaStoreSearch(records = listOf(hit))

            val result = tool(interaction, search).execute(args("report"), context()) as ToolResult.Success
            val root = Json.parseToJsonElement(result.text).jsonObject

            assertEquals(listOf(Capability.FILES), interaction.capabilityRequests)
            assertTrue(search.called)
            assertEquals("report", search.query)
            assertEquals(
                "report.pdf",
                root
                    .getValue("items")
                    .jsonArray
                    .single()
                    .jsonObject
                    .getValue("name")
                    .jsonPrimitive.content,
            )
            assertEquals("false", root.getValue("truncated").jsonPrimitive.content)
        }

    @Test
    fun `matches all filename words regardless of order`() =
        runTest {
            val hit = SearchFileMetadata("Математика_за_3_часа.pdf", "content://math", "Download/math.pdf", 42, 123, "application/pdf")
            val search = RecordingMediaStoreSearch(records = listOf(hit))

            val result =
                tool(FakeUserInteraction(), search)
                    .execute(args("за 3 часа математика"), context()) as ToolResult.Success
            val items =
                Json
                    .parseToJsonElement(result.text)
                    .jsonObject
                    .getValue("items")
                    .jsonArray

            assertEquals(
                listOf("Математика_за_3_часа.pdf"),
                items.map {
                    it.jsonObject
                        .getValue("name")
                        .jsonPrimitive.content
                },
            )
        }

    @Test
    fun `matches ё and е interchangeably`() =
        runTest {
            val hit = SearchFileMetadata("елка.txt", "content://tree", "Download/tree.txt", 42, 123, "text/plain")
            val search = RecordingMediaStoreSearch(records = listOf(hit))

            val result = tool(FakeUserInteraction(), search).execute(args("ёлка"), context()) as ToolResult.Success

            assertEquals(
                listOf("елка.txt"),
                Json
                    .parseToJsonElement(result.text)
                    .jsonObject
                    .getValue("items")
                    .jsonArray
                    .map {
                        it.jsonObject
                            .getValue("name")
                            .jsonPrimitive.content
                    },
            )
        }

    @Test
    fun `alternatives use OR matching and deduplicate matching media records`() =
        runTest {
            val duplicate =
                SearchFileMetadata("шпаргалка_cheatsheet.pdf", "content://same", "Download/same.pdf", 42, 123, "application/pdf")
            val other = SearchFileMetadata("cheatsheet.docx", "content://other", "Download/other.docx", 24, 120, "application/docx")
            val search = RecordingMediaStoreSearch(records = listOf(duplicate, duplicate, other))

            val result =
                tool(FakeUserInteraction(), search)
                    .execute(args("not-present", alternatives = listOf("шпаргалка", "cheatsheet")), context()) as
                    ToolResult.Success
            val items =
                Json
                    .parseToJsonElement(result.text)
                    .jsonObject
                    .getValue("items")
                    .jsonArray

            assertEquals(
                listOf("шпаргалка_cheatsheet.pdf", "cheatsheet.docx"),
                items.map {
                    it.jsonObject
                        .getValue("name")
                        .jsonPrimitive.content
                },
            )
        }

    @Test
    fun `pdf type filter excludes docx results`() =
        runTest {
            val pdf = SearchFileMetadata("report.pdf", "content://pdf", "Download/report.pdf", 42, 123, "application/pdf")
            val docx =
                SearchFileMetadata(
                    "report.docx",
                    "content://docx",
                    "Download/report.docx",
                    24,
                    120,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                )
            val search = RecordingMediaStoreSearch(records = listOf(pdf, docx))

            val result =
                tool(FakeUserInteraction(), search).execute(args("report", types = listOf("pdf")), context()) as
                    ToolResult.Success
            val items =
                Json
                    .parseToJsonElement(result.text)
                    .jsonObject
                    .getValue("items")
                    .jsonArray

            assertEquals(
                listOf("report.pdf"),
                items.map {
                    it.jsonObject
                        .getValue("name")
                        .jsonPrimitive.content
                },
            )
        }

    @Test
    fun `unknown file type fails before requesting permission`() =
        runTest {
            val interaction = FakeUserInteraction()
            val search =
                RecordingMediaStoreSearch(
                    records = listOf(SearchFileMetadata("report.pdf", "content://report", "", 1, 1, null)),
                )

            val result =
                tool(interaction, search).execute(args("report", types = listOf("executable")), context())

            assertTrue(result is ToolResult.Failed)
            assertTrue((result as ToolResult.Failed).reason.contains("types"))
            assertTrue(interaction.capabilityRequests.isEmpty())
            assertFalse(search.called)
        }

    @Test
    fun `empty results report attempted terms without exposing file metadata`() =
        runTest {
            val result =
                tool(FakeUserInteraction(), RecordingMediaStoreSearch())
                    .execute(args("private-query", alternatives = listOf("private-alt")), context()) as
                    ToolResult.Success
            val root = Json.parseToJsonElement(result.text).jsonObject

            assertTrue(root.containsKey("attempted"))
            assertEquals(
                listOf("private-query", "private-alt"),
                root.getValue("attempted").jsonArray.map { it.jsonPrimitive.content },
            )
            assertFalse(root.containsKey("name"))
            assertFalse(root.containsKey("path"))
            assertFalse(result.text.contains("private-file.pdf"))
        }

    @Test
    fun `successful search returns typed files while keeping the saved output format`() =
        runTest {
            val hit = SearchFileMetadata("report.pdf", "content://report", "Download/report.pdf", 42, 123, "application/pdf")
            val result =
                tool(FakeUserInteraction(), RecordingMediaStoreSearch(records = listOf(hit)))
                    .execute(args("report"), context()) as ToolResult.Success

            val expectedText =
                listOf(
                    """{"items":[{"name":"report.pdf","uri":"content://report","path":"Download/report.pdf","size":42,""",
                    """"dateModified":123,"mimeType":"application/pdf"}],"truncated":false,"partialAccess":false}""",
                ).joinToString("")
            assertEquals(expectedText, result.text)
            assertEquals(
                ToolContent.FilesFound(
                    listOf(ToolFile("report.pdf", "content://report", 42, "application/pdf")),
                ),
                result.content,
            )
        }

    @Test
    fun `old saved search output restores the same typed file metadata`() {
        val savedOutput =
            listOf(
                """{"items":[{"name":"Договор.pdf","uri":"content://media/external/file/1","path":""",
                """"Документы/Договор.pdf","size":1200,"dateModified":42,"mimeType":"application/pdf"}],"truncated":false}""",
            ).joinToString("")

        val content = tool(FakeUserInteraction(), RecordingMediaStoreSearch()).restoreContent(savedOutput)

        assertEquals(
            ToolContent.FilesFound(
                listOf(ToolFile("Договор.pdf", "content://media/external/file/1", 1200L, "application/pdf")),
            ),
            content,
        )
    }

    @Test
    fun `malformed legacy output and unsafe file rows restore without openable hits`() {
        val searchTool = tool(FakeUserInteraction(), RecordingMediaStoreSearch())
        val savedOutput =
            """{"items":[{"name":"private.txt","uri":"file:///private/private.txt","size":1,"mimeType":"text/plain"},""" +
                """{"name":"bad-size.txt","uri":"content://media/external/file/2","size":"2"}]}"""

        assertEquals(ToolContent.FilesFound(emptyList()), searchTool.restoreContent("not JSON"))
        assertEquals(ToolContent.FilesFound(emptyList()), searchTool.restoreContent(savedOutput))
    }

    @Test
    fun `malformed arguments fail without requesting permission or querying`() =
        runTest {
            val interaction = FakeUserInteraction()
            val search = RecordingMediaStoreSearch()

            val result = tool(interaction, search).execute(buildJsonObject { put("query", 9) }, context())

            assertTrue(result is ToolResult.Failed)
            assertEquals(emptyList<Capability>(), interaction.capabilityRequests)
            assertFalse(search.called)
        }

    @Test
    fun `stops scanning at the five second clock budget and marks results truncated`() =
        runTest {
            val clock = FakeClock()
            val interaction = FakeUserInteraction()
            val search =
                MediaStoreSearch { _, shouldContinue ->
                    val firstRowAllowed = shouldContinue()
                    clock.epochMillis += SearchFilesLimits.TIMEOUT_MILLIS
                    val secondRowAllowed = shouldContinue()
                    MediaStoreQueryResult(
                        records =
                            if (firstRowAllowed) {
                                listOf(
                                    SearchFileMetadata("first.txt", "content://first", "first.txt", 1, 1, null),
                                )
                            } else {
                                emptyList()
                            },
                        truncated = !secondRowAllowed,
                    )
                }

            val result = tool(interaction, search).execute(args("txt"), context(clock)) as ToolResult.Success
            val root = Json.parseToJsonElement(result.text).jsonObject

            assertTrue(
                root
                    .getValue("truncated")
                    .jsonPrimitive.content
                    .toBoolean(),
            )
            assertEquals(1, root.getValue("items").jsonArray.size)
        }

    @Test
    fun `declares a safe search tool and russian card`() {
        val tool = tool(FakeUserInteraction(), RecordingMediaStoreSearch())

        assertEquals("search_files", tool.spec.name)
        assertEquals(Risk.SAFE, tool.spec.risk)
        assertTrue(tool.spec.description.isNotBlank())
        val querySchema =
            tool.spec.inputSchema
                .getValue("properties")
                .jsonObject
                .getValue("query")
                .jsonObject
        assertEquals("1", querySchema.getValue("minLength").jsonPrimitive.content)
        assertEquals("200", querySchema.getValue("maxLength").jsonPrimitive.content)
        assertEquals("Поиск файлов", tool.describe(args("план")).title)
        assertTrue(tool.describe(args("план")).summary.contains("план"))
    }

    @Test
    fun `schema advertises alternatives and closed file types`() {
        val tool = tool(FakeUserInteraction(), RecordingMediaStoreSearch())
        val properties =
            tool.spec.inputSchema
                .getValue("properties")
                .jsonObject
        val alternatives = properties.getValue("alternatives").jsonObject
        val types = properties.getValue("types").jsonObject

        assertEquals("array", alternatives.getValue("type").jsonPrimitive.content)
        assertEquals("5", alternatives.getValue("maxItems").jsonPrimitive.content)
        assertEquals(
            listOf("pdf", "doc", "image", "video", "audio", "archive"),
            types
                .getValue("items")
                .jsonObject
                .getValue("enum")
                .jsonArray
                .map { it.jsonPrimitive.content },
        )
        assertTrue(tool.spec.description.contains("synonyms"))
        assertTrue(tool.spec.description.contains("transliteration"))
        assertTrue(tool.spec.description.contains("English"))
    }

    @Test
    fun `more than five alternatives fails without querying`() =
        runTest {
            val interaction = FakeUserInteraction()
            val search = RecordingMediaStoreSearch()

            val result =
                tool(interaction, search)
                    .execute(args("report", alternatives = (1..6).map { "term$it" }), context())

            assertTrue(result is ToolResult.Failed)
            assertFalse(search.called)
            assertTrue(interaction.capabilityRequests.isEmpty())
        }

    @Test
    fun `empty result attempted terms stay within the serialized output budget`() =
        runTest {
            val query = "界".repeat(SearchFilesArgs.MAX_QUERY_LENGTH)
            val alternatives =
                (1..5).map { "界".repeat(SearchFilesArgs.MAX_QUERY_LENGTH - 1) + ('a' + it) }
            val result =
                tool(FakeUserInteraction(), RecordingMediaStoreSearch())
                    .execute(args(query, alternatives), context()) as ToolResult.Success

            assertTrue(result.text.toByteArray(Charsets.UTF_8).size <= SearchFilesLimits.MAX_OUTPUT_BYTES)
            val output = Json.parseToJsonElement(result.text).jsonObject
            assertTrue(output.containsKey("attempted"))
            assertEquals(6, output.getValue("attempted").jsonArray.size)
            assertEquals("true", output.getValue("attemptsTruncated").jsonPrimitive.content)
        }

    @Test
    fun `the media query runs on the dispatcher provided by the port`() =
        runTest {
            val seenThreads = mutableListOf<String>()
            val search =
                object : MediaStoreSearch {
                    override fun search(
                        criteria: FileSearchCriteria,
                        shouldContinue: () -> Boolean,
                    ): MediaStoreQueryResult {
                        seenThreads += Thread.currentThread().name
                        return MediaStoreQueryResult(emptyList(), truncated = false)
                    }
                }
            val io =
                Executors.newSingleThreadExecutor { r -> Thread(r, "search-test-io") }.asCoroutineDispatcher()
            try {
                val result =
                    SearchFilesTool(FakeUserInteraction(), search, FakeDispatcherProvider(main = io, io = io, default = io))
                        .execute(args("report"), context())

                assertTrue(result is ToolResult.Success)
            } finally {
                io.close()
            }
            assertTrue(
                "expected the query on the provided io dispatcher, saw $seenThreads",
                seenThreads.isNotEmpty() && seenThreads.all { it.startsWith("search-test-io") },
            )
        }

    private fun tool(
        interaction: FakeUserInteraction,
        search: MediaStoreSearch,
    ) = SearchFilesTool(interaction, search)

    private fun args(
        query: String,
        alternatives: List<String> = emptyList(),
        types: List<String> = emptyList(),
    ) = buildJsonObject {
        put("query", query)
        if (alternatives.isNotEmpty()) put("alternatives", JsonArray(alternatives.map { JsonPrimitive(it) }))
        if (types.isNotEmpty()) put("types", JsonArray(types.map { JsonPrimitive(it) }))
    }

    private fun context(clock: FakeClock = FakeClock()) = ToolContext(clock, FakeAppLogger())
}

private class RecordingMediaStoreSearch(
    private val records: List<SearchFileMetadata> = emptyList(),
) : MediaStoreSearch {
    var called: Boolean = false
    var query: String? = null

    override fun search(
        criteria: FileSearchCriteria,
        shouldContinue: () -> Boolean,
    ): MediaStoreQueryResult {
        called = true
        this.query = criteria.query
        return MediaStoreQueryResult(
            records.filter { criteria.matches(it.name, it.mimeType) },
            truncated = !shouldContinue(),
        )
    }
}
