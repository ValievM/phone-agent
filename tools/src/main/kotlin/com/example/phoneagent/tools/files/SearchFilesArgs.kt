package com.example.phoneagent.tools.files

import kotlinx.serialization.Serializable

@Serializable
internal data class SearchFilesArgs(
    val query: String = "",
    val alternatives: List<String> = emptyList(),
    val types: List<String> = emptyList(),
) {
    fun criteria(): FileSearchCriteria = FileSearchCriteria(query, alternatives, types)

    fun validationError(): SearchFilesValidationError? = criteria().validationError()

    internal companion object {
        const val MAX_QUERY_LENGTH = FILE_SEARCH_MAX_QUERY_LENGTH
        const val MAX_ALTERNATIVES = FILE_SEARCH_MAX_ALTERNATIVES
    }
}

internal enum class SearchFilesValidationError {
    EMPTY_QUERY,
    QUERY_TOO_LONG,
    TOO_MANY_ALTERNATIVES,
    INVALID_ALTERNATIVE,
    ALTERNATIVE_TOO_LONG,
    UNKNOWN_TYPE,
}
