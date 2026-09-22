package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * `find_files` tool: case-insensitive name search inside the folder the user pre-selected in
 * the app settings. SAFE risk — it only reports names and relative paths, never file contents,
 * and never leaves the selected subtree (enforced by [SafTreeWalker]). Expected failures come
 * back as [ToolResult.Failed] with a reason, not exceptions.
 */
internal class FindFilesTool
    @Inject
    constructor(
        private val selectedFolder: SelectedFolder,
    ) : Tool {
        override val spec: ToolSpec =
            ToolSpec(
                name = NAME,
                description =
                    "Search the folder the user pre-selected in the app settings for files whose " +
                        "name contains the query. Returns up to 20 matches as name and path relative " +
                        "to that folder. Only names are read; file contents are never opened.",
                inputSchema =
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "query",
                                    buildJsonObject {
                                        put("type", "string")
                                        put("description", "Case-insensitive substring of a file name to look for.")
                                    },
                                )
                            },
                        )
                        put("required", JsonArray(listOf(JsonPrimitive("query"))))
                    },
                risk = Risk.SAFE,
            )

        override fun describe(args: JsonObject): CallDescription {
            val query = args.queryOrNull()
            return CallDescription(
                title = "Поиск файлов",
                summary =
                    query?.let { "Найти файлы по запросу «$it» в выбранной папке" }
                        ?: "Найти файлы в выбранной пользователем папке",
                details = listOf("Показываются только имена файлов, содержимое не читается"),
            )
        }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult {
            val parsed = parseArgs(args)
            return when (parsed) {
                is ParsedArgs.Invalid -> {
                    ToolResult.Failed(parsed.reason)
                }

                is ParsedArgs.Valid -> {
                    when (val root = selectedFolder.root()) {
                        null -> ToolResult.Failed(REASON_NOT_SELECTED)
                        else -> searchSafely(root, parsed.query, context)
                    }
                }
            }
        }

        private fun searchSafely(
            root: DocumentNode,
            query: String,
            context: ToolContext,
        ): ToolResult =
            try {
                val result = SafTreeWalker(context.clock).search(root, query)
                ToolResult.Success(result.toModelText())
            } catch (exception: Exception) {
                ToolResult.Failed("File system query failed: ${exception.message}")
            }

        private companion object {
            const val NAME = "find_files"
            const val REASON_NOT_SELECTED =
                "Search folder is not selected: the user needs to pick one in the app settings"
        }
    }

private sealed interface ParsedArgs {
    data class Valid(
        val query: String,
    ) : ParsedArgs

    data class Invalid(
        val reason: String,
    ) : ParsedArgs
}

private fun parseArgs(args: JsonObject): ParsedArgs =
    try {
        val parsed = json.decodeFromJsonElement(FindFilesArgs.serializer(), args)
        parsed
            .validationError()
            ?.let { ParsedArgs.Invalid("Invalid find_files arguments: $it") }
            ?: ParsedArgs.Valid(parsed.query)
    } catch (expected: SerializationException) {
        ParsedArgs.Invalid("Invalid find_files arguments: ${expected.message}")
    }

private fun WalkResult.toModelText(): String =
    if (matches.isEmpty()) {
        "No files matching the query were found in the selected folder."
    } else {
        buildString {
            appendLine("Found ${matches.size} matching item(s) in the selected folder:")
            for (match in matches) {
                appendLine("- ${match.name} (${match.relativePath})")
            }
            if (WalkLimit.DEPTH in limitsHit) appendLine("Search stopped early: depth limit of 6 reached.")
            if (WalkLimit.NODES in limitsHit) appendLine("Search stopped early: scan limit of 5000 nodes reached.")
            if (WalkLimit.TIME in limitsHit) appendLine("Search stopped early: 5-second time limit reached.")
            if (WalkLimit.RESULTS in limitsHit) appendLine("Result list capped at 20 matches; more may exist.")
        }
    }

private fun JsonObject.queryOrNull(): String? =
    try {
        json.decodeFromJsonElement(FindFilesArgs.serializer(), this).query.takeIf { it.isNotBlank() }
    } catch (expected: SerializationException) {
        null
    }

private val json = Json { ignoreUnknownKeys = true }
