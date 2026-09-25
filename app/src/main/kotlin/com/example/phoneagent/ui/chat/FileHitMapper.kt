package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentEvent
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.ui.designsystem.components.FileHit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Maps only the direct structured output from search_files; assistant text is never inspected. */
internal fun AgentEvent.toFileHits(): List<FileHit> =
    (this as? AgentEvent.ToolCallFinished)?.let { it.outcome.toFileHits(it.call.name) }.orEmpty()

/** The same mapping for a result read back from the saved history. */
internal fun ToolOutcome.toFileHits(toolName: String): List<FileHit> =
    (this as? ToolOutcome.Success)
        ?.takeIf { toolName == SEARCH_FILES_TOOL_NAME }
        ?.text
        ?.let { output -> runCatching { Json.parseToJsonElement(output) as? JsonObject }.getOrNull() }
        ?.let { result -> result[ITEMS_KEY] as? JsonArray }
        ?.mapNotNull { (it as? JsonObject)?.toFileHit() }
        .orEmpty()

private fun JsonObject.toFileHit(): FileHit? {
    val name = this[NAME_KEY].stringOrNull()
    val uri = this[URI_KEY].stringOrNull()
    val sizeElement = this[SIZE_KEY]
    val sizePrimitive = sizeElement as? JsonPrimitive
    val size = sizePrimitive?.longOrNull
    val mimeElement = this[MIME_TYPE_KEY]
    val mime = mimeElement.stringOrNull()
    val sizeIsValid =
        sizeElement == null || sizeElement == JsonNull ||
            (sizePrimitive != null && !sizePrimitive.isString && size != null && size >= 0L)
    val mimeIsValid = mimeElement == null || mimeElement == JsonNull || mime != null
    val identityIsValid = name?.isNotBlank() == true && uri?.isNotBlank() == true
    val metadataIsValid = sizeIsValid && mimeIsValid

    if (!identityIsValid || !metadataIsValid) return null
    return FileHit(name, uri, size, mime).takeIf(FileHit::hasContentUriScheme)
}

private fun JsonElement?.stringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.contentOrNull else null
}

private const val SEARCH_FILES_TOOL_NAME = "search_files"
private const val ITEMS_KEY = "items"
private const val NAME_KEY = "name"
private const val URI_KEY = "uri"
private const val SIZE_KEY = "size"
private const val MIME_TYPE_KEY = "mimeType"
