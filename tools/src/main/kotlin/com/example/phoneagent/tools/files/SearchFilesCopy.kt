package com.example.phoneagent.tools.files

/** User-facing card copy and model-facing protocol text for `search_files`. */
internal object SearchFilesCopy {
    const val SPEC_DESCRIPTION: String =
        "Search the Android media index for accessible files whose display name contains every " +
            "query word, ignoring case, ё/e, word order, and separators. If nothing is found, " +
            "try up to five synonyms, transliterations, or English variants in alternatives. " +
            "Optional types filter by pdf, doc, image, video, audio, or archive. Return only " +
            "file metadata; never read file contents. Some downloads may require the user to select a folder."
    const val QUERY_SCHEMA_DESCRIPTION: String =
        "All words to find in a file name; spaces, _, -, and . are equivalent separators."
    const val ALTERNATIVES_SCHEMA_DESCRIPTION: String =
        "Up to five synonyms, transliterations, or English variants; any alternative may match."
    const val TYPES_SCHEMA_DESCRIPTION: String = "Optional file type filters; any listed type may match."

    const val CARD_TITLE: String = "Поиск файлов"
    const val CARD_SUMMARY: String = "Найти совпадения в доступном медиаиндексе"
    val CARD_DETAILS: List<String> =
        listOf(
            "Возвращаются только метаданные, содержимое не читается",
            "Часть загрузок других приложений может быть недоступна",
        )
    const val PERMISSION_DENIED: String = "Media access was not granted; ask the user to allow file search access."
    const val INVALID_ARGUMENTS: String =
        "Invalid search_files arguments: query must be a string and alternatives/types must be arrays."

    fun cardSummary(query: String): String = "Найти файлы по запросу «$query» в доступном медиаиндексе"

    fun validationError(error: SearchFilesValidationError): String =
        when (error) {
            SearchFilesValidationError.EMPTY_QUERY -> {
                "query must not be empty or blank"
            }

            SearchFilesValidationError.QUERY_TOO_LONG -> {
                "query must be at most ${SearchFilesArgs.MAX_QUERY_LENGTH} characters"
            }

            SearchFilesValidationError.TOO_MANY_ALTERNATIVES -> {
                "alternatives may contain at most ${SearchFilesArgs.MAX_ALTERNATIVES} entries"
            }

            SearchFilesValidationError.INVALID_ALTERNATIVE -> {
                "alternatives must contain non-empty search phrases"
            }

            SearchFilesValidationError.ALTERNATIVE_TOO_LONG -> {
                "each alternative must be at most ${SearchFilesArgs.MAX_QUERY_LENGTH} characters"
            }

            SearchFilesValidationError.UNKNOWN_TYPE -> {
                "types must use only: pdf, doc, image, video, audio, archive"
            }
        }

    /** Model-facing reason: fixed text on purpose - the provider exception may embed URIs/paths. */
    const val SEARCH_FAILED: String = "MediaStore search failed: the provider reported an error."

    fun searchFailed(): String = SEARCH_FAILED
}
