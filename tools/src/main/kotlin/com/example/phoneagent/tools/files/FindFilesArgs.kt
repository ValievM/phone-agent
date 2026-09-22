package com.example.phoneagent.tools.files

import kotlinx.serialization.Serializable

/** Arguments of a find_files call, decoded from the model's JSON by [FindFilesTool]. */
@Serializable
internal data class FindFilesArgs(
    /** Substring to look for in file and folder names; matched as plain data, case-insensitively. */
    val query: String,
) {
    /** Null when the query is usable; otherwise a short reason for the model to correct itself. */
    fun validationError(): String? =
        when {
            query.isBlank() -> {
                "query must not be empty or blank"
            }

            query.length > MAX_QUERY_LENGTH -> {
                "query must be at most $MAX_QUERY_LENGTH characters, got ${query.length}"
            }

            else -> {
                null
            }
        }

    internal companion object {
        // Generous for a file-name substring (file names are rarely longer than 255 chars).
        const val MAX_QUERY_LENGTH = 200
    }
}
