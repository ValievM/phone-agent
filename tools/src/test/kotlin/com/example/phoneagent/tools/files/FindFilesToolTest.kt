package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FindFilesToolTest {
    private val clock = FakeClock()
    private val logger = FakeAppLogger()
    private val context = ToolContext(clock, logger)

    private fun args(query: String): JsonObject = buildJsonObject { put("query", query) }

    private fun toolWith(root: FakeDocumentNode?): FindFilesTool = FindFilesTool(FakeSelectedFolder(root))

    @Test
    fun `spec declares safe risk and an object schema requiring query`() {
        val spec = toolWith(root = null).spec

        assertEquals("find_files", spec.name)
        assertEquals(Risk.SAFE, spec.risk)
        assertTrue(spec.description.isNotBlank())
        assertEquals("object", spec.inputSchema["type"]?.jsonPrimitive?.content)
        val required = spec.inputSchema["required"]?.jsonArray ?: error("required missing")
        assertTrue(required.any { it.jsonPrimitive.content == "query" })
    }

    @Test
    fun `blank query fails with a validation reason`() =
        runTest {
            val result = toolWith(dir("root")).execute(args("   "), context)

            assertTrue(result is ToolResult.Failed)
            assertTrue((result as ToolResult.Failed).reason.contains("query"))
        }

    @Test
    fun `missing query field fails with a validation reason`() =
        runTest {
            val result = toolWith(dir("root")).execute(buildJsonObject { }, context)

            assertTrue(result is ToolResult.Failed)
            assertTrue((result as ToolResult.Failed).reason.contains("query"))
        }

    @Test
    fun `query longer than the limit fails`() =
        runTest {
            val longQuery = "a".repeat(FindFilesArgs.MAX_QUERY_LENGTH + 1)

            val result = toolWith(dir("root")).execute(args(longQuery), context)

            assertTrue(result is ToolResult.Failed)
            assertTrue((result as ToolResult.Failed).reason.contains("at most"))
        }

    @Test
    fun `query at exactly the limit succeeds`() =
        runTest {
            val boundaryQuery = "r".repeat(FindFilesArgs.MAX_QUERY_LENGTH)
            val root = dir("root", file("report.txt"))

            val result = toolWith(root).execute(args(boundaryQuery), context)

            assertTrue(result is ToolResult.Success)
        }

    @Test
    fun `fails with a setup reason when no folder is selected`() =
        runTest {
            val result = toolWith(root = null).execute(args("report"), context)

            assertTrue(result is ToolResult.Failed)
            assertTrue((result as ToolResult.Failed).reason.contains("not selected"))
        }

    @Test
    fun `returns names and relative paths on success`() =
        runTest {
            val root = dir("root", dir("sub", file("report.txt")))

            val result = toolWith(root).execute(args("report"), context)

            val text = (result as ToolResult.Success).text
            assertTrue(text.contains("report.txt"))
            assertTrue(text.contains("sub/report.txt"))
        }

    @Test
    fun `reports the result cap when more matches exist`() =
        runTest {
            val matches = List(30) { file("match_$it.txt") }
            val root = dir("root", *matches.toTypedArray())

            val result = toolWith(root).execute(args("match"), context)

            val text = (result as ToolResult.Success).text
            assertTrue(text.contains("20"))
        }

    @Test
    fun `reports no matches plainly`() =
        runTest {
            val result = toolWith(dir("root")).execute(args("report"), context)

            val text = (result as ToolResult.Success).text
            assertTrue(text.contains("No files"))
        }

    @Test
    fun `file system failure becomes Failed instead of an exception`() =
        runTest {
            val brokenRoot =
                FakeDocumentNode(
                    name = "root",
                    isDirectory = true,
                    childrenError = SecurityException("permission revoked"),
                )

            val result = toolWith(brokenRoot).execute(args("match"), context)

            assertTrue(result is ToolResult.Failed)
        }

    @Test
    fun `describe shows the query in a russian summary`() {
        val description = toolWith(dir("root")).describe(args("отчёт"))

        assertEquals("Поиск файлов", description.title)
        assertTrue(description.summary.contains("отчёт"))
        assertTrue(description.details.isNotEmpty())
    }

    @Test
    fun `describe falls back to a generic summary when args are undecodable`() {
        val description = toolWith(dir("root")).describe(buildJsonObject { })

        assertEquals("Поиск файлов", description.title)
        assertTrue(description.summary.isNotBlank())
    }

    @Test
    fun `cancelling during the walk surfaces CancellationException instead of a failure`() =
        runTest {
            val walkerReachedBlockedNode = CountDownLatch(1)
            val releaseWalker = CountDownLatch(1)
            val turnCancelled =
                java.util.concurrent.atomic
                    .AtomicBoolean(false)
            val blockedNode =
                object : FakeDocumentNode("blocked", isDirectory = true) {
                    override fun children(): List<FakeDocumentNode> {
                        walkerReachedBlockedNode.countDown()
                        releaseWalker.await(5, TimeUnit.SECONDS)
                        if (turnCancelled.get()) throw CancellationException("walk interrupted by stop")
                        return emptyList()
                    }
                }
            val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            try {
                val deferred =
                    async(io) {
                        FindFilesTool(FakeSelectedFolder(dir("root", file("report.txt"), blockedNode)), dispatchersOf(io))
                            .execute(args("report"), context)
                    }

                val reached =
                    walkerReachedBlockedNode.await(5, TimeUnit.SECONDS)
                assertTrue("walker did not reach the blocked node within the timeout", reached)
                deferred.cancel()
                turnCancelled.set(true)
                releaseWalker.countDown()

                val outcome = runCatching { deferred.await() }
                assertTrue(
                    "expected CancellationException to surface, got $outcome",
                    outcome.exceptionOrNull() is CancellationException,
                )
            } finally {
                io.close()
            }
        }

    @Test
    fun `the walk runs on the dispatcher provided by the port, not on the caller`() =
        runTest {
            val seenThreads = mutableListOf<String>()
            val blockingNode =
                object : FakeDocumentNode("io", isDirectory = true) {
                    override fun children(): List<FakeDocumentNode> {
                        seenThreads += Thread.currentThread().name
                        return emptyList()
                    }
                }
            val io = Executors.newSingleThreadExecutor { r -> Thread(r, "find-files-test-io") }.asCoroutineDispatcher()
            try {
                val tool = FindFilesTool(FakeSelectedFolder(dir("root", file("report.txt"), blockingNode)), dispatchersOf(io))

                val result = tool.execute(args("report"), context)

                assertTrue(result is ToolResult.Success)
                assertTrue(
                    "expected the walk on the provided io dispatcher, saw $seenThreads",
                    seenThreads.isNotEmpty() && seenThreads.all { it == "find-files-test-io" },
                )
            } finally {
                io.close()
            }
        }

    private fun dispatchersOf(io: CoroutineDispatcher): DispatcherProvider = FakeDispatcherProvider(main = io, io = io, default = io)

    private class FakeSelectedFolder(
        private val rootNode: FakeDocumentNode?,
    ) : SelectedFolder {
        override suspend fun root(): DocumentNode? = rootNode
    }
}
