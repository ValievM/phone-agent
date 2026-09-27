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
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolFile
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
            val pipeline =
                ToolPipeline(
                    ToolRegistry(setOf(tool(search))),
                    interaction,
                    FakeClock(),
                    FakeAppLogger(),
                )

            val outcome = pipeline.run("search_files", args("report"))

            assertEquals(ToolOutcome.Failed(SearchFilesCopy.PERMISSION_DENIED), outcome)
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

            val result = tool(search).execute(args("report"), toolContext)

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
            val pipeline =
                ToolPipeline(
                    ToolRegistry(
                        setOf(
                            SearchFilesTool(
                                search,
                                FakeDispatcherProvider(main = UnconfinedTestDispatcher(testScheduler)),
                            ),
                        ),
                    ),
                    interaction,
                    FakeClock(),
                    FakeAppLogger(),
                )

            val result = pipeline.run("search_files", args("report")) as ToolOutcome.Success
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
                tool(search)
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

            val result = tool(search).execute(args("ёлка"), context()) as ToolResult.Success

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
                tool(search)
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
                tool(search).execute(args("report", types = listOf("pdf")), context()) as
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
    fun `types can list without a query and return newest files first`() =
        runTest {
            val search =
                RecordingMediaStoreSearch(
                    records =
                        listOf(
                            SearchFileMetadata("older.pdf", "content://old", "Download/older.pdf", 1, 100, "application/pdf"),
                            SearchFileMetadata("newest.docx", "content://docx", "Download/newest.docx", 1, 300, "application/docx"),
                            SearchFileMetadata("newer.pdf", "content://new", "Download/newer.pdf", 1, 200, "application/pdf"),
                        ),
                )

            val result = tool(search).execute(args(types = listOf("pdf")), context())

            assertTrue(result is ToolResult.Success)
            assertEquals(listOf("newer.pdf", "older.pdf"), resultNames((result as ToolResult.Success).text))
        }

    @Test
    fun `types-only listing preserves result count and JSON caps`() =
        runTest {
            val countLimitedRecords =
                (0..SearchFilesLimits.MAX_RESULTS).map { index ->
                    SearchFileMetadata(
                        "file-$index.pdf",
                        "content://count/$index",
                        "Download/file-$index.pdf",
                        1,
                        index.toLong(),
                        "application/pdf",
                    )
                }
            val countLimited =
                tool(RecordingMediaStoreSearch(records = countLimitedRecords))
                    .execute(args(types = listOf("pdf")), context()) as ToolResult.Success
            val countLimitedRoot = Json.parseToJsonElement(countLimited.text).jsonObject

            assertEquals(SearchFilesLimits.MAX_RESULTS, resultNames(countLimited.text).size)
            assertTrue(
                countLimitedRoot
                    .getValue("truncated")
                    .jsonPrimitive.content
                    .toBoolean(),
            )

            val byteLimitedRecords =
                (0 until 10).map { index ->
                    SearchFileMetadata(
                        "n".repeat(249) + "$index.pdf",
                        "content://bytes/$index",
                        "p".repeat(SearchFilesMetadataCaps.PATH),
                        1,
                        index.toLong(),
                        "application/pdf",
                    )
                }
            val byteLimited =
                tool(RecordingMediaStoreSearch(records = byteLimitedRecords))
                    .execute(args(types = listOf("pdf")), context()) as ToolResult.Success
            val byteLimitedRoot = Json.parseToJsonElement(byteLimited.text).jsonObject

            assertTrue(byteLimited.text.toByteArray(Charsets.UTF_8).size <= SearchFilesLimits.MAX_OUTPUT_BYTES)
            assertTrue(resultNames(byteLimited.text).size < byteLimitedRecords.size)
            assertTrue(
                byteLimitedRoot
                    .getValue("truncated")
                    .jsonPrimitive.content
                    .toBoolean(),
            )
        }

    @Test
    fun `dot extension query returns the same files as a type filter`() =
        runTest {
            val search =
                RecordingMediaStoreSearch(
                    records =
                        listOf(
                            SearchFileMetadata("report.pdf", "content://pdf", "Download/report.pdf", 1, 300, "application/pdf"),
                            SearchFileMetadata(
                                "report-without-extension",
                                "content://mime",
                                "Download/report-without-extension",
                                1,
                                200,
                                "application/pdf",
                            ),
                            SearchFileMetadata("pdf_notes.docx", "content://docx", "Download/pdf_notes.docx", 1, 100, "application/docx"),
                        ),
                )
            val tool = tool(search)

            val byExtension = tool.execute(args(".pdf"), context())
            val byType = tool.execute(args(types = listOf("pdf")), context())

            assertTrue(byExtension is ToolResult.Success)
            assertTrue(byType is ToolResult.Success)
            assertEquals(
                resultNames((byType as ToolResult.Success).text),
                resultNames((byExtension as ToolResult.Success).text),
            )
        }

    @Test
    fun `short stem and type together narrow results`() =
        runTest {
            val search =
                RecordingMediaStoreSearch(
                    records =
                        listOf(
                            SearchFileMetadata("Математика_за_3_часа.pdf", "content://pdf", "Download/math.pdf", 1, 300, "application/pdf"),
                            SearchFileMetadata(
                                "Математика_за_3_часа.docx",
                                "content://docx",
                                "Download/math.docx",
                                1,
                                200,
                                "application/docx",
                            ),
                        ),
                )

            val result =
                tool(search).execute(args("матем", types = listOf("pdf")), context()) as
                    ToolResult.Success

            assertEquals(listOf("Математика_за_3_часа.pdf"), resultNames(result.text))
        }

    @Test
    fun `matches the immediate parent folder and ISO date but not ancestor paths`() =
        runTest {
            val search =
                RecordingMediaStoreSearch(
                    records =
                        listOf(
                            SearchFileMetadata(
                                "book.bin",
                                "content://book",
                                "Telegram/Maths/book.bin",
                                1,
                                1_705_320_000,
                                "application/octet-stream",
                            ),
                        ),
                )
            val tool = tool(search)

            val matching =
                tool.execute(args("math 2024-01-15"), context()) as
                    ToolResult.Success
            val ancestor = tool.execute(args("Telegram"), context()) as ToolResult.Success

            assertEquals(listOf("book.bin"), resultNames(matching.text))
            assertTrue(resultNames(ancestor.text).isEmpty())
        }

    @Test
    fun `empty type-only results explain the attempted type without file metadata`() =
        runTest {
            val result =
                tool(RecordingMediaStoreSearch())
                    .execute(args(types = listOf("pdf")), context())

            assertTrue(result is ToolResult.Success)
            val root = Json.parseToJsonElement((result as ToolResult.Success).text).jsonObject

            assertEquals(emptyList<String>(), resultNames(result.text))
            assertEquals(listOf("type:pdf"), root.getValue("attempted").jsonArray.map { it.jsonPrimitive.content })
        }

    @Test
    fun `alternatives cannot replace a missing query or type filter`() =
        runTest {
            val search = RecordingMediaStoreSearch()

            val result =
                tool(search)
                    .execute(args(alternatives = listOf("report")), context())

            assertTrue(result is ToolResult.Failed)
            assertFalse(search.called)
        }

    @Test
    fun `unknown file type fails without querying`() =
        runTest {
            val search =
                RecordingMediaStoreSearch(
                    records = listOf(SearchFileMetadata("report.pdf", "content://report", "", 1, 1, null)),
                )

            val result =
                tool(search).execute(args("report", types = listOf("executable")), context())

            assertTrue(result is ToolResult.Failed)
            assertTrue((result as ToolResult.Failed).reason.contains("types"))
            assertFalse(search.called)
        }

    @Test
    fun `empty results report attempted terms without exposing file metadata`() =
        runTest {
            val result =
                tool(RecordingMediaStoreSearch())
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
                tool(RecordingMediaStoreSearch(records = listOf(hit)))
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

        val content = tool(RecordingMediaStoreSearch()).restoreContent(savedOutput)

        assertEquals(
            ToolContent.FilesFound(
                listOf(ToolFile("Договор.pdf", "content://media/external/file/1", 1200L, "application/pdf")),
            ),
            content,
        )
    }

    @Test
    fun `malformed legacy output and unsafe file rows restore without openable hits`() {
        val searchTool = tool(RecordingMediaStoreSearch())
        val savedOutput =
            """{"items":[{"name":"private.txt","uri":"file:///private/private.txt","size":1,"mimeType":"text/plain"},""" +
                """{"name":"bad-size.txt","uri":"content://media/external/file/2","size":"2"}]}"""

        assertEquals(ToolContent.FilesFound(emptyList()), searchTool.restoreContent("not JSON"))
        assertEquals(ToolContent.FilesFound(emptyList()), searchTool.restoreContent(savedOutput))
    }

    @Test
    fun `malformed arguments fail without querying`() =
        runTest {
            val search = RecordingMediaStoreSearch()

            val result = tool(search).execute(buildJsonObject { put("query", 9) }, context())

            assertTrue(result is ToolResult.Failed)
            assertFalse(search.called)
        }

    @Test
    fun `stops scanning at the execution budget and marks results truncated`() =
        runTest {
            val clock = FakeClock()
            val toolContext = context(clock)
            val search =
                MediaStoreSearch { _, shouldContinue ->
                    val firstRowAllowed = shouldContinue()
                    clock.epochMillis += toolContext.executionTimeoutMillis
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

            val result = tool(search).execute(args("txt"), toolContext) as ToolResult.Success
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
    fun `search_files respects the thirty second budget rather than stopping after five seconds`() =
        runTest {
            val clock = FakeClock()
            var continuedAtTwentyNineSeconds = false
            var continuedAtThirtySeconds = true
            val record = SearchFileMetadata("report.pdf", "content://report", "Download/report.pdf", 42, 123, "application/pdf")
            val search =
                MediaStoreSearch { _, shouldContinue ->
                    shouldContinue()
                    clock.epochMillis += 29_999
                    continuedAtTwentyNineSeconds = shouldContinue()
                    clock.epochMillis += 1
                    continuedAtThirtySeconds = shouldContinue()
                    MediaStoreQueryResult(
                        records = listOf(record),
                        truncated = !continuedAtThirtySeconds,
                    )
                }
            val interaction = FakeUserInteraction()
            val pipeline =
                ToolPipeline(
                    ToolRegistry(
                        setOf(
                            SearchFilesTool(
                                search,
                                FakeDispatcherProvider(main = UnconfinedTestDispatcher(testScheduler)),
                            ),
                        ),
                    ),
                    interaction,
                    clock,
                    FakeAppLogger(),
                )

            val outcome = pipeline.run("search_files", args("report"))
            assertTrue("expected a search result, got $outcome", outcome is ToolOutcome.Success)
            val result = outcome as ToolOutcome.Success
            val output = Json.parseToJsonElement(result.text).jsonObject

            assertTrue(continuedAtTwentyNineSeconds)
            assertFalse(continuedAtThirtySeconds)
            assertTrue(
                output
                    .getValue("truncated")
                    .jsonPrimitive.content
                    .toBoolean(),
            )
        }

    @Test
    fun `search_files execution can exceed the five second default budget`() =
        runTest {
            val interaction = FakeUserInteraction()
            val delegate =
                SearchFilesTool(
                    RecordingMediaStoreSearch(),
                    FakeDispatcherProvider(main = UnconfinedTestDispatcher(testScheduler)),
                )
            val delayedTool =
                object : Tool by delegate {
                    override suspend fun execute(
                        args: JsonObject,
                        context: ToolContext,
                    ): ToolResult {
                        delay(6_000)
                        return delegate.execute(args, context)
                    }
                }
            val pipeline =
                ToolPipeline(
                    ToolRegistry(setOf(delayedTool)),
                    interaction,
                    FakeClock(),
                    FakeAppLogger(),
                )

            val outcome = pipeline.run("search_files", args("report"))

            assertTrue("expected a search result, got $outcome", outcome is ToolOutcome.Success)
        }

    @Test
    fun `declares a safe search tool and russian card`() {
        val tool = tool(RecordingMediaStoreSearch())

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
        val tool = tool(RecordingMediaStoreSearch())
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
        assertTrue(tool.spec.description.contains("short word stems"))
        assertTrue(tool.spec.description.contains("types alone"))
        assertFalse(
            tool.spec.inputSchema["required"]
                ?.jsonArray
                ?.any { it.jsonPrimitive.content == "query" } == true,
        )
    }

    @Test
    fun `more than five alternatives fails without querying`() =
        runTest {
            val search = RecordingMediaStoreSearch()

            val result =
                tool(search)
                    .execute(args("report", alternatives = (1..6).map { "term$it" }), context())

            assertTrue(result is ToolResult.Failed)
            assertFalse(search.called)
        }

    @Test
    fun `empty result attempted terms stay within the serialized output budget`() =
        runTest {
            val query = "界".repeat(SearchFilesArgs.MAX_QUERY_LENGTH)
            val alternatives =
                (1..5).map { "界".repeat(SearchFilesArgs.MAX_QUERY_LENGTH - 1) + ('a' + it) }
            val result =
                tool(RecordingMediaStoreSearch())
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
                    SearchFilesTool(search, FakeDispatcherProvider(main = io, io = io, default = io))
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

    private fun tool(search: MediaStoreSearch) = SearchFilesTool(search)

    private fun args(
        query: String = "",
        alternatives: List<String> = emptyList(),
        types: List<String> = emptyList(),
    ) = buildJsonObject {
        if (query.isNotEmpty()) put("query", query)
        if (alternatives.isNotEmpty()) put("alternatives", JsonArray(alternatives.map { JsonPrimitive(it) }))
        if (types.isNotEmpty()) put("types", JsonArray(types.map { JsonPrimitive(it) }))
    }

    private fun resultNames(text: String) =
        Json
            .parseToJsonElement(text)
            .jsonObject
            .getValue("items")
            .jsonArray
            .map {
                it.jsonObject
                    .getValue("name")
                    .jsonPrimitive.content
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
            records.filter { metadata ->
                val parentFolder =
                    metadata.path
                        .substringBeforeLast('/', missingDelimiterValue = "")
                        .substringAfterLast('/')
                criteria.matches(
                    name = metadata.name,
                    mimeType = metadata.mimeType,
                    parentFolderName = parentFolder,
                    dateModifiedEpochSeconds = metadata.dateModified,
                )
            },
            truncated = !shouldContinue(),
        )
    }
}
