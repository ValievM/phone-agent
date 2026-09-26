package com.example.phoneagent.tools.files

import kotlinx.serialization.Serializable

/** Arguments of a find_files call, decoded from the model's JSON by [FindFilesTool]. */
@Serializable
internal data class FindFilesArgs(
    /** Primary search phrase; results are matched as plain, untrusted name data. */
    val query: String,
    val alternatives: List<String> = emptyList(),
    val types: List<String> = emptyList(),
) {
    fun criteria(): FileSearchCriteria = FileSearchCriteria(query, alternatives, types)

    fun validationError(): SearchFilesValidationError? = criteria().validationError()

    internal companion object {
        const val MAX_QUERY_LENGTH = FILE_SEARCH_MAX_QUERY_LENGTH
    }
}
