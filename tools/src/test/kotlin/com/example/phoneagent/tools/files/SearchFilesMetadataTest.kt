package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
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

class SearchFilesMetadataTest {
    @Test
    fun `types can list without a query and return newest files first`() =
        runTest {
            val search =
                RecordingMetadataSearch(
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
                tool(RecordingMetadataSearch(records = countLimitedRecords))
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
                tool(RecordingMetadataSearch(records = byteLimitedRecords))
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
                RecordingMetadataSearch(
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
                RecordingMetadataSearch(
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
    fun `matches immediate parent folder but not date or ancestor paths`() =
        runTest {
            val search =
                RecordingMetadataSearch(
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

            val parent = tool.execute(args("math"), context()) as ToolResult.Success
            val date = tool.execute(args("2024-01-15"), context()) as ToolResult.Success
            val ancestor = tool.execute(args("Telegram"), context()) as ToolResult.Success

            assertEquals(listOf("book.bin"), resultNames(parent.text))
            assertTrue(resultNames(date.text).isEmpty())
            assertTrue(resultNames(ancestor.text).isEmpty())
        }

    @Test
    fun `empty type-only results explain the attempted type without file metadata`() =
        runTest {
            val result =
                tool(RecordingMetadataSearch())
                    .execute(args(types = listOf("pdf")), context())

            assertTrue(result is ToolResult.Success)
            val success = result as ToolResult.Success
            val root = Json.parseToJsonElement(success.text).jsonObject

            assertEquals(emptyList<String>(), resultNames(success.text))
            assertEquals(listOf("type:pdf"), root.getValue("attempted").jsonArray.map { it.jsonPrimitive.content })
        }

    @Test
    fun `alternatives cannot replace a missing query or type filter`() =
        runTest {
            val search = RecordingMetadataSearch()

            val result =
                tool(search)
                    .execute(args(alternatives = listOf("report")), context())

            assertTrue(result is ToolResult.Failed)
            assertFalse(search.called)
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

    private fun context() = ToolContext(FakeClock(), FakeAppLogger())
}

private class RecordingMetadataSearch(
    private val records: List<SearchFileMetadata> = emptyList(),
) : MediaStoreSearch {
    var called: Boolean = false
        private set

    override fun search(
        criteria: FileSearchCriteria,
        shouldContinue: () -> Boolean,
    ): MediaStoreQueryResult {
        called = true
        val matching =
            records.filter { metadata ->
                val parentFolder =
                    metadata.path
                        .substringBeforeLast('/', missingDelimiterValue = "")
                        .substringAfterLast('/')
                criteria.matches(
                    name = metadata.name,
                    mimeType = metadata.mimeType,
                    parentFolderName = parentFolder,
                )
            }
        return MediaStoreQueryResult(matching, truncated = !shouldContinue())
    }
}
