package com.example.phoneagent.tools.files

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * All user-facing and model-facing text for `find_files` in one place, kept out of the tool's
 * logic files. Values are unchanged from where they used to live inline — this is purely a
 * relocation so a future localization pass has one file to change per tool instead of a hunt
 * through the codebase.
 */
internal object FindFilesCopy {
    const val SPEC_DESCRIPTION: String =
        "Search the folder the user pre-selected in the app settings for files whose name " +
            "contains every query word, ignoring case, ё/e, word order, and separators. " +
            "If nothing is found, try up to five synonyms, transliterations, or English " +
            "variants in alternatives. Optional types filter by pdf, doc, image, video, " +
            "audio, or archive. Returns name and relative path only; file contents are never opened."
    const val QUERY_SCHEMA_DESCRIPTION: String =
        "All words to find in a file or folder name; spaces, _, -, and . are equivalent separators."
    const val ALTERNATIVES_SCHEMA_DESCRIPTION: String =
        "Up to five synonyms, transliterations, or English variants; any alternative may match."
    const val TYPES_SCHEMA_DESCRIPTION: String = "Optional file type filters; any listed type may match."

    const val CARD_TITLE: String = "Поиск файлов"
    const val CARD_SUMMARY_NO_QUERY: String = "Найти файлы в выбранной пользователем папке"
    val CARD_DETAILS: List<String> = listOf("Показываются только имена файлов, содержимое не читается")

    const val QUERY_BLANK: String = "query must not be empty or blank"
    const val REASON_NOT_SELECTED: String = "Search folder is not selected: the user needs to pick one in the app settings"

    fun noMatches(attempts: List<String>): String = "No files matched these search attempts: ${JsonArray(attempts.map(::JsonPrimitive))}"

    const val LIMIT_DEPTH: String = "Search stopped early: depth limit of 6 reached."
    const val LIMIT_NODES: String = "Search stopped early: scan limit of 5000 nodes reached."
    const val LIMIT_TIME: String = "Search stopped early: 5-second time limit reached."
    const val LIMIT_RESULTS: String = "Result list capped at 20 matches; more may exist."

    fun cardSummaryWithQuery(query: String): String = "Найти файлы по запросу «$query» в выбранной папке"

    fun validationError(error: SearchFilesValidationError): String =
        invalidArguments(
            when (error) {
                SearchFilesValidationError.EMPTY_QUERY -> {
                    QUERY_BLANK
                }

                SearchFilesValidationError.QUERY_TOO_LONG -> {
                    queryTooLong(FindFilesArgs.MAX_QUERY_LENGTH)
                }

                SearchFilesValidationError.TOO_MANY_ALTERNATIVES -> {
                    "alternatives may contain at most 5 entries"
                }

                SearchFilesValidationError.INVALID_ALTERNATIVE -> {
                    "alternatives must contain non-empty search phrases"
                }

                SearchFilesValidationError.ALTERNATIVE_TOO_LONG -> {
                    "each alternative must be at most ${FindFilesArgs.MAX_QUERY_LENGTH} characters"
                }

                SearchFilesValidationError.UNKNOWN_TYPE -> {
                    "types must use only: pdf, doc, image, video, audio, archive"
                }
            },
        )

    fun queryTooLong(maxLength: Int): String = "query must be at most $maxLength characters"

    fun invalidArguments(reason: String?): String = "Invalid find_files arguments: $reason"

    /** Model-facing reason: fixed text on purpose - the exception may embed paths/URIs. */
    const val FILE_SYSTEM_QUERY_FAILED: String = "File system query failed: the folder could not be read."

    fun fileSystemQueryFailed(): String = FILE_SYSTEM_QUERY_FAILED

    fun matchesFoundHeader(count: Int): String = "Found $count matching item(s) in the selected folder:"

    fun matchLine(
        name: String,
        relativePath: String,
    ): String = "- $name ($relativePath)"
}
