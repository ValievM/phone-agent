package com.example.phoneagent.domain.tools

import kotlinx.serialization.json.JsonObject

/** A tool's fields are rendered generically; the tool remains responsible for checking the draft. */
public data class EditableField(
    public val key: String,
    public val label: String,
    public val kind: EditableFieldKind,
)

public enum class EditableFieldKind { INTEGER, TEXT }

public data class ArgumentReview(
    public val description: CallDescription,
    public val validationError: String?,
)

public interface ToolArgumentEditor {
    public val fields: List<EditableField>

    /** Returns the current description and an error when [args] must not be executed. */
    public fun review(args: JsonObject): ArgumentReview
}
