package com.example.phoneagent.tools.files

import java.time.LocalDate
import java.util.Locale

internal const val FILE_SEARCH_MAX_QUERY_LENGTH = 200
internal const val FILE_SEARCH_MAX_ALTERNATIVES = 5

private data class SearchAttempt(
    val phrase: String,
    val words: List<String>,
)

/** Shared matching rules for SAF and MediaStore file searches. */
internal data class FileSearchCriteria(
    val query: String,
    val alternatives: List<String> = emptyList(),
    val types: List<String> = emptyList(),
) {
    private val extensionQueryType: SearchFileType? =
        query
            .trim()
            .takeIf { it.startsWith(".") && it.length > 1 }
            ?.drop(1)
            ?.let(SearchFileType::fromExtension)

    private val searchQuery: String = if (extensionQueryType == null) query else ""

    private val attempts: List<SearchAttempt> =
        (listOf(searchQuery) + alternatives)
            .map { phrase ->
                SearchAttempt(phrase, normalize(phrase).split(' ').filter(String::isNotEmpty))
            }.filter { it.words.isNotEmpty() }
            .distinctBy { it.words }

    private val selectedTypes: Set<SearchFileType> = types.mapNotNull(SearchFileType::from).toSet()

    private val hasTypeFilter: Boolean = types.isNotEmpty() || extensionQueryType != null

    val attemptedQueries: List<String> =
        (
            (if (extensionQueryType == null) attempts.map(SearchAttempt::phrase) else listOf(query) + attempts.map(SearchAttempt::phrase)) +
                selectedTypes.map { "type:${it.argument}" }
        ).distinct()

    fun validationError(): SearchFilesValidationError? {
        val normalizedQuery = normalize(searchQuery)
        return when {
            normalizedQuery.isEmpty() && !hasTypeFilter -> {
                SearchFilesValidationError.EMPTY_QUERY
            }

            searchQuery.length > FILE_SEARCH_MAX_QUERY_LENGTH || normalizedQuery.length > FILE_SEARCH_MAX_QUERY_LENGTH -> {
                SearchFilesValidationError.QUERY_TOO_LONG
            }

            alternatives.size > FILE_SEARCH_MAX_ALTERNATIVES -> {
                SearchFilesValidationError.TOO_MANY_ALTERNATIVES
            }

            else -> {
                alternatives.firstNotNullOfOrNull(::alternativeValidationError)
                    ?: if (types.any { SearchFileType.from(it) == null }) SearchFilesValidationError.UNKNOWN_TYPE else null
            }
        }
    }

    private fun alternativeValidationError(alternative: String): SearchFilesValidationError? {
        val normalized = normalize(alternative)
        return when {
            alternative.isBlank() || normalized.isEmpty() -> {
                SearchFilesValidationError.INVALID_ALTERNATIVE
            }

            alternative.length > FILE_SEARCH_MAX_QUERY_LENGTH || normalized.length > FILE_SEARCH_MAX_QUERY_LENGTH -> {
                SearchFilesValidationError.ALTERNATIVE_TOO_LONG
            }

            else -> {
                null
            }
        }
    }

    fun matches(
        name: String,
        mimeType: String? = null,
        isDirectory: Boolean = false,
        parentFolderName: String? = null,
        dateModifiedEpochSeconds: Long? = null,
    ): Boolean {
        val matchingType =
            (!hasTypeFilter || !isDirectory) &&
                (selectedTypes.isEmpty() || selectedTypes.any { it.matches(name, mimeType) }) &&
                extensionQueryType?.matches(name, mimeType) != false
        if (!matchingType) return false
        val normalizedMetadata =
            normalize(
                buildString(name.length + parentFolderName.orEmpty().length) {
                    append(name)
                    parentFolderName?.takeIf(String::isNotBlank)?.let {
                        append(' ')
                        append(it)
                    }
                    dateModifiedEpochSeconds?.let {
                        append(' ')
                        append(LocalDate.ofEpochDay(Math.floorDiv(it, SECONDS_PER_DAY)))
                    }
                },
            )
        return attempts.isEmpty() || attempts.any { attempt -> attempt.words.all { it in normalizedMetadata } }
    }

    private companion object {
        val SEPARATORS = setOf('_', '-', '.')

        fun normalize(value: String): String =
            buildString(value.length) {
                for (character in value.lowercase(Locale.ROOT).replace('ё', 'е')) {
                    if (character.isWhitespace() || character.isISOControl() || character in SEPARATORS) {
                        if (isNotEmpty() && last() != ' ') append(' ')
                    } else {
                        append(character)
                    }
                }
                if (isNotEmpty() && last() == ' ') deleteAt(lastIndex)
            }

        const val SECONDS_PER_DAY = 86_400L
    }
}

internal enum class SearchFileType(
    val argument: String,
    private val extensions: Set<String>,
    private val mimeTypes: Set<String> = emptySet(),
    private val mimePrefixes: Set<String> = emptySet(),
) {
    PDF("pdf", setOf("pdf"), setOf("application/pdf")),
    DOC(
        "doc",
        setOf("doc", "docx", "odt", "rtf", "txt", "md", "csv", "xls", "xlsx", "ods", "ppt", "pptx", "odp"),
        setOf(
            "application/msword",
            "application/rtf",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ),
        setOf("text/"),
    ),
    IMAGE("image", setOf("bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "svg", "tif", "tiff", "webp"), mimePrefixes = setOf("image/")),
    VIDEO("video", setOf("3gp", "avi", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "webm"), mimePrefixes = setOf("video/")),
    AUDIO("audio", setOf("aac", "flac", "m4a", "mid", "midi", "mp3", "oga", "ogg", "opus", "wav", "wma"), mimePrefixes = setOf("audio/")),
    ARCHIVE(
        "archive",
        setOf("7z", "apk", "bz", "bz2", "cab", "gz", "iso", "rar", "tar", "tgz", "xz", "zip", "zst"),
        setOf(
            "application/epub+zip",
            "application/gzip",
            "application/java-archive",
            "application/vnd.android.package-archive",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/x-bzip2",
            "application/x-cpio",
            "application/x-iso9660-image",
            "application/x-rar-compressed",
            "application/x-tar",
            "application/x-xz",
            "application/x-zip-compressed",
            "application/zip",
        ),
    ),
    ;

    fun matches(
        name: String,
        mimeType: String?,
    ): Boolean {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        return extension in extensions || mime?.let(mimeTypes::contains) == true || mimePrefixes.any { mime?.startsWith(it) == true }
    }

    companion object {
        val arguments: List<String> = entries.map(SearchFileType::argument)

        fun from(argument: String): SearchFileType? = entries.firstOrNull { it.argument == argument }

        fun fromExtension(extension: String): SearchFileType? = entries.firstOrNull { extension.lowercase(Locale.ROOT) in it.extensions }
    }
}
